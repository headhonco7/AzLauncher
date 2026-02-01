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

/**
 * Tahap 5.1:
 * - Admin PIN dialog (dipicu dari MainActivity: tekan MENU 5x cepat)
 * - Setelah PIN benar: menu admin (Open Settings / Refresh Config / Exit)
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { ConfigRepository(context) }

    var config by remember { mutableStateOf(RemoteConfig()) }
    var statusText by remember { mutableStateOf("CONFIG: default") }

    // refresh token agar bisa "Refresh Config" manual
    var refreshToken by remember { mutableIntStateOf(0) }

    val timeText by rememberClockText(format24h = config.topRight.clock.format24h)

    // Hero slideshow state
    val heroItems = config.hero.items.filter { !it.imageUrl.isNullOrBlank() }
    var heroIndex by remember { mutableIntStateOf(0) }

    // QR bitmap state
    var wifiQrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dialog info state (untuk tile klik)
    var infoDialogOpen by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf("Info") }
    var infoDialogMessage by remember { mutableStateOf("") }

    fun showInfo(title: String, msg: String) {
        infoDialogTitle = title
        infoDialogMessage = msg
        infoDialogOpen = true
    }

    // Admin gate states
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var adminMenuOpen by remember { mutableStateOf(false) }

    // NOTE: sementara hardcode. Nanti bisa dipindah ke remote config.
    val ADMIN_PIN = "1234"

    // ===== LOAD CACHE + FETCH REMOTE (setiap refreshToken berubah) =====
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
            val (remoteCfg, raw) = withContext(Dispatchers.IO) {
                repo.fetchRemoteConfig(url)
            }
            config = remoteCfg
            repo.saveCache(raw)
            statusText = if (refreshToken == 0) "CONFIG: remote" else "CONFIG: remote (refreshed)"
        } catch (_: Throwable) {
            statusText = if (cached != null) "CONFIG: cache (remote failed)" else "CONFIG: default (remote failed)"
        }
    }

    // Slideshow hero (auto)
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

    // Generate QR setiap wifi config berubah
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
    val roomLabel = buildString {
        append(config.roomLabel.prefix.ifBlank { "ROOM" })
        append(" ")
        append(roomName.ifBlank { "UNKNOWN" })
    }

    val whatsappLine = "${config.contact.whatsappFoText}: ${config.contact.whatsappNumber}"
    val socialLine = "${config.contact.socialText}: ${config.contact.socialHandle}"

    // ===== Info dialog (untuk tile) =====
    if (infoDialogOpen) {
        AlertDialog(
            onDismissRequest = { infoDialogOpen = false },
            title = { Text(infoDialogTitle) },
            text = { Text(infoDialogMessage) },
            confirmButton = {
                TextButton(onClick = { infoDialogOpen = false }) { Text("OK") }
            }
        )
    }

    // ===== Admin PIN dialog (dipicu dari Activity) =====
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
                    Text(
                        text = "Masukkan PIN untuk membuka menu admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB7C7DD)
                    )
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = {
                            pinText = it.take(10)
                            pinError = null
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinText == ADMIN_PIN) {
                        pinText = ""
                        pinError = null
                        adminMenuOpen = true
                    } else {
                        pinError = "PIN salah"
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinText = ""
                    pinError = null
                    AdminGate.close()
                }) { Text("Batal") }
            }
        )
    }

    // ===== Admin menu dialog =====
    if (adminMenuOpen) {
        AlertDialog(
            onDismissRequest = {
                adminMenuOpen = false
                AdminGate.close()
            },
            title = { Text("Admin Menu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Pilih tindakan admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB7C7DD)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Refresh config (fetch remote lagi)
                    refreshToken++
                    adminMenuOpen = false
                    AdminGate.close()
                }) { Text("Refresh Config") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // Open Android Settings (best-effort)
                        adminMenuOpen = false
                        AdminGate.close()

                        val act = context as? Activity
                        runCatching { act?.stopLockTask() }

                        runCatching {
                            val i = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(i)
                        }.onFailure {
                            showInfo("Gagal", "Tidak bisa membuka Settings di device ini.")
                        }
                    }) { Text("Open Settings") }

                    TextButton(onClick = {
                        // Exit launcher (admin)
                        adminMenuOpen = false
                        AdminGate.close()

                        val act = context as? Activity
                        runCatching { act?.stopLockTask() }
                        runCatching { act?.finishAffinity() }
                    }) { Text("Exit") }
                }
            }
        )
    }

    // ===== UI =====
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Wallpaper
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {

                // Top bar
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
                                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
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

                // Mid area
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
                                        Box(
                                            modifier = Modifier
                                                .size(170.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0F141D))
                                                .border(1.dp, Color(0x552A3442), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val bmp = wifiQrBitmap
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

                // Apps Row (5 tile full width)
                if (config.appsRow.enabled) {
                    val items = config.appsRow.items.take(5)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { item ->
                            AppTile(
                                modifier = Modifier.weight(1f),
                                label = item.label,
                                iconUrl = item.iconUrl,
                                onClick = {
                                    val pkg = item.packageName
                                    when {
                                        pkg.isBlank() -> showInfo("Aplikasi", "Package kosong.")
                                        pkg.startsWith("__") -> showInfo("Aplikasi belum siap", "Aplikasi \"${item.label}\" belum dikonfigurasi.")
                                        else -> {
                                            val ok = launchAppByPackage(context, pkg)
                                            if (!ok) showInfo("Aplikasi tidak tersedia", "\"${item.label}\" belum terpasang.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Running text
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
private fun AppTile(
    modifier: Modifier,
    label: String,
    iconUrl: String?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1.0f, label = "tileScale")

    val borderColor = if (focused) Color(0xFF8AB4F8) else Color(0x552A3442)
    val bgColor = if (focused) Color(0x771F2630) else Color(0x551F2630)

    Box(
        modifier = modifier
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

    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) black else white)
        }
    }
    return bmp
}
