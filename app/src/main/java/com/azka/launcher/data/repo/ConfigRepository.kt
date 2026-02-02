package com.azka.launcher.data.repo

import android.content.Context
import com.azka.launcher.data.model.RemoteConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ConfigRepository(
    private val context: Context
) {
    companion object {
        const val DEFAULT_CONFIG_URL =
            "https://raw.githubusercontent.com/headhonco7/azlauncher-content/main/config.json"

        private const val CACHE_FILE_NAME = "azlauncher_config_cache.json"
        private const val META_FILE_NAME = "azlauncher_config_meta.txt"

        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 12000
    }

    private val cacheFile: File get() = File(context.filesDir, CACHE_FILE_NAME)
    private val metaFile: File get() = File(context.filesDir, META_FILE_NAME)

    data class FetchResult(
        val updated: Boolean,
        val config: RemoteConfig?,
        val rawJson: String?
    )

    fun loadCachedConfigOrNull(): RemoteConfig? {
        return try {
            if (!cacheFile.exists()) return null
            val json = cacheFile.readText(Charsets.UTF_8)
            parseConfigAllowedOnly(json)
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveCache(rawJson: String) {
        try {
            cacheFile.writeText(rawJson, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    private fun saveMeta(meta: String) {
        try {
            metaFile.writeText(meta, Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    private fun loadMetaOrNull(): String? {
        return try {
            if (!metaFile.exists()) return null
            metaFile.readText(Charsets.UTF_8).trim().ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    fun fetchRemoteConfigIfChanged(configUrl: String): FetchResult {
        val meta = loadMetaOrNull()
        val res = httpGetConditional(configUrl, meta)

        if (res.code == 304) {
            return FetchResult(updated = false, config = null, rawJson = null)
        }

        if (res.code !in 200..299 || res.body == null) {
            throw RuntimeException("HTTP ${res.code}")
        }

        val cfg = parseConfigAllowedOnly(res.body)
        saveCache(res.body)

        val newMeta = res.etag ?: res.lastModified
        if (!newMeta.isNullOrBlank()) saveMeta(newMeta)

        return FetchResult(updated = true, config = cfg, rawJson = res.body)
    }

    private data class HttpRes(
        val code: Int,
        val body: String?,
        val etag: String?,
        val lastModified: String?
    )

    private fun httpGetConditional(url: String, meta: String?): HttpRes {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")

        if (!meta.isNullOrBlank()) {
            conn.setRequestProperty("If-None-Match", meta)
            conn.setRequestProperty("If-Modified-Since", meta)
        }

        conn.connect()
        val code = conn.responseCode
        val etag = conn.getHeaderField("ETag")
        val lastMod = conn.getHeaderField("Last-Modified")

        if (code == 304) {
            return HttpRes(code = 304, body = null, etag = etag, lastModified = lastMod)
        }

        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else null

        return HttpRes(code = code, body = body, etag = etag, lastModified = lastMod)
    }

    /**
     * HANYA parse field yang diizinkan update dari config:
     * - background.url
     * - wifiCard.ssid + wifiCard.password
     * - hero.items[].imageUrl/title/subtitle/note
     * - runningText.text
     *
     * Semua komponen lain FIX (ambil dari default RemoteConfig()).
     */
    private fun parseConfigAllowedOnly(rawJson: String): RemoteConfig {
        val base = RemoteConfig() // semua default FIX dari sini

        val root = JSONObject(rawJson)
        val schemaVersion = root.optInt("schemaVersion", base.schemaVersion)

        // (1) Wallpaper
        val bgObj = root.optJSONObject("background")
        val bgUrl = bgObj?.optString("url", null)
        val background = base.background.copy(
            url = bgUrl ?: base.background.url
        )

        // (2) WiFi SSID + PASS saja
        val wifiObj = root.optJSONObject("wifiCard")
        val ssid = wifiObj?.optString("ssid", null)
        val pass = wifiObj?.optString("password", null)
        val wifiCard = base.wifiCard.copy(
            ssid = ssid ?: base.wifiCard.ssid,
            password = pass ?: base.wifiCard.password
        )

        // (3) Hero items (gambar + teks)
        val heroObj = root.optJSONObject("hero")
        val heroItems = mutableListOf<RemoteConfig.Hero.HeroItem>()
        val heroItemsArr = heroObj?.optJSONArray("items") ?: JSONArray()
        for (i in 0 until heroItemsArr.length()) {
            val itemObj = heroItemsArr.optJSONObject(i) ?: continue
            val imageUrl = itemObj.optString("imageUrl", null)
            if (imageUrl.isNullOrBlank()) continue
            heroItems += RemoteConfig.Hero.HeroItem(
                imageUrl = imageUrl,
                title = itemObj.optString("title", ""),
                subtitle = itemObj.optString("subtitle", ""),
                note = itemObj.optString("note", ""),
                action = RemoteConfig.Hero.HeroItem.Action(type = "none", packageName = null) // FIX: action tidak dipakai
            )
        }
        val hero = base.hero.copy(
            items = heroItems
            // enabled/autoSlide/intervalMs tetap FIX dari base.hero
        )

        // (4) Running text
        val runObj = root.optJSONObject("runningText")
        val runningText = base.runningText.copy(
            text = runObj?.optString("text", base.runningText.text) ?: base.runningText.text
        )

        return base.copy(
            schemaVersion = schemaVersion,
            background = background,
            wifiCard = wifiCard,
            hero = hero,
            runningText = runningText
        )
    }
}
