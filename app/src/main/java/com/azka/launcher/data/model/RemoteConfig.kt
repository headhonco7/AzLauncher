package com.azka.launcher.data.model

/**
 * Prinsip final:
 * - Layout & komponen bersifat FIX (tidak update dari config),
 * - Kecuali yang diperbolehkan: wallpaper, wifi ssid+pass, hero items, runningText.
 *
 * Catatan:
 * Data model masih menyimpan banyak field untuk kompatibilitas, tapi parser akan mengabaikannya.
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
            val format24h: Boolean = true,
            val showDate: Boolean = true,
            val dateLocale: String = "id"
        )

        data class Weather(
            val enabled: Boolean = false,
            val mode: String = "static_text",
            val text: String = ""
        )
    }

    data class Hero(
        val enabled: Boolean = true,
        val autoSlide: Boolean = true,
        val intervalMs: Long = 10_000L, // FIX 8–12 detik range nanti di UI
        val items: List<HeroItem> = emptyList()
    ) {
        data class HeroItem(
            val imageUrl: String? = null,
            val title: String = "",
            val subtitle: String = "",
            val note: String = "",
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
        val whatsappFoText: String = "WhatsApp Front Office",
        // FIX sesuai permintaan user (tidak akan diambil dari config)
        val whatsappNumber: String = "0851 22000 590",
        val whatsappNote: String = "",
        val socialText: String = "",
        val socialHandle: String = ""
    )

    data class WifiCard(
        val enabled: Boolean = true,
        val title: String = "Wi-Fi Info",
        val ssid: String = "HotelWifi",
        val password: String = "12345678",
        val encryption: String = "WPA",
        val showQr: Boolean = true,
        val hintText: String = "Scan untuk terhubung"
    )

    data class AppsRow(
        val enabled: Boolean = true,
        val title: String = "Apps",
        // FIX 5 item: Netflix, YouTube, IPTV1, IPTV2, Cast
        val items: List<AppItem> = listOf(
            AppItem(id = "netflix", label = "Netflix", packageName = "com.netflix.ninja", iconUrl = null),
            AppItem(id = "youtube", label = "YouTube", packageName = "com.google.android.youtube.tv", iconUrl = null),
            AppItem(id = "iptv1", label = "IPTV 1", packageName = "__IPTV_1__", iconUrl = null),
            AppItem(id = "iptv2", label = "IPTV 2", packageName = "__IPTV_2__", iconUrl = null),
            AppItem(id = "cast", label = "Cast", packageName = "com.google.android.gms.cast", iconUrl = null)
        )
    ) {
        data class AppItem(
            val id: String,
            val label: String,
            val packageName: String,
            val iconUrl: String?
        )
    }

    data class RunningText(
        val enabled: Boolean = true,
        val text: String = "• Check-out pukul 12.00 • Water refill tersedia di lobby • Hubungi FO via WhatsApp"
    )
}
