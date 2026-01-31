package com.azka.launcher.data.model

/**
 * Config v1 (ringan) yang cukup untuk men-drive UI mock kamu.
 * Parsing kita buat toleran: kalau field hilang, pakai default aman.
 */
data class RemoteConfig(
    val schemaVersion: Int = 1,
    val branding: Branding = Branding(),
    val background: Background = Background(),
    val topRight: TopRight = TopRight(),
    val hero: Hero = Hero(),
    val roomLabel: RoomLabel = RoomLabel(),
    val contact: Contact = Contact(),
    val wifiCard: WifiCard = WifiCard(),
    val appsRow: AppsRow = AppsRow(),
    val runningText: RunningText = RunningText()
) {
    data class Branding(
        val appTitle: String = "AzLauncher",
        val logoUrl: String? = null
    )

    data class Background(
        val type: String = "image",
        val url: String? = null
    )

    data class TopRight(
        val clock: Clock = Clock(),
        val weather: Weather = Weather()
    ) {
        data class Clock(
            val enabled: Boolean = true,
            val format24h: Boolean = true
        )

        data class Weather(
            val enabled: Boolean = true,
            val mode: String = "static_text",
            val text: String = "Sunny • 28°C"
        )
    }

    data class Hero(
        val enabled: Boolean = true,
        val autoSlide: Boolean = true,
        val intervalMs: Long = 8000L,
        val items: List<HeroItem> = emptyList()
    ) {
        data class HeroItem(
            val imageUrl: String? = null,
            val action: Action = Action()
        ) {
            data class Action(
                val type: String = "none",
                val packageName: String? = null
            )
        }
    }

    data class RoomLabel(
        val enabled: Boolean = true,
        val prefix: String = "ROOM",
        val source: String = "device_name",
        val fallbackSource: String = "build_model"
    )

    data class Contact(
        val enabled: Boolean = true,
        val whatsappFoText: String = "WhatsApp FO",
        val whatsappNumber: String = "+62812XXXXXXX",
        val socialText: String = "Akun Sosmed",
        val socialHandle: String = "@guesthouse"
    )

    data class WifiCard(
        val enabled: Boolean = true,
        val title: String = "WiFi",
        val ssid: String = "AZ-GUEST",
        val password: String = "az12345",
        val encryption: String = "WPA",
        val showQr: Boolean = true
    )

    data class AppsRow(
        val enabled: Boolean = true,
        val title: String = "Apps",
        val items: List<AppItem> = listOf(
            AppItem(id = "youtube", label = "YouTube", packageName = "com.google.android.youtube.tv"),
            AppItem(id = "netflix", label = "Netflix", packageName = "com.netflix.ninja"),
            AppItem(id = "iptv1", label = "Live TV 1", packageName = "__IPTV1_PACKAGE__"),
            AppItem(id = "iptv2", label = "Live TV 2", packageName = "__IPTV2_PACKAGE__")
        )
    ) {
        data class AppItem(
            val id: String,
            val label: String,
            val packageName: String,
            val iconUrl: String? = null
        )
    }

    data class RunningText(
        val enabled: Boolean = true,
        val text: String = "Selamat datang • Check-out pukul 12.00 • Hubungi FO via WhatsApp"
    )
}
