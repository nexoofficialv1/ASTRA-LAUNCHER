package com.nexoofficial.astralauncher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WallpaperSettingsSheet(
    dimPercent: Int,
    accentColor: Color,
    onDimChange: (Int) -> Unit,
    onWallpaperChanged: () -> Unit,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences("astra_launcher", Context.MODE_PRIVATE)
    }

    var target by remember {
        mutableStateOf(
            WallpaperTarget.fromStorage(
                preferences.getString("wallpaper_target", WallpaperTarget.HOME.name)
            )
        )
    }
    var busy by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            scope.launch {
                busy = true
                val applied = withContext(Dispatchers.IO) {
                    applyWallpaperFromUri(context, uri, target)
                }
                busy = false

                if (applied) {
                    onWallpaperChanged()
                    Toast.makeText(context, "Wallpaper applied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not apply wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onWallpaperChanged()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SheetHandle()
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ASTRA Wallpaper",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Gallery · Presets · Home / Lock · Dim",
                            color = accentColor.copy(alpha = 0.90f),
                            fontSize = 11.sp,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.075f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = Color.White, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                SectionLabel("APPLY TO")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperTarget.values().forEach { option ->
                        ChoiceChip(
                            label = option.label,
                            selected = target == option,
                            accentColor = accentColor,
                            modifier = Modifier.width(104.dp)
                        ) {
                            target = option
                            preferences.edit()
                                .putString("wallpaper_target", option.name)
                                .apply()
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("CHOOSE WALLPAPER")
                Spacer(Modifier.height(8.dp))

                ActionCard(
                    title = "Choose from Gallery",
                    subtitle = "Pick any photo from your phone",
                    accentColor = accentColor,
                    onClick = {
                        galleryLauncher.launch(arrayOf("image/*"))
                    }
                )

                Spacer(Modifier.height(8.dp))

                ActionCard(
                    title = "Android Wallpaper Picker",
                    subtitle = "Open your phone's wallpaper collection",
                    accentColor = accentColor,
                    onClick = {
                        runCatching {
                            systemPickerLauncher.launch(Intent(Intent.ACTION_SET_WALLPAPER))
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Wallpaper picker is not available on this phone.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(5.dp))
                SectionLabel("ASTRA PRESETS")
                Spacer(Modifier.height(8.dp))
            }

            items(AstraWallpaperPreset.values().toList(), key = { it.name }) { preset ->
                PresetCard(
                    preset = preset,
                    accentColor = accentColor,
                    busy = busy
                ) {
                    if (!busy) {
                        scope.launch {
                            busy = true
                            val applied = withContext(Dispatchers.IO) {
                                applyPresetWallpaper(context, preset, target)
                            }
                            busy = false

                            if (applied) {
                                onWallpaperChanged()
                                Toast.makeText(
                                    context,
                                    "${preset.label} applied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not apply preset",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(5.dp))
                SectionLabel("HOME WALLPAPER DIM")
                Spacer(Modifier.height(5.dp))
                Text(
                    "Dim keeps text, weather and icons readable over bright wallpapers.",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Off",
                        18 to "Light",
                        34 to "Balanced",
                        50 to "Dark"
                    ).forEach { (value, label) ->
                        ChoiceChip(
                            label = label,
                            selected = dimPercent == value,
                            accentColor = accentColor,
                            modifier = Modifier.width(105.dp)
                        ) {
                            onDimChange(value)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (busy) "Applying wallpaper…" else
                        "Tip: ASTRA automatically updates its accent color from your Home wallpaper when Adaptive Accent is enabled.",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

private enum class WallpaperTarget(
    val label: String,
    val flags: Int
) {
    HOME(
        "Home",
        WallpaperManager.FLAG_SYSTEM
    ),
    LOCK(
        "Lock",
        WallpaperManager.FLAG_LOCK
    ),
    BOTH(
        "Both",
        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    );

    companion object {
        fun fromStorage(value: String?): WallpaperTarget =
            values().firstOrNull { it.name == value } ?: HOME
    }
}

private enum class AstraWallpaperPreset(
    val label: String,
    val subtitle: String,
    val startColor: Int,
    val endColor: Int,
    val accent: Int
) {
    EMBER(
        label = "ASTRA Ember",
        subtitle = "Black · orange · cinematic",
        startColor = 0xFF08090C.toInt(),
        endColor = 0xFFFF5B00.toInt(),
        accent = 0xFFFFA000.toInt()
    ),
    AURORA(
        label = "ASTRA Aurora",
        subtitle = "Deep teal · electric blue",
        startColor = 0xFF07151A.toInt(),
        endColor = 0xFF006D77.toInt(),
        accent = 0xFF39D9C8.toInt()
    ),
    VIOLET(
        label = "ASTRA Violet",
        subtitle = "Midnight · violet · neon",
        startColor = 0xFF090713.toInt(),
        endColor = 0xFF5927E5.toInt(),
        accent = 0xFFB997FF.toInt()
    ),
    CARBON(
        label = "ASTRA Carbon",
        subtitle = "Minimal graphite · silver",
        startColor = 0xFF060708.toInt(),
        endColor = 0xFF34383D.toInt(),
        accent = 0xFFB9C0C8.toInt()
    )
}

@Composable
private fun PresetCard(
    preset: AstraWallpaperPreset,
    accentColor: Color,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(preset.startColor),
                            Color(preset.endColor)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Color(preset.accent).copy(alpha = 0.52f),
                        CircleShape
                    )
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                preset.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                preset.subtitle,
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 10.sp
            )
        }

        Text(
            "APPLY",
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp)
        }

        Text("→", color = Color.White.copy(alpha = 0.46f), fontSize = 18.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.34f),
        fontSize = 9.sp,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) accentColor
                else Color.White.copy(alpha = 0.055f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF171007) else Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
    )
}

private fun applyWallpaperFromUri(
    context: Context,
    uri: Uri,
    target: WallpaperTarget
): Boolean = runCatching {
    val manager = WallpaperManager.getInstance(context)
    context.contentResolver.openInputStream(uri)?.use { input ->
        manager.setStream(input, null, true, target.flags)
    } ?: error("Unable to open image")
}.isSuccess

private fun applyPresetWallpaper(
    context: Context,
    preset: AstraWallpaperPreset,
    target: WallpaperTarget
): Boolean = runCatching {
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(1080)
    val height = metrics.heightPixels.coerceAtLeast(1920)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        preset.startColor,
        preset.endColor,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    paint.shader = null
    paint.color = preset.accent
    paint.alpha = 52
    canvas.drawCircle(
        width * 0.92f,
        height * 0.16f,
        width * 0.52f,
        paint
    )

    paint.alpha = 32
    canvas.drawCircle(
        width * 0.12f,
        height * 0.82f,
        width * 0.46f,
        paint
    )

    paint.alpha = 255

    try {
        WallpaperManager.getInstance(context)
            .setBitmap(bitmap, null, true, target.flags)
    } finally {
        bitmap.recycle()
    }
}.isSuccess
