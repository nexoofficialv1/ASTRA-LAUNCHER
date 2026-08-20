package com.nexoofficial.astralauncher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexoofficial.astralauncher.model.WeatherSnapshot
import com.nexoofficial.astralauncher.weather.WeatherCodeMapper
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class AstraWidgetType(val label: String) {
    NONE("None"), CLOCK("ASTRA Clock"), WEATHER("ASTRA Weather"),
    BATTERY("ASTRA Battery"), THIRD_PARTY("Android Widget");

    companion object {
        fun fromStorage(value: String?): AstraWidgetType =
            values().firstOrNull { it.name == value } ?: NONE
    }
}

@Composable
fun WidgetSettingsSheet(
    currentType: AstraWidgetType,
    currentHeightDp: Int,
    accentColor: Color,
    onPickSystemWidget: () -> Unit,
    onSelectNative: (AstraWidgetType) -> Unit,
    onHeightChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF090A0D))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ASTRA Widgets", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("Native + installed Android widgets", color = accentColor.copy(alpha = 0.9f), fontSize = 11.sp)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("×", color = Color.White, fontSize = 22.sp) }
            }

            Spacer(Modifier.height(18.dp))
            Label("ASTRA WIDGETS")
            Spacer(Modifier.height(8.dp))

            listOf(AstraWidgetType.CLOCK, AstraWidgetType.WEATHER, AstraWidgetType.BATTERY).forEach { type ->
                ChoiceCard(type.label, currentType == type, accentColor) { onSelectNative(type) }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            Label("INSTALLED APP WIDGETS")
            Spacer(Modifier.height(8.dp))
            ActionCard("Add Android Widget", "Calendar · Music · Notes · Clock · More", accentColor, onPickSystemWidget)

            Spacer(Modifier.height(16.dp))
            Label("WIDGET SIZE")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(110 to "Compact", 150 to "Medium", 190 to "Large").forEach { (height, label) ->
                    SizeChip(label, currentHeightDp == height, accentColor) { onHeightChange(height) }
                }
            }

            Text(
                "Free drag and resize handles come next with the Workspace Engine.",
                color = Color.White.copy(alpha = 0.32f), fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (currentType != AstraWidgetType.NONE) {
                Spacer(Modifier.height(16.dp))
                ActionCard("Remove Home Widget", currentType.label, Color(0xFFFF8068), onRemove)
            }
        }
    }
}

@Composable
fun HomeWidgetSlot(
    type: AstraWidgetType,
    appWidgetId: Int,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    weather: WeatherSnapshot?,
    accentColor: Color,
    heightDp: Int,
    onManage: () -> Unit
) {
    if (type == AstraWidgetType.NONE) return

    Box(
        Modifier.fillMaxWidth().height(heightDp.coerceIn(100, 210).dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xB9121318))
    ) {
        when (type) {
            AstraWidgetType.CLOCK -> NativeClock(accentColor)
            AstraWidgetType.WEATHER -> NativeWeather(weather, accentColor)
            AstraWidgetType.BATTERY -> NativeBattery(accentColor)
            AstraWidgetType.THIRD_PARTY -> HostedAndroidWidget(appWidgetId, host, manager, accentColor)
            AstraWidgetType.NONE -> Unit
        }

        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).clickable(onClick = onManage),
            contentAlignment = Alignment.Center
        ) { Text("⋯", color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun NativeClock(accent: Color) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(30_000L) }
    }
    Row(
        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF101216), accent.copy(alpha = 0.18f)))).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text(now.format(DateTimeFormatter.ofPattern("EEE · dd MMM", Locale.getDefault())).uppercase(), color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
        }
        Text("ASTRA", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NativeWeather(weather: WeatherSnapshot?, accent: Color) {
    Row(
        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.24f), Color(0xFF111318)))).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(weather?.let { WeatherCodeMapper.glyph(it.current.weatherCode, it.current.isDay) } ?: "◌", color = accent, fontSize = 38.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(weather?.let { "${it.current.temperatureC.roundToInt()}°C" } ?: "Weather", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(weather?.locationName ?: "Open ASTRA Weather to update", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NativeBattery(accent: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var level by remember { mutableStateOf(readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) { level = readBattery(context); delay(30_000L) }
    }
    Row(
        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF111318), accent.copy(alpha = 0.18f)))).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(accent.copy(alpha = 0.17f)), contentAlignment = Alignment.Center) {
            Text("$level%", color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Battery", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(if (level < 25) "Low battery" else "Battery level is healthy", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun HostedAndroidWidget(id: Int, host: AppWidgetHost, manager: AppWidgetManager, accent: Color) {
    val info = remember(id) { if (id == AppWidgetManager.INVALID_APPWIDGET_ID) null else manager.getAppWidgetInfo(id) }
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Widget unavailable. Add it again from Widgets.", color = accent, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> host.createView(context, id, info).apply { setAppWidget(id, info) } }
    )
}

@Composable
private fun ChoiceCard(title: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text("W", color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(if (selected) "✓" else "+", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.055f))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text("+", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp)
        }
        Text("→", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}

@Composable
private fun SizeChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(108.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent else Color.White.copy(alpha = 0.055f))
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF171007) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp, letterSpacing = 1.4.sp)
}

private fun readBattery(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.coerceIn(0, 100) ?: 0
