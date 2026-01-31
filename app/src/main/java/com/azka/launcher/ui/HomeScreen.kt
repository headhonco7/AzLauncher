package com.azka.launcher.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Tahap 1: layout blok (placeholder) sesuai mock.
 * Tahap berikutnya baru diisi data dari remote config + gambar + QR + slideshow.
 */
@Composable
fun HomeScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // wallpaper placeholder
                .background(Color(0xFF101318))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ===== Top bar: LOGO kiri, WEATHER + JAM kanan =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // LOGO placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 160.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F2630))
                            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOGO",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFD7E3F4)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // WEATHER placeholder
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .widthIn(min = 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F2630))
                            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WEATHER",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB7C7DD)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // JAM placeholder
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1F2630))
                            .border(1.dp, Color(0xFF2A3442), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JAM",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB7C7DD)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ===== Mid area =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // LEFT: SLIDE SHOW
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
                                text = "SLIDE SHOW",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFD7E3F4)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ROOM LABEL (ambil dari device name nanti)
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
                                text = "ROOM <device-name>",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE6EEF9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                    text = "WHATSAPP FO: +62xxxxxxxxxxx",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFD7E3F4)
                                )
                                Text(
                                    text = "AKUN SOSMED: @guesthouse",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFD7E3F4)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // WIFI INFO CARD + QR
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
                                    text = "WIFI INFO CARD + QR CODE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFD7E3F4)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "SSID: AZ-GUEST",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB7C7DD)
                                )
                                Text(
                                    text = "PASS: az12345",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB7C7DD)
                                )
                                Spacer(modifier = Modifier.weight(1f))
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

                Spacer(modifier = Modifier.height(16.dp))

                // ===== Apps Row (zona fokus DPAD) =====
                val apps = remember {
                    listOf(
                        "YouTube",
                        "Netflix",
                        "Live TV 1",
                        "Live TV 2"
                    )
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(apps) { label ->
                        AppTile(label = label)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ===== Running text =====
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
                        text = "RUNNING TEXT: Selamat datang • Check-out pukul 12.00 • Hubungi FO via WhatsApp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB7C7DD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
