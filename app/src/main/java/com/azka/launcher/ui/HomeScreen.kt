package com.azka.launcher.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azka.launcher.data.model.RemoteConfig
import com.azka.launcher.data.repo.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tahap 2.2:
 * - UI sudah digerakkan oleh RemoteConfig (teks dulu).
 * - Load cache cepat, fetch remote di background, simpan cache bila sukses.
 * - Belum render gambar/QR/slideshow: kita jaga compile hijau & minim risiko.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { ConfigRepository(context) }

    var config by remember { mutableStateOf(RemoteConfig()) }
    var statusText by remember { mutableStateOf("CONFIG: default") }

    // Jam sederhana (update 1x per menit)
    val timeText by rememberClockText(format24h = config.topRight.clock.format24h)

    // Load cache + fetch remote sekali saat start
    LaunchedEffect(Unit) {
        val cached = repo.loadCachedConfigOrNull()
        if (cached != null) {
            config = cached
            statusText = "CONFIG: cache"
        }

        // URL sementara. Nanti (Tahap Admin) kita buat bisa diedit.
        val url = ConfigRepository.DEFAULT_CONFIG_URL

        // Kalau masih default placeholder example.com, kita skip fetch supaya tidak buang waktu.
        if (url.startsWith("https://example.com")) {
            statusText = "CONFIG: url not set"
            return@LaunchedEffect
        }

        try {
            val (remoteCfg, raw) = withContext(Dispatchers.IO) {
                repo.fetchRemoteConfig(url)
            }
            config = remoteCfg
            repo.saveCache(raw)
            statusText = "CONFIG: remote"
        } catch (_: Throwable) {
            // Biarkan pakai cache/default
            if (cached != null) statusText = "CONFIG: cache (remote failed)" else statusText = "CONFIG: default (remote failed)"
        }
    }

    val roomName = remember { getDeviceNameForRoom(context) }
    val roomLabel = buildString {
        append(config.roomLabel.prefix.ifBlank { "ROOM" })
        append(" ")
        append(roomName.ifBlank { "UNKNOWN" })
    }

    val whatsappLine = "${config.contact.whatsappFoText}: ${config.contact.whatsappNumber}"
    val socialLine = "${config.contact.socialText}: ${config.contact.socialHandle}"

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // wallpaper placeholder (nanti diganti image remote)
                .background(Color(0xFF101318))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ===== Top bar: LOGO kiri, WEATHER + JAM kanan =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // LOGO placeholder (nanti load image via URL config.branding.logoUrl)
                    Box(
                        modifier = Modifier
                            .size(width = 160.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F2630))
                            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = config.branding.appTitle.ifBlank { "AzLauncher" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFD7E3F4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // WEATHER (static_text dari config dulu)
                    if (config.topRight.weather.enabled) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F2630))
                                .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = config.topRight.weather.text.ifBlank { "Sunny • 28°C" },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFB7C7DD),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // JAM
                    if (config.topRight.clock.enabled) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .width(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F2630))
                                .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFB7C7DD),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ===== Mid area =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // LEFT: SLIDE SHOW (placeholder)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1B2230))
                                .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (config.hero.enabled) "HERO (banner)" else "HERO disabled",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFD7E3F4)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ROOM LABEL
                        if (config.roomLabel.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF151B25))
                                    .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = roomLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFE6EEF9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // RIGHT: Contact + WiFi Card
                    Column(
                        modifier = Modifier
                            .widthIn(min = 320.dp, max = 420.dp)
                            .fillMaxHeight()
                    ) {
                        // CONTACT (info only)
                        if (config.contact.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1F2630))
                                    .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = whatsappLine,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFD7E3F4),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = socialLine,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFD7E3F4),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // WIFI INFO CARD + QR (placeholder QR)
                        if (config.wifiCard.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1B2230))
                                    .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = config.wifiCard.title.ifBlank { "WiFi" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFD7E3F4)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "SSID: ${config.wifiCard.ssid}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB7C7DD),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "PASS: ${config.wifiCard.password}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFB7C7DD),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (config.wifiCard.showQr) {
                                        Box(
                                            modifier = Modifier
                                                .size(170.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0F141D))
                                                .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "QR",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFB7C7DD)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ===== Apps Row (zona fokus DPAD) =====
                if (config.appsRow.enabled) {
                    val items = config.appsRow.items
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(items) { item ->
                            AppTile(label = item.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ===== Running text =====
                if (config.runningText.enabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E131B))
                            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = config.runningText.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB7C7DD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ===== Status kecil (debug sementara) =====
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5E6B7D),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppTile(label: String) {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2630))
            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(16.dp))
            .focusable()
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFD7E3F4),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberClockText(format24h: Boolean): State<String> {
    val formatter = remember(format24h) {
        SimpleDateFormat(if (format24h) "HH:mm" else "hh:mm a", Locale.getDefault())
    }
    val state = remember { mutableStateOf(formatter.format(Date())) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(formatter) {
        // Update tiap menit agar ringan
        while (true) {
            state.value = formatter.format(Date())
            val now = System.currentTimeMillis()
            val nextMinute = ((now / 60000L) + 1) * 60000L
            val delayMs = nextMinute - now
            kotlinx.coroutines.delay(delayMs.coerceAtLeast(250L))
        }
    }

    // Scope dipakai oleh Compose secara internal; tidak perlu dipakai sekarang
    @Suppress("UNUSED_VARIABLE")
    val _unused = scope

    return state
}

private fun getDeviceNameForRoom(context: Context): String {
    // Prioritas: Settings.Global.DEVICE_NAME (kalau teknisi set per kamar)
    val global = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    if (!global.isNullOrBlank()) return global.trim()

    // Fallback: beberapa device vendor menaruh di Secure
    val secure = runCatching {
        Settings.Secure.getString(context.contentResolver, "device_name")
    }.getOrNull()
    if (!secure.isNullOrBlank()) return secure.trim()

    // Fallback terakhir
    return Build.MODEL?.trim().orEmpty()
}
