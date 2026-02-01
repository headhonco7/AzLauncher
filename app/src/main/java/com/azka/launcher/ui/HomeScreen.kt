package com.azka.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azka.launcher.data.model.RemoteConfig
import com.azka.launcher.data.repo.ConfigRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

/**
 * Tahap 3.3:
 * - QR WiFi beneran (standar: WIFI:T:...;S:...;P:...;;)
 * - Tetap aman kalau gagal generate -> fallback teks "QR"
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { ConfigRepository(context) }

    var config by remember { mutableStateOf(RemoteConfig()) }
    var statusText by remember { mutableStateOf("CONFIG: default") }

    val timeText by rememberClockText(format24h = config.topRight.clock.format24h)

    // Hero slideshow state
    val heroItems = config.hero.items.filter { !it.imageUrl.isNullOrBlank() }
    var heroIndex by remember { mutableIntStateOf(0) }

    // Load cache + fetch remote sekali saat start
    LaunchedEffect(Unit) {
        val cached = repo.loadCachedConfigOrNull()
        if (cached != null) {
            config = cached
            statusText = "CONFIG: cache"
        }

        val url = ConfigRepository.DEFAULT_CONFIG_URL
        if (url.contains("REPLACE_ME") || url.startsWith("https://example.com")) {
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
            statusText = if (cached != null) "CONFIG: cache (remote failed)" else "CONFIG: default (remote failed)"
        }
    }

    // Slideshow hero (auto) — ringan
    LaunchedEffect(config.hero.enabled, config.hero.autoSlide, config.hero.intervalMs, heroItems.size) {
        heroIndex = 0
        if (!config.hero.enabled) return@LaunchedEffect
        if (!config.hero.autoSlide) return@LaunchedEffect
        if (heroItems.isEmpty()) return@LaunchedEffect

        while (true) {
            kotlinx.coroutines.delay(config.hero.intervalMs.coerceAtLeast(2000L))
            heroIndex = (heroIndex + 1) % heroItems.size
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

    // ===== QR generation (async, cached by inputs) =====
    val wifiQrBitmapState: State<Bitmap?> = produceState<Bitmap?>(initialValue = null,
        key1 = config.wifiCard.ssid,
        key2 = config.wifiCard.password,
        key3 = config.wifiCard.encryption,
        key4 = config.wifiCard.showQr,
        key5 = config.wifiCard.enabled
    ) {
        if (!config.wifiCard.enabled || !config.wifiCard.showQr) {
            value = null
            return@produceState
        }
        val ssid = config.wifiCard.ssid
        val pass = config.wifiCard.password
        val enc = config.wifiCard.encryption
        if (ssid.isBlank()) {
            value = null
            return@produceState
        }

        value = withContext(Dispatchers.Default) {
            runCatching {
                val wifiPayload = buildWifiQrPayload(
                    ssid = ssid,
                    password = pass,
                    encryption = enc
                )
                generateQrBitmap(
                    text = wifiPayload,
                    sizePx = 520
                )
            }.getOrNull()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ===== Wallpaper (image) =====
            if (!config.background.url.isNullOrBlank()) {
                AsyncImage(
                    model = config.background.url,
                    contentDescription = "Wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xAA0B0F16), Color(0xD0101318))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101318))
                )
            }

            // ===== Content =====
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // ===== Top bar =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x551F2630))
                            .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (!config.branding.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = config.branding.logoUrl,
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = config.branding.appTitle.ifBlank { "AzLauncher" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE6EEF9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (config.topRight.weather.enabled) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x551F2630))
                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(10.dp))
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

                    if (config.topRight.clock.enabled) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .width(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x551F2630))
                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(10.dp)),
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
                                .background(Color(0x441B2230))
                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (config.hero.enabled && heroItems.isNotEmpty()) {
                                val current = heroItems[heroIndex.coerceIn(0, heroItems.lastIndex)]
                                AsyncImage(
                                    model = current.imageUrl,
                                    contentDescription = "Hero Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0x33000000), Color(0x66000000))
                                            )
                                        )
                                )
                            } else {
                                Text(
                                    text = if (config.hero.enabled) "HERO (no items)" else "HERO disabled",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color(0xFFD7E3F4)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (config.roomLabel.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x55151B25))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp))
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

                    Column(
                        modifier = Modifier
                            .widthIn(min = 320.dp, max = 420.dp)
                            .fillMaxHeight()
                    ) {
                        if (config.contact.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x551F2630))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
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

                        if (config.wifiCard.enabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x441B2230))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
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
                                        val bmp = wifiQrBitmapState.value
                                        Box(
                                            modifier = Modifier
                                                .size(170.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0F141D))
                                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "WiFi QR",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            } else {
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
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            AppTile(label = item.label, iconUrl = item.iconUrl)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (config.runningText.enabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x550E131B))
                            .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp))
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
private fun AppTile(label: String, iconUrl: String?) {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x551F2630))
            .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!iconUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x332A3442), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = "$label icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD7E3F4),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberClockText(format24h: Boolean): State<String> {
    val formatter = remember(format24h) {
        SimpleDateFormat(if (format24h) "HH:mm" else "hh:mm a", Locale.getDefault())
    }
    val state = remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(formatter) {
        while (true) {
            state.value = formatter.format(Date())
            val now = System.currentTimeMillis()
            val nextMinute = ((now / 60000L) + 1) * 60000L
            val delayMs = nextMinute - now
            kotlinx.coroutines.delay(delayMs.coerceAtLeast(250L))
        }
    }
    return state
}

private fun getDeviceNameForRoom(context: Context): String {
    val global = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    if (!global.isNullOrBlank()) return global.trim()

    val secure = runCatching {
        Settings.Secure.getString(context.contentResolver, "device_name")
    }.getOrNull()
    if (!secure.isNullOrBlank()) return secure.trim()

    return Build.MODEL?.trim().orEmpty()
}

/**
 * Standar QR WiFi:
 * WIFI:T:WPA;S:<ssid>;P:<password>;;
 * T bisa: WPA / WEP / nopass
 */
private fun buildWifiQrPayload(ssid: String, password: String, encryption: String): String {
    val t = when (encryption.trim().uppercase(Locale.US)) {
        "WEP" -> "WEP"
        "NOPASS", "OPEN", "NONE" -> "nopass"
        else -> "WPA"
    }
    val s = escapeWifiField(ssid)
    val p = escapeWifiField(password)
    return if (t == "nopass") {
        "WIFI:T:nopass;S:$s;;"
    } else {
        "WIFI:T:$t;S:$s;P:$p;;"
    }
}

private fun escapeWifiField(value: String): String {
    // Escape karakter spesial sesuai praktik umum payload WiFi QR
    // \ ; , : " -> di-escape dengan backslash
    return value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")
}

private fun generateQrBitmap(text: String, sizePx: Int): Bitmap {
    val writer = QRCodeWriter()
    val hints: EnumMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
    hints[EncodeHintType.MARGIN] = 1

    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    // Warna: hitam-putih (kontras tinggi, cocok TV)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) black else white)
        }
    }
    return bmp
}
