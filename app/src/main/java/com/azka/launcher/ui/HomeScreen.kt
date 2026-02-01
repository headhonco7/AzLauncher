package com.azka.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { ConfigRepository(context) }

    var config by remember { mutableStateOf(RemoteConfig()) }
    var statusText by remember { mutableStateOf("CONFIG: default") }
    var refreshToken by remember { mutableIntStateOf(0) }

    val timeText by rememberClockText(format24h = config.topRight.clock.format24h)

    val heroItems = config.hero.items.filter { !it.imageUrl.isNullOrBlank() }
    var heroIndex by remember { mutableIntStateOf(0) }

    var wifiQrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ===== Focus: Apps row =====
    val appFocusRequesters = remember { List(5) { FocusRequester() } }
    var appsRowRendered by remember { mutableStateOf(false) }

    // ===== Info dialog =====
    var infoDialogOpen by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf("Info") }
    var infoDialogMessage by remember { mutableStateOf("") }
    fun showInfo(title: String, msg: String) {
        infoDialogTitle = title
        infoDialogMessage = msg
        infoDialogOpen = true
    }

    // ===== Admin =====
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var adminMenuOpen by remember { mutableStateOf(false) }
    val ADMIN_PIN = "1234"

    // ===== initial + manual refresh =====
    LaunchedEffect(refreshToken) {
        val cached = repo.loadCachedConfigOrNull()
        if (cached != null) {
            config = cached
            statusText = if (refreshToken == 0) "CONFIG: cache" else "CONFIG: cache (refreshing)"
        }

        val url = ConfigRepository.DEFAULT_CONFIG_URL
        if (url.contains("REPLACE_ME") || url.startsWith("https://example.com")) {
            statusText = "CONFIG: url not set"
            return@LaunchedEffect
        }

        try {
            val result = withContext(Dispatchers.IO) { repo.fetchRemoteConfigIfChanged(url) }
            if (result.updated && result.config != null) {
                config = result.config
                statusText = if (refreshToken == 0) "CONFIG: remote" else "CONFIG: remote (refreshed)"
            } else {
                statusText = if (cached != null) "CONFIG: cache (no change)" else "CONFIG: default (no change)"
            }
        } catch (_: Throwable) {
            statusText = if (cached != null) "CONFIG: cache (remote failed)" else "CONFIG: default (remote failed)"
        }
    }

    // ===== auto refresh polling =====
    LaunchedEffect(Unit) {
        val url = ConfigRepository.DEFAULT_CONFIG_URL
        if (url.contains("REPLACE_ME") || url.startsWith("https://example.com")) return@LaunchedEffect
        val intervalMs = 30L * 60L * 1000L
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            runCatching {
                val result = withContext(Dispatchers.IO) { repo.fetchRemoteConfigIfChanged(url) }
                if (result.updated && result.config != null) {
                    config = result.config
                    statusText = "CONFIG: remote (auto-updated)"
                }
            }
        }
    }

    // ===== Hero slideshow =====
    LaunchedEffect(config.hero.enabled, config.hero.autoSlide, config.hero.intervalMs, heroItems.size) {
        heroIndex = 0
        if (!config.hero.enabled || !config.hero.autoSlide || heroItems.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(config.hero.intervalMs.coerceAtLeast(2000L))
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }

    // ===== QR =====
    LaunchedEffect(
        config.wifiCard.enabled,
        config.wifiCard.showQr,
        config.wifiCard.ssid,
        config.wifiCard.password,
        config.wifiCard.encryption
    ) {
        wifiQrBitmap = null
        if (!config.wifiCard.enabled || !config.wifiCard.showQr) return@LaunchedEffect
        if (config.wifiCard.ssid.isBlank()) return@LaunchedEffect
        wifiQrBitmap = withContext(Dispatchers.Default) {
            runCatching {
                val payload = buildWifiQrPayload(
                    ssid = config.wifiCard.ssid,
                    password = config.wifiCard.password,
                    encryption = config.wifiCard.encryption
                )
                generateQrBitmap(text = payload, sizePx = 520)
            }.getOrNull()
        }
    }

    val roomName = remember { getDeviceNameForRoom(context) }
    val roomLabel = "${config.roomLabel.prefix.ifBlank { "ROOM" }} ${roomName.ifBlank { "UNKNOWN" }}"
    val whatsappLine = "${config.contact.whatsappFoText}: ${config.contact.whatsappNumber}"
    val socialLine = "${config.contact.socialText}: ${config.contact.socialHandle}"

    // ===== Info dialog =====
    if (infoDialogOpen) {
        AlertDialog(
            onDismissRequest = { infoDialogOpen = false },
            title = { Text(infoDialogTitle) },
            text = { Text(infoDialogMessage) },
            confirmButton = { TextButton(onClick = { infoDialogOpen = false }) { Text("OK") } }
        )
    }

    // ===== Admin PIN =====
    if (AdminGate.show && !adminMenuOpen) {
        AlertDialog(
            onDismissRequest = {
                pinText = ""
                pinError = null
                AdminGate.close()
            },
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
                        pinText = ""; pinError = null; adminMenuOpen = true
                    } else pinError = "PIN salah"
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinText = ""; pinError = null; AdminGate.close()
                }) { Text("Batal") }
            }
        )
    }

    // ===== Admin Menu =====
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
                                Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { showInfo("Gagal", "Tidak bisa membuka Settings.") }
                    }) { Text("Open Settings") }

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

    // ===== UI =====
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {

            // Background
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
                            listOf(Color(0xAA0B0F16), Color(0xD0101318))
                        )
                    )
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF101318)))
            }

            Column(Modifier.fillMaxSize().padding(24.dp)) {

                // ===== Top bar =====
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(200.dp, 72.dp)
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
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                config.branding.appTitle.ifBlank { "AzLauncher" },
                                color = Color(0xFFE6EEF9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (config.topRight.clock.enabled) {
                        Box(
                            Modifier.height(40.dp).width(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x551F2630))
                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(timeText, color = Color(0xFFB7C7DD))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ===== Middle =====
                Row(Modifier.fillMaxWidth().weight(1f)) {

                    // Hero (no focus)
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x441B2230))
                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
                        ) {
                            if (config.hero.enabled && heroItems.isNotEmpty()) {
                                AsyncImage(
                                    model = heroItems[heroIndex].imageUrl,
                                    contentDescription = "Hero",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (config.roomLabel.enabled) {
                            Box(
                                Modifier.fillMaxWidth().height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x55151B25))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(roomLabel, color = Color(0xFFE6EEF9))
                            }
                        }
                    }

                    Spacer(Modifier.width(18.dp))

                    // Right column
                    Column(Modifier.widthIn(min = 320.dp, max = 420.dp)) {
                        if (config.contact.enabled) {
                            Box(
                                Modifier.fillMaxWidth().height(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x551F2630))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(whatsappLine, color = Color(0xFFD7E3F4))
                                    Text(socialLine, color = Color(0xFFD7E3F4))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        if (config.wifiCard.enabled) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x441B2230))
                                    .border(1.dp, Color(0x552A3442), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(config.wifiCard.title, color = Color(0xFFD7E3F4))
                                    Spacer(Modifier.height(6.dp))
                                    Text("SSID: ${config.wifiCard.ssid}", color = Color(0xFFB7C7DD))
                                    Text("PASS: ${config.wifiCard.password}", color = Color(0xFFB7C7DD))
                                    Spacer(Modifier.weight(1f))
                                    if (config.wifiCard.showQr && wifiQrBitmap != null) {
                                        Image(
                                            bitmap = wifiQrBitmap!!.asImageBitmap(),
                                            contentDescription = "WiFi QR",
                                            modifier = Modifier.size(170.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ===== Apps Row (FOCUS HERE) =====
                if (config.appsRow.enabled) {
                    val items = config.appsRow.items.take(5)
                    Row(
                        Modifier.fillMaxWidth().height(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            AppTile(
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(appFocusRequesters[index]),
                                label = item.label,
                                iconUrl = item.iconUrl,
                                onClick = {
                                    val pkg = item.packageName
                                    when {
                                        pkg.isBlank() -> showInfo("Aplikasi", "Package kosong.")
                                        pkg.startsWith("__") -> showInfo("Aplikasi belum siap", "\"${item.label}\" belum dikonfigurasi.")
                                        else -> {
                                            val ok = launchAppByPackage(context, pkg)
                                            if (!ok) showInfo("Aplikasi tidak tersedia", "\"${item.label}\" belum terpasang.")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Default focus → Netflix (index 0)
                    LaunchedEffect(Unit) {
                        appsRowRendered = true
                        appFocusRequesters.firstOrNull()?.requestFocus()
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (config.runningText.enabled) {
                    Box(
                        Modifier.fillMaxWidth().height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x550E131B))
                            .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(config.runningText.text, color = Color(0xFFB7C7DD))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(statusText, color = Color(0xFF5E6B7D))
            }
        }
    }
}

@Composable
private fun AppTile(
    modifier: Modifier,
    label: String,
    iconUrl: String?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tileScale")
    val borderColor = if (focused) Color(0xFF8AB4F8) else Color(0x552A3442)
    val bgColor = if (focused) Color(0x771F2630) else Color(0x551F2630)

    Box(
        modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (!iconUrl.isNullOrBlank()) {
                Box(
                    Modifier.size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x332A3442), RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = "$label icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = Color(0xFFD7E3F4), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ===== Helpers (unchanged) =====
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

private fun launchAppByPackage(context: Context, packageName: String): Boolean = try {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
        true
    } ?: false
} catch (_: Throwable) { false }

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
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

private fun generateQrBitmap(text: String, sizePx: Int): Bitmap {
    val writer = QRCodeWriter()
    val hints: EnumMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
    hints[EncodeHintType.MARGIN] = 1
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    for (y in 0 until bitMatrix.height) for (x in 0 until bitMatrix.width)
        bmp.setPixel(x, y, if (bitMatrix[x, y]) black else white)
    return bmp
}
