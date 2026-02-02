package com.azka.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azka.launcher.R
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

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { ConfigRepository(context) }

    // FIX WhatsApp number (tidak boleh dari config)
    val FIXED_WA_NUMBER = "0851 22000 590"

    // FIX app list sesuai permintaan (package name umum Android TV)
    val fixedApps = remember {
        listOf(
            FixedApp("Netflix", "com.netflix.ninja"),
            FixedApp("YouTube", "com.google.android.youtube.tv"),
            FixedApp("Vidio", "com.vidio.android"),
            FixedApp("YouTube Kids", "com.google.android.apps.youtube.kids"),
            FixedApp("Spotify", "com.spotify.tv.android")
        )
    }

    var config by remember { mutableStateOf(RemoteConfig()) }
    var statusText by remember { mutableStateOf("CONFIG: default") }
    var refreshToken by remember { mutableIntStateOf(0) }

    val timeText by rememberClockText(format24h = true)
    val dateText by rememberDateText(enabled = true, localeTag = "id")

    val heroItems = config.hero.items.filter { !it.imageUrl.isNullOrBlank() }
    var heroIndex by remember { mutableIntStateOf(0) }

    var wifiQrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Focus: Apps row
    val appFocusRequesters = remember { List(5) { FocusRequester() } }

    // Info dialog
    var infoDialogOpen by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf("Info") }
    var infoDialogMessage by remember { mutableStateOf("") }
    fun showInfo(title: String, msg: String) {
        infoDialogTitle = title
        infoDialogMessage = msg
        infoDialogOpen = true
    }

    // Admin PIN
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var adminMenuOpen by remember { mutableStateOf(false) }
    val ADMIN_PIN = "1234"

    fun shortErr(t: Throwable): String {
        val name = t::class.java.simpleName.ifBlank { "Error" }
        val msg = (t.message ?: "").trim()
        return if (msg.isBlank()) name else "$name: ${msg.take(80)}"
    }

    // ===== initial + manual refresh =====
    LaunchedEffect(refreshToken) {
        val cached = repo.loadCachedConfigOrNull()
        if (cached != null) {
            config = cached
            statusText = if (refreshToken == 0) "CONFIG: cache" else "CONFIG: cache (refreshing)"
        }

        try {
            val result = withContext(Dispatchers.IO) {
                repo.fetchRemoteConfigIfChanged(ConfigRepository.DEFAULT_CONFIG_URL)
            }
            if (result.updated && result.config != null) {
                config = result.config
                statusText = if (refreshToken == 0) "CONFIG: remote" else "CONFIG: remote (refreshed)"
            } else {
                statusText = if (cached != null) "CONFIG: cache (no change)" else "CONFIG: default (no change)"
            }
        } catch (t: Throwable) {
            statusText = if (cached != null) {
                "CONFIG: cache (remote failed: ${shortErr(t)})"
            } else {
                "CONFIG: default (remote failed: ${shortErr(t)})"
            }
        }
    }

    // ===== auto refresh polling =====
    LaunchedEffect(Unit) {
        val intervalMs = 30L * 60L * 1000L
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            runCatching {
                val result = withContext(Dispatchers.IO) {
                    repo.fetchRemoteConfigIfChanged(ConfigRepository.DEFAULT_CONFIG_URL)
                }
                if (result.updated && result.config != null) {
                    config = result.config
                    statusText = "CONFIG: remote (auto-updated)"
                }
            }.onFailure { t ->
                statusText = "CONFIG: remote failed (auto: ${shortErr(t)})"
            }
        }
    }

    // ===== Hero slideshow (8–12 detik, tanpa gesture) =====
    LaunchedEffect(config.hero.enabled, config.hero.autoSlide, config.hero.intervalMs, heroItems.size) {
        heroIndex = 0
        if (!config.hero.enabled || !config.hero.autoSlide || heroItems.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(config.hero.intervalMs.coerceIn(8_000L, 12_000L))
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }

    // ===== QR generate =====
    LaunchedEffect(config.wifiCard.ssid, config.wifiCard.password, config.wifiCard.encryption) {
        wifiQrBitmap = null
        if (config.wifiCard.ssid.isBlank()) return@LaunchedEffect

        wifiQrBitmap = withContext(Dispatchers.Default) {
            runCatching {
                val payload = buildWifiQrPayload(
                    ssid = config.wifiCard.ssid,
                    password = config.wifiCard.password,
                    encryption = config.wifiCard.encryption
                )
                generateQrBitmap(text = payload, sizePx = 720)
            }.getOrNull()
        }
    }

    // ===== dialogs =====
    if (infoDialogOpen) {
        AlertDialog(
            onDismissRequest = { infoDialogOpen = false },
            title = { Text(infoDialogTitle) },
            text = { Text(infoDialogMessage) },
            confirmButton = { TextButton(onClick = { infoDialogOpen = false }) { Text("OK") } }
        )
    }

    if (AdminGate.show && !adminMenuOpen) {
        AlertDialog(
            onDismissRequest = { pinText = ""; pinError = null; AdminGate.close() },
            title = { Text("Admin PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Masukkan PIN untuk membuka menu admin.", color = Color(0xFFB7C7DD))
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.take(10); pinError = null },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    pinError?.let { Text(it, color = Color(0xFFFF6B6B)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinText == ADMIN_PIN) {
                        pinText = ""; pinError = null
                        adminMenuOpen = true
                    } else pinError = "PIN salah"
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pinText = ""; pinError = null; AdminGate.close() }) { Text("Batal") }
            }
        )
    }

    if (adminMenuOpen) {
        AlertDialog(
            onDismissRequest = { adminMenuOpen = false; AdminGate.close() },
            title = { Text("Admin Menu") },
            text = { Text("Pilih tindakan admin.", color = Color(0xFFB7C7DD)) },
            confirmButton = {
                TextButton(onClick = {
                    refreshToken++
                    adminMenuOpen = false
                    AdminGate.close()
                }) { Text("Refresh Config") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        adminMenuOpen = false; AdminGate.close()
                        val act = context as? Activity
                        runCatching { act?.stopLockTask() }
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { showInfo("Gagal", "Tidak bisa membuka Settings.") }
                    }) { Text("Settings") }

                    TextButton(onClick = {
                        adminMenuOpen = false; AdminGate.close()
                        val act = context as? Activity
                        runCatching { act?.stopLockTask() }
                        runCatching { act?.finishAffinity() }
                    }) { Text("Exit") }
                }
            }
        )
    }

    // ===== UI constants & glass style =====
    val outerPad = 28.dp
    val gap = 18.dp
    val cardRadius = 18.dp

    // “glass” lebih mirip konsep
    val glassBg = Color(0x5C0B0F16)
    val glassBorder = Color(0x44FFFFFF)

    fun glass(mod: Modifier): Modifier =
        mod.clip(RoundedCornerShape(cardRadius))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(cardRadius))

    // bottom marquee reserve
    val marqueeHeight = 34.dp
    val marqueeBottomPadding = 14.dp
    val contentBottomPadding = marqueeHeight + marqueeBottomPadding + 10.dp

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {

            // Background + vignette
            if (!config.background.url.isNullOrBlank()) {
                AsyncImage(
                    model = config.background.url,
                    contentDescription = "Wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xAA000000), Color(0x33000000), Color(0xAA000000))
                        )
                    )
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0F16)))
            }

            // ===== MAIN CONTENT =====
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = outerPad, end = outerPad, top = outerPad, bottom = contentBottomPadding)
            ) {

                // =========================
                // HEADER: logo.png + "De AZKA Guest House"
                // =========================
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = glass(Modifier)
                            .height(66.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        // pakai logo.png dari drawable
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x22000000))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Text(
                            text = "De AZKA Guest House",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            timeText,
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        if (dateText.isNotBlank()) {
                            Text(
                                dateText,
                                color = Color(0xFFE0E6EF),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(gap))

                // =========================================
                // SLIDESHOW (16:9, hanya gambar) + WIFI (16:9)
                // =========================================
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Top
                ) {
                    // SLIDESHOW 16:9 — lebih tinggi, tidak melebar
                    Box(
                        modifier = glass(
                            Modifier
                                .weight(1.15f)
                                .aspectRatio(16f / 9f)
                        ).padding(14.dp)
                    ) {
                        val current =
                            if (heroItems.isNotEmpty()) heroItems[heroIndex.coerceIn(0, heroItems.lastIndex)] else null

                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x22000000))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        ) {
                            if (current?.imageUrl != null) {
                                AsyncImage(
                                    model = current.imageUrl,
                                    contentDescription = "Slideshow",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // dots kecil (optional, tetap halus)
                        if (heroItems.isNotEmpty()) {
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                heroItems.take(7).forEachIndexed { idx, _ ->
                                    val active = idx == heroIndex
                                    Box(
                                        Modifier
                                            .size(if (active) 10.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(if (active) Color.White else Color(0x66FFFFFF))
                                    )
                                }
                            }
                        }
                    }

                    // WIFI 16:9 — tinggi sama, lebar menyesuaikan
                    Box(
                        modifier = glass(
                            Modifier
                                .weight(0.85f)
                                .aspectRatio(16f / 9f)
                        ).padding(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Wi-Fi Info",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "SSID: ${config.wifiCard.ssid}",
                                    color = Color(0xFFE0E6EF),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Password: ${config.wifiCard.password}",
                                    color = Color(0xFFE0E6EF),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "Scan untuk terhubung",
                                    color = Color(0xFFE0E6EF),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val bmp = wifiQrBitmap
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(2.dp, Color(0x22000000), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "WiFi QR",
                                        modifier = Modifier.fillMaxSize().padding(10.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(gap))

                // =========================================
                // APP ROW (fix 5) — icon asli dari app terpasang
                // =========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fixedApps.forEachIndexed { index, app ->
                        AppCardItem(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(appFocusRequesters[index]),
                            label = app.label,
                            packageName = app.packageName,
                            onClick = {
                                val ok = launchAppByPackage(context, app.packageName)
                                if (!ok) showInfo("Aplikasi tidak tersedia", "\"${app.label}\" belum terpasang.")
                            }
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    appFocusRequesters.firstOrNull()?.requestFocus()
                }

                Spacer(Modifier.height(gap))

                // =========================================
                // WHATSAPP BOX — lebih “soft” seperti konsep
                // =========================================
                Box(
                    modifier = glass(
                        Modifier
                            .fillMaxWidth()
                            .height(108.dp)
                    ).padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "WA",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "WhatsApp Front Office",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = FIXED_WA_NUMBER,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    statusText,
                    color = Color(0x88FFFFFF),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // =========================================
            // RUNNING TEXT — marquee (kanan -> kiri, loop)
            // =========================================
            Text(
                text = config.runningText.text,
                color = Color(0xFFFFD34D),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = marqueeBottomPadding)
                    .padding(start = outerPad, end = outerPad)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        // kecepatan default ok; bisa kita tuning nanti kalau kamu minta
                    )
            )
        }
    }
}

private data class FixedApp(val label: String, val packageName: String)

@Composable
private fun AppCardItem(
    modifier: Modifier,
    label: String,
    packageName: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "appCardScale")

    val cardBg = if (focused) Color(0xB0141B28) else Color(0x80111827)
    val border = if (focused) Color(0xFFFFFFFF) else Color(0x55FFFFFF)
    val borderW = if (focused) 3.dp else 2.dp

    val iconBitmap by rememberAppIconBitmap(context, packageName)

    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(borderW, border, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(2.2f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            // fallback kalau app belum terpasang
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberAppIconBitmap(context: Context, packageName: String): State<ImageBitmap?> {
    val state = remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        state.value = withContext(Dispatchers.Default) {
            runCatching {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                drawableToBitmap(drawable, 512, 512).asImageBitmap()
            }.getOrNull()
        }
    }
    return state
}

private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
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
            kotlinx.coroutines.delay((nextMinute - now).coerceAtLeast(250L))
        }
    }
    return state
}

