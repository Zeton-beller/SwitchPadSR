package com.zet.switchpadsr.data

import android.content.Context
import android.net.Uri
import com.zet.switchpadsr.model.*
import org.json.JSONArray
import org.json.JSONObject

data class RecentVideo(val uri: String, val name: String, val lastPositionMs: Long = 0L)

/** SharedPreferences backed persistence for settings and the recent videos list. */
class SettingsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("switchpadsr", Context.MODE_PRIVATE)

    fun load(): RenderSettings {
        val d = RenderSettings()
        val v160Migrated = sp.getBoolean("migrated_v160", false)
        if (!v160Migrated) {
            sp.edit()
                .putBoolean("migrated_v160", true)
                .putInt("algo", SRAlgorithmType.AI_HYBRID.id)
                .putInt("aiBackbone", AiBackbone.RAVU_LITE_AR.id)
                .apply()
        }
        return RenderSettings(
            algorithm = SRAlgorithmType.fromId(sp.getInt("algo", d.algorithm.id)),
            targetResolution = TargetResolutionPreset.fromName(sp.getString("target", null)),
            customScaleFactor = sp.getFloat("customScale", d.customScaleFactor),
            displayMode = DisplayAspectMode.fromId(sp.getInt("display", d.displayMode.id)),
            aiBackbone = AiBackbone.fromId(sp.getInt("aiBackbone", d.aiBackbone.id)),
            aiPostProcessor = AiPostProcessor.fromId(sp.getInt("aiPostProc", d.aiPostProcessor.id)),
            aiSharpness = sp.getFloat("aiSharp", d.aiSharpness),
            fsrcnnxSharpness = sp.getFloat("fsrcnnxSharp", d.fsrcnnxSharpness),
            artCnnHighQuality = sp.getBoolean("artCnnHq", d.artCnnHighQuality),
            sgsrThreshold = sp.getFloat("sgsrThr", d.sgsrThreshold),
            sgsrSharpness = sp.getFloat("sgsrSharp", d.sgsrSharpness),
            sgsrUseDirection = sp.getBoolean("sgsrDir", d.sgsrUseDirection),
            fsrSharpness = sp.getFloat("fsrSharp", d.fsrSharpness),
            fsrDering = sp.getBoolean("fsrDering", d.fsrDering),
            anime4kStrength = sp.getFloat("a4kStr", d.anime4kStrength),
            anime4kMode = sp.getInt("a4kMode", d.anime4kMode),
            crtScanline = sp.getFloat("crtScan", d.crtScanline),
            crtCurvature = sp.getFloat("crtCurv", d.crtCurvature),
            crtMask = sp.getFloat("crtMask", d.crtMask),
            splitScreenEnabled = false,
            splitPosition = sp.getFloat("splitPos", d.splitPosition),
            showHud = sp.getBoolean("hud", d.showHud),
            captureAudio = sp.getBoolean("capAudio", d.captureAudio),
            audioVolume = sp.getFloat("audioVol", d.audioVolume),
            loopVideo = sp.getBoolean("loop", d.loopVideo)
        )
    }

    fun save(s: RenderSettings) {
        sp.edit()
            .putInt("algo", s.algorithm.id)
            .putString("target", s.targetResolution.name)
            .putFloat("customScale", s.customScaleFactor)
            .putInt("display", s.displayMode.id)
            .putInt("aiBackbone", s.aiBackbone.id)
            .putInt("aiPostProc", s.aiPostProcessor.id)
            .putFloat("aiSharp", s.aiSharpness)
            .putFloat("fsrcnnxSharp", s.fsrcnnxSharpness)
            .putBoolean("artCnnHq", s.artCnnHighQuality)
            .putFloat("sgsrThr", s.sgsrThreshold)
            .putFloat("sgsrSharp", s.sgsrSharpness)
            .putBoolean("sgsrDir", s.sgsrUseDirection)
            .putFloat("fsrSharp", s.fsrSharpness)
            .putBoolean("fsrDering", s.fsrDering)
            .putFloat("a4kStr", s.anime4kStrength)
            .putInt("a4kMode", s.anime4kMode)
            .putFloat("crtScan", s.crtScanline)
            .putFloat("crtCurv", s.crtCurvature)
            .putFloat("crtMask", s.crtMask)
            .putFloat("splitPos", s.splitPosition)
            .putBoolean("hud", s.showHud)
            .putBoolean("capAudio", s.captureAudio)
            .putFloat("audioVol", s.audioVolume)
            .putBoolean("loop", s.loopVideo)
            .apply()
    }

    fun lastMode(): InputSourceMode =
        if (sp.getString("lastMode", null) == "VIDEO") InputSourceMode.LOCAL_VIDEO else InputSourceMode.CAPTURE_CARD

    fun saveLastMode(m: InputSourceMode) {
        sp.edit().putString("lastMode", if (m == InputSourceMode.LOCAL_VIDEO) "VIDEO" else "CAPTURE").apply()
    }

    fun recents(): List<RecentVideo> {
        val raw = sp.getString("recents", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                RecentVideo(o.getString("uri"), o.optString("name", "视频"), o.optLong("pos", 0L))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun addRecent(uri: Uri, name: String, positionMs: Long = 0L) {
        val list = recents().filter { it.uri != uri.toString() }.toMutableList()
        list.add(0, RecentVideo(uri.toString(), name, positionMs))
        writeRecents(list.take(12))
    }

    fun updatePosition(uri: String, positionMs: Long) {
        writeRecents(recents().map { if (it.uri == uri) it.copy(lastPositionMs = positionMs) else it })
    }

    fun removeRecent(uri: String) = writeRecents(recents().filter { it.uri != uri })

    private fun writeRecents(list: List<RecentVideo>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("pos", it.lastPositionMs))
        }
        sp.edit().putString("recents", arr.toString()).apply()
    }
}
