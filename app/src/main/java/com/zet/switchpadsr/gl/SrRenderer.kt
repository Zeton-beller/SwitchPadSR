package com.zet.switchpadsr.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.zet.switchpadsr.model.DisplayAspectMode
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.model.SRAlgorithmType
import com.zet.switchpadsr.model.TargetResolutionPreset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RenderStats(
    val fps: Float = 0f,
    val gpuMs: Float = 0f,
    val inW: Int = 0, val inH: Int = 0,
    val outW: Int = 0, val outH: Int = 0
)

/**
 * GLES 3.0 multi-pass super-resolution renderer.
 *
 * Source (ExoPlayer / UVC camera) -> SurfaceTexture (OES) -> RGBA input FBO
 *  -> SR pass(es) at target resolution -> final composition on screen.
 */
class SrRenderer(
    private val context: Context,
    private val onInputSurface: (Surface?) -> Unit,
    private val onStats: (RenderStats) -> Unit,
    private val onError: (String) -> Unit
) : GLSurfaceView.Renderer {

    companion object { private const val TAG = "SwitchPadSR-Render"; private const val MAX_DIM = 4096 }

    @Volatile private var settings = RenderSettings()
    private val settingsVersion = AtomicInteger(0)
    private val srcW = AtomicInteger(1920)
    private val srcH = AtomicInteger(1080)
    private val frameAvailable = AtomicBoolean(false)
    @Volatile var glView: GLSurfaceView? = null

    fun updateSettings(s: RenderSettings) {
        settings = s
        settingsVersion.incrementAndGet()
        glView?.requestRender()
    }

    /** Forget the last frame so the screen goes black until the new source delivers one. */
    fun resetFrame() {
        glView?.queueEvent { hasFrame = false; frameAvailable.set(false) }
        glView?.requestRender()
    }

    fun setSourceSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (srcW.getAndSet(w) != w || srcH.getAndSet(h) != h) glView?.requestRender()
    }

    // ---- GL-thread state ----
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val texMat = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    private val programs = HashMap<String, GlProgram>()
    private var inputFbo: GlFbo? = null
    private var fboA: GlFbo? = null
    private var fboB: GlFbo? = null
    private var screenW = 1
    private var screenH = 1
    private var hasFrame = false
    private var processedVersion = -1
    private var resultTex = 0
    private var resultW = 0
    private var resultH = 0
    private var needsProcess = true

    private var statFrames = 0
    private var statStart = System.nanoTime()
    private var gpuMs = 0f
    private var processCount = 0
    private var failed = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGl()
        failed = false
        try {
            programs["oes"] = GlUtil.buildProgram(context, "fullscreen.vert", "oes_copy.frag")
            for ((k, f) in listOf(
                "bicubic" to "bicubic.frag", "sgsr" to "sgsr.frag", "easu" to "fsr_easu.frag",
                "rcas" to "fsr_rcas.frag", "anime4k" to "anime4k.frag", "crt" to "retro_crt.frag",
                "final" to "final.frag"
            )) programs[k] = GlUtil.buildProgram(context, "fullscreen.vert", f)

            val t = IntArray(1)
            GLES30.glGenTextures(1, t, 0)
            oesTex = t[0]
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            val st = SurfaceTexture(oesTex)
            st.setDefaultBufferSize(srcW.get(), srcH.get())
            st.setOnFrameAvailableListener {
                frameAvailable.set(true)
                glView?.requestRender()
            }
            surfaceTexture = st
            surface = Surface(st)
            hasFrame = false
            needsProcess = true
            onInputSurface(surface)
            Log.i(TAG, "GL ready: ${GLES30.glGetString(GLES30.GL_RENDERER)} / ${GLES30.glGetString(GLES30.GL_VERSION)}")
        } catch (e: Throwable) {
            failed = true
            Log.e(TAG, "GL init failed", e)
            onError("渲染引擎初始化失败: ${e.message}")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = max(width, 1)
        screenH = max(height, 1)
        needsProcess = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (failed) { clearScreen(); return }
        try {
            draw()
        } catch (e: Throwable) {
            failed = true
            Log.e(TAG, "draw failed", e)
            onError("渲染失败: ${e.message}")
            clearScreen()
        }
    }

    private fun clearScreen() {
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun draw() {
        val s = settings
        var newFrame = false
        if (frameAvailable.getAndSet(false)) {
            val st = surfaceTexture
            if (st != null) {
                st.updateTexImage()
                st.getTransformMatrix(texMat)
                newFrame = true
                hasFrame = true
                statFrames++
            }
        }
        if (!hasFrame) { clearScreen(); return }

        val inW = srcW.get(); val inH = srcH.get()
        val rect = displayRect(s, inW, inH)

        if (inputFbo?.w != inW || inputFbo?.h != inH) {
            inputFbo?.delete()
            inputFbo = GlUtil.createFbo(inW, inH)
            needsProcess = true
            newFrame = true  // re-copy
        }
        val inFbo = inputFbo!!

        if (newFrame) {
            val p = programs.getValue("oes")
            bindTarget(inFbo.fbo, inFbo.w, inFbo.h)
            GLES30.glUseProgram(p.id)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES30.glUniform1i(p.loc("u_tex"), 0)
            GLES30.glUniformMatrix4fv(p.loc("u_uvMat"), 1, false, texMat, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        }

        val version = settingsVersion.get()
        if (newFrame || needsProcess || version != processedVersion) {
            val measure = (++processCount % 30 == 0)
            val t0 = if (measure) { GLES30.glFinish(); System.nanoTime() } else 0L
            runPipeline(s, inFbo, rect)
            if (measure) { GLES30.glFinish(); gpuMs = (System.nanoTime() - t0) / 1e6f }
            processedVersion = version
            needsProcess = false
        }

        drawFinal(s, inFbo, rect)
        reportStats(inW, inH)
    }

    // ------------------------------------------------------------------ pipeline
    private fun targetSize(s: RenderSettings, inW: Int, inH: Int, rect: FloatArray): Pair<Int, Int> {
        val aspect = inW.toFloat() / inH
        var h: Int
        var w: Int
        when (s.targetResolution) {
            TargetResolutionPreset.AUTO -> { w = rect[2].roundToInt(); h = rect[3].roundToInt() }
            TargetResolutionPreset.CUSTOM -> { h = (inH * s.customScaleFactor).roundToInt(); w = (h * aspect).roundToInt() }
            else -> { h = s.targetResolution.height; w = (h * aspect).roundToInt() }
        }
        w = min(max(w, 16), MAX_DIM); h = min(max(h, 16), MAX_DIM)
        return w to h
    }

    private fun ensureFbo(cur: GlFbo?, w: Int, h: Int): GlFbo =
        if (cur != null && cur.w == w && cur.h == h) cur else { cur?.delete(); GlUtil.createFbo(w, h) }

    private fun runPipeline(s: RenderSettings, inFbo: GlFbo, rect: FloatArray) {
        val (tw, th) = targetSize(s, inFbo.w, inFbo.h, rect)
        when (s.algorithm) {
            SRAlgorithmType.OFF -> { resultTex = inFbo.tex; resultW = inFbo.w; resultH = inFbo.h }
            SRAlgorithmType.BICUBIC -> {
                fboA = ensureFbo(fboA, tw, th)
                pass("bicubic", fboA!!, inFbo.tex) { p -> size2(p, "u_inSize", inFbo.w, inFbo.h) }
                setResult(fboA!!)
            }
            SRAlgorithmType.SGSR -> {
                fboA = ensureFbo(fboA, tw, th)
                pass("sgsr", fboA!!, inFbo.tex) { p ->
                    size2(p, "u_inSize", inFbo.w, inFbo.h)
                    GLES30.glUniform1f(p.loc("u_threshold"), s.sgsrThreshold)
                    GLES30.glUniform1f(p.loc("u_sharp"), s.sgsrSharpness)
                    GLES30.glUniform1i(p.loc("u_useDir"), if (s.sgsrUseDirection) 1 else 0)
                }
                setResult(fboA!!)
            }
            SRAlgorithmType.FSR -> {
                fboA = ensureFbo(fboA, tw, th)
                fboB = ensureFbo(fboB, tw, th)
                pass("easu", fboA!!, inFbo.tex) { p ->
                    size2(p, "u_inSize", inFbo.w, inFbo.h)
                    GLES30.glUniform1i(p.loc("u_dering"), if (s.fsrDering) 1 else 0)
                }
                pass("rcas", fboB!!, fboA!!.tex) { p ->
                    size2(p, "u_inSize", tw, th)
                    GLES30.glUniform1f(p.loc("u_sharp"), s.fsrSharpness)
                }
                setResult(fboB!!)
            }
            SRAlgorithmType.ANIME4K -> {
                fboA = ensureFbo(fboA, tw, th)
                fboB = ensureFbo(fboB, tw, th)
                pass("bicubic", fboA!!, inFbo.tex) { p -> size2(p, "u_inSize", inFbo.w, inFbo.h) }
                pass("anime4k", fboB!!, fboA!!.tex) { p ->
                    size2(p, "u_inSize", tw, th)
                    GLES30.glUniform1f(p.loc("u_strength"), s.anime4kStrength)
                    GLES30.glUniform1i(p.loc("u_mode"), s.anime4kMode)
                }
                setResult(fboB!!)
            }
            SRAlgorithmType.CRT -> {
                fboA = ensureFbo(fboA, tw, th)
                pass("crt", fboA!!, inFbo.tex) { p ->
                    size2(p, "u_outSize", tw, th)
                    GLES30.glUniform1f(p.loc("u_scanline"), s.crtScanline)
                    GLES30.glUniform1f(p.loc("u_curvature"), s.crtCurvature)
                    GLES30.glUniform1f(p.loc("u_mask"), s.crtMask)
                }
                setResult(fboA!!)
            }
        }
    }

    private fun setResult(f: GlFbo) { resultTex = f.tex; resultW = f.w; resultH = f.h }

    private fun size2(p: GlProgram, name: String, w: Int, h: Int) =
        GLES30.glUniform2f(p.loc(name), w.toFloat(), h.toFloat())

    private fun bindTarget(fbo: Int, w: Int, h: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, w, h)
    }

    private inline fun pass(name: String, target: GlFbo, inTex: Int, setup: (GlProgram) -> Unit) {
        val p = programs.getValue(name)
        bindTarget(target.fbo, target.w, target.h)
        GLES30.glUseProgram(p.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inTex)
        GLES30.glUniform1i(p.loc("u_tex"), 0)
        setup(p)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    // ------------------------------------------------------------------ final
    /** returns [x, y, w, h] in screen pixels, origin bottom-left. */
    private fun displayRect(s: RenderSettings, inW: Int, inH: Int): FloatArray {
        val sw = screenW.toFloat(); val sh = screenH.toFloat()
        val aspect = inW.toFloat() / inH
        val w: Float; val h: Float
        when (s.displayMode) {
            DisplayAspectMode.STRETCH -> { w = sw; h = sh }
            DisplayAspectMode.CENTER_CROP -> {
                if (sw / sh > aspect) { w = sw; h = sw / aspect } else { h = sh; w = sh * aspect }
            }
            else -> {
                if (sw / sh > aspect) { h = sh; w = sh * aspect } else { w = sw; h = sw / aspect }
            }
        }
        return floatArrayOf((sw - w) / 2f, (sh - h) / 2f, w, h)
    }

    private fun drawFinal(s: RenderSettings, inFbo: GlFbo, rect: FloatArray) {
        val p = programs.getValue("final")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glUseProgram(p.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (resultTex != 0) resultTex else inFbo.tex)
        GLES30.glUniform1i(p.loc("u_sr"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inFbo.tex)
        GLES30.glUniform1i(p.loc("u_raw"), 1)
        GLES30.glUniform2f(p.loc("u_screen"), screenW.toFloat(), screenH.toFloat())
        GLES30.glUniform4f(p.loc("u_rect"), rect[0], rect[1], rect[2], rect[3])
        GLES30.glUniform1i(p.loc("u_ambient"), if (s.displayMode == DisplayAspectMode.AMBILIGHT) 1 else 0)
        GLES30.glUniform1i(p.loc("u_split"), if (s.splitScreenEnabled) 1 else 0)
        GLES30.glUniform1f(p.loc("u_splitPos"), s.splitPosition)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun reportStats(inW: Int, inH: Int) {
        val now = System.nanoTime()
        val dt = now - statStart
        if (dt >= 1_000_000_000L) {
            val fps = statFrames * 1e9f / dt
            statFrames = 0
            statStart = now
            onStats(RenderStats(fps, gpuMs, inW, inH, resultW, resultH))
        }
    }

    // ------------------------------------------------------------------ cleanup
    private fun releaseGl() {
        onInputSurface(null)
        surface?.release(); surface = null
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release(); surfaceTexture = null
        // GL objects belong to the previous (dead) context if it was lost: just drop references.
        programs.clear()
        inputFbo = null; fboA = null; fboB = null
        resultTex = 0
        processedVersion = -1
        frameAvailable.set(false)
    }

    /** Must be called from the GL thread (GLSurfaceView.queueEvent). */
    fun release() {
        inputFbo?.delete(); fboA?.delete(); fboB?.delete()
        programs.values.forEach { it.delete() }
        releaseGl()
    }
}
