package com.zet.switchpadsr.gl

import android.content.Context
import android.opengl.GLES30
import android.util.Log

private const val TAG = "SwitchPadSR-GL"

class GlProgram(val id: Int) {
    private val locations = HashMap<String, Int>()
    fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    fun delete() = GLES30.glDeleteProgram(id)
}

class GlFbo(val tex: Int, val fbo: Int, val w: Int, val h: Int) {
    fun delete() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
    }
}

object GlUtil {
    fun readAsset(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader().use { it.readText() }

    private fun compile(type: Int, src: String, name: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            throw RuntimeException("Shader compile failed ($name): $log")
        }
        return s
    }

    fun buildProgram(ctx: Context, vert: String, frag: String): GlProgram {
        val vs = compile(GLES30.GL_VERTEX_SHADER, readAsset(ctx, "shaders/$vert"), vert)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, readAsset(ctx, "shaders/$frag"), frag)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw RuntimeException("Program link failed ($frag): $log")
        }
        Log.i(TAG, "Program $frag linked")
        return GlProgram(p)
    }

    fun createFbo(w: Int, h: Int): GlFbo {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, tex[0], 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(1, tex, 0)
            throw RuntimeException("FBO incomplete: 0x${Integer.toHexString(status)} (${w}x$h)")
        }
        return GlFbo(tex[0], fbo[0], w, h)
    }
}