@Composable
private fun rememberDateText(enabled: Boolean, localeTag: String): State<String> {
    val state = remember { mutableStateOf("") }
    val locale = remember(localeTag) {
        runCatching { Locale.forLanguageTag(localeTag) }.getOrElse { Locale("id", "ID") }
    }
    val formatter = remember(locale) { SimpleDateFormat("EEEE, d MMMM", locale) }

    LaunchedEffect(enabled, formatter) {
        if (!enabled) {
            state.value = ""
            return@LaunchedEffect
        }
        while (true) {
            state.value = formatter.format(Date()).replaceFirstChar { it.titlecase(locale) }
            val now = System.currentTimeMillis()
            val nextMinute = ((now / 60000L) + 1) * 60000L
            kotlinx.coroutines.delay((nextMinute - now).coerceAtLeast(250L))
        }
    }
    return state
}

private fun launchAppByPackage(context: Context, packageName: String): Boolean {
    return try {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}

private fun buildWifiQrPayload(ssid: String, password: String, encryption: String): String {
    val t = when (encryption.trim().uppercase(Locale.US)) {
        "WEP" -> "WEP"
        "NOPASS", "OPEN", "NONE" -> "nopass"
        else -> "WPA"
    }
    val s = escapeWifiField(ssid)
    val p = escapeWifiField(password)
    return if (t == "nopass") "WIFI:T:nopass;S:$s;;" else "WIFI:T:$t;S:$s;P:$p;;"
}

private fun escapeWifiField(value: String): String =
    value.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")

private fun generateQrBitmap(text: String, sizePx: Int): Bitmap {
    val writer = QRCodeWriter()
    val hints: EnumMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
    hints[EncodeHintType.MARGIN] = 1
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) black else white)
        }
    }
    return bmp
}

/**
 * Back handling / kiosk behavior tetap ada di Activity (bukan di sini).
 * Di HomeScreen, kita fokus UI saja.
 */
object AdminGate {
    var show by mutableStateOf(false)
    fun open() { show = true }
    fun close() { show = false }
}
