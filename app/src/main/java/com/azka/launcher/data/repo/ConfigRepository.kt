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
        // Nanti URL ini kita pindah jadi bisa diubah via AdminScreen (Tahap 5).
        // Untuk sekarang: aman default "belum".
        const val DEFAULT_CONFIG_URL = "https://example.com/config.json"

        private const val CACHE_FILE_NAME = "azlauncher_config_cache.json"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 12000
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    fun loadCachedConfigOrNull(): RemoteConfig? {
        return try {
            if (!cacheFile.exists()) return null
            val json = cacheFile.readText(Charsets.UTF_8)
            parseConfig(json)
        } catch (_: Throwable) {
            null
        }
    }

    fun saveCache(rawJson: String) {
        try {
            cacheFile.writeText(rawJson, Charsets.UTF_8)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Fetch remote JSON. Return Pair(config, rawJson) jika sukses.
     */
    fun fetchRemoteConfig(configUrl: String): Pair<RemoteConfig, String> {
        val raw = httpGet(configUrl)
        val cfg = parseConfig(raw)
        return cfg to raw
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.connect()

        val code = conn.responseCode
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code")
        }

        val stream = conn.inputStream
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Parser toleran: field boleh hilang -> pakai default.
     * Tidak pakai dependency tambahan (kita pakai org.json bawaan).
     */
    private fun parseConfig(rawJson: String): RemoteConfig {
        val root = JSONObject(rawJson)

        val schemaVersion = root.optInt("schemaVersion", 1)

        val brandingObj = root.optJSONObject("branding")
        val branding = RemoteConfig.Branding(
            appTitle = brandingObj?.optString("appTitle", "AzLauncher") ?: "AzLauncher",
            logoUrl = brandingObj?.optString("logoUrl", null)
        )

        val bgObj = root.optJSONObject("background")
        val background = RemoteConfig.Background(
            type = bgObj?.optString("type", "image") ?: "image",
            url = bgObj?.optString("url", null)
        )

        val topRightObj = root.optJSONObject("topRight")
        val clockObj = topRightObj?.optJSONObject("clock")
        val weatherObj = topRightObj?.optJSONObject("weather")

        val topRight = RemoteConfig.TopRight(
            clock = RemoteConfig.TopRight.Clock(
                enabled = clockObj?.optBoolean("enabled", true) ?: true,
                format24h = clockObj?.optBoolean("format24h", true) ?: true
            ),
            weather = RemoteConfig.TopRight.Weather(
                enabled = weatherObj?.optBoolean("enabled", true) ?: true,
                mode = weatherObj?.optString("mode", "static_text") ?: "static_text",
                text = weatherObj?.optString("text", "Sunny • 28°C") ?: "Sunny • 28°C"
            )
        )

        val heroObj = root.optJSONObject("hero")
        val heroItems = mutableListOf<RemoteConfig.Hero.HeroItem>()
        val heroItemsArr = heroObj?.optJSONArray("items") ?: JSONArray()
        for (i in 0 until heroItemsArr.length()) {
            val itemObj = heroItemsArr.optJSONObject(i) ?: continue
            val actionObj = itemObj.optJSONObject("action")
            val action = RemoteConfig.Hero.HeroItem.Action(
                type = actionObj?.optString("type", "none") ?: "none",
                packageName = actionObj?.optString("packageName", null)
            )
            heroItems += RemoteConfig.Hero.HeroItem(
                imageUrl = itemObj.optString("imageUrl", null),
                action = action
            )
        }

        val hero = RemoteConfig.Hero(
            enabled = heroObj?.optBoolean("enabled", true) ?: true,
            autoSlide = heroObj?.optBoolean("autoSlide", true) ?: true,
            intervalMs = heroObj?.optLong("intervalMs", 8000L) ?: 8000L,
            items = heroItems
        )

        val roomObj = root.optJSONObject("roomLabel")
        val roomLabel = RemoteConfig.RoomLabel(
            enabled = roomObj?.optBoolean("enabled", true) ?: true,
            prefix = roomObj?.optString("prefix", "ROOM") ?: "ROOM",
            source = roomObj?.optString("source", "device_name") ?: "device_name",
            fallbackSource = roomObj?.optString("fallbackSource", "build_model") ?: "build_model"
        )

        val contactObj = root.optJSONObject("contact")
        val contact = RemoteConfig.Contact(
            enabled = contactObj?.optBoolean("enabled", true) ?: true,
            whatsappFoText = contactObj?.optString("whatsappFoText", "WhatsApp FO") ?: "WhatsApp FO",
            whatsappNumber = contactObj?.optString("whatsappNumber", "+62812XXXXXXX") ?: "+62812XXXXXXX",
            socialText = contactObj?.optString("socialText", "Akun Sosmed") ?: "Akun Sosmed",
            socialHandle = contactObj?.optString("socialHandle", "@guesthouse") ?: "@guesthouse"
        )

        val wifiObj = root.optJSONObject("wifiCard")
        val wifiCard = RemoteConfig.WifiCard(
            enabled = wifiObj?.optBoolean("enabled", true) ?: true,
            title = wifiObj?.optString("title", "WiFi") ?: "WiFi",
            ssid = wifiObj?.optString("ssid", "AZ-GUEST") ?: "AZ-GUEST",
            password = wifiObj?.optString("password", "az12345") ?: "az12345",
            encryption = wifiObj?.optString("encryption", "WPA") ?: "WPA",
            showQr = wifiObj?.optBoolean("showQr", true) ?: true
        )

        val appsRowObj = root.optJSONObject("appsRow")
        val appsArr = appsRowObj?.optJSONArray("items") ?: JSONArray()
        val appItems = mutableListOf<RemoteConfig.AppsRow.AppItem>()
        for (i in 0 until appsArr.length()) {
            val itObj = appsArr.optJSONObject(i) ?: continue
            val id = itObj.optString("id", "item_$i")
            val label = itObj.optString("label", id)
            val pkg = itObj.optString("packageName", "")
            val iconUrl = itObj.optString("iconUrl", null)
            if (label.isNotBlank() && pkg.isNotBlank()) {
                appItems += RemoteConfig.AppsRow.AppItem(
                    id = id,
                    label = label,
                    packageName = pkg,
                    iconUrl = iconUrl
                )
            }
        }
        val appsRow = RemoteConfig.AppsRow(
            enabled = appsRowObj?.optBoolean("enabled", true) ?: true,
            title = appsRowObj?.optString("title", "Apps") ?: "Apps",
            items = if (appItems.isNotEmpty()) appItems else RemoteConfig.AppsRow().items
        )

        val runObj = root.optJSONObject("runningText")
        val runningText = RemoteConfig.RunningText(
            enabled = runObj?.optBoolean("enabled", true) ?: true,
            text = runObj?.optString(
                "text",
                "Selamat datang • Check-out pukul 12.00 • Hubungi FO via WhatsApp"
            ) ?: "Selamat datang • Check-out pukul 12.00 • Hubungi FO via WhatsApp"
        )

        return RemoteConfig(
            schemaVersion = schemaVersion,
            branding = branding,
            background = background,
            topRight = topRight,
            hero = hero,
            roomLabel = roomLabel,
            contact = contact,
            wifiCard = wifiCard,
            appsRow = appsRow,
            runningText = runningText
        )
    }
}
