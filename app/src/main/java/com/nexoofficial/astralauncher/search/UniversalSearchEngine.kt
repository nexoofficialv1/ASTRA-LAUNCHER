package com.nexoofficial.astralauncher.search

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import com.nexoofficial.astralauncher.model.LauncherApp
import java.util.Locale

enum class SearchKind { APP, SETTING, ACTION, WEATHER, WEB, AI }

sealed interface SearchAction {
    data class LaunchApp(val componentName: ComponentName) : SearchAction
    data class LaunchIntent(val intent: Intent) : SearchAction
    data object OpenWeather : SearchAction
    data class WebSearch(val query: String) : SearchAction
    data class AiPlaceholder(val query: String) : SearchAction
}

data class UniversalSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: SearchKind,
    val action: SearchAction
)

class UniversalSearchEngine {

    fun search(query: String, apps: List<LauncherApp>): List<UniversalSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return suggestions(apps)

        val normalized = normalize(trimmed)
        val launchTarget = stripLaunchCommand(normalized)
        val results = mutableListOf<UniversalSearchResult>()

        val appMatches = apps.asSequence()
            .map { app -> app to appScore(app, launchTarget) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(6)
            .map { (app, _) ->
                UniversalSearchResult(
                    id = "app:${app.componentName.flattenToString()}",
                    title = app.label,
                    subtitle = "Open app",
                    kind = SearchKind.APP,
                    action = SearchAction.LaunchApp(app.componentName)
                )
            }
            .toList()
        results += appMatches

        settingsCatalog().filter { item ->
            item.keywords.any { normalized.contains(it) || it.contains(normalized) }
        }.take(4).forEach { results += it.result }

        actionCatalog().filter { item ->
            item.keywords.any { normalized.contains(it) || it.contains(normalized) }
        }.take(4).forEach { results += it.result }

        if (weatherKeywords.any { normalized.contains(it) }) {
            results += UniversalSearchResult(
                id = "weather",
                title = "ASTRA Weather",
                subtitle = "Current conditions and 5-day forecast",
                kind = SearchKind.WEATHER,
                action = SearchAction.OpenWeather
            )
        }

        val deduped = results.distinctBy { it.id }.take(10).toMutableList()
        deduped += UniversalSearchResult(
            id = "web:$normalized",
            title = "Search the web",
            subtitle = trimmed,
            kind = SearchKind.WEB,
            action = SearchAction.WebSearch(trimmed)
        )
        deduped += UniversalSearchResult(
            id = "ai:$normalized",
            title = "Ask ASTRA AI",
            subtitle = "Cloud AI connector is reserved for v0.3",
            kind = SearchKind.AI,
            action = SearchAction.AiPlaceholder(trimmed)
        )
        return deduped
    }

    private fun suggestions(apps: List<LauncherApp>): List<UniversalSearchResult> {
        val defaults = actionCatalog().take(4).map { it.result }.toMutableList()
        defaults += UniversalSearchResult(
            id = "weather",
            title = "ASTRA Weather",
            subtitle = "Current location",
            kind = SearchKind.WEATHER,
            action = SearchAction.OpenWeather
        )
        apps.take(4).forEach { app ->
            defaults += UniversalSearchResult(
                id = "app:${app.componentName.flattenToString()}",
                title = app.label,
                subtitle = "Installed app",
                kind = SearchKind.APP,
                action = SearchAction.LaunchApp(app.componentName)
            )
        }
        return defaults
    }

    private fun appScore(app: LauncherApp, normalizedQuery: String): Int {
        if (normalizedQuery.isBlank()) return 0
        val label = normalize(app.label)
        val pkg = normalize(app.packageName)
        return when {
            label == normalizedQuery -> 100
            label.startsWith(normalizedQuery) -> 80
            label.contains(normalizedQuery) -> 60
            pkg.contains(normalizedQuery) -> 30
            else -> 0
        }
    }

    private fun settingsCatalog(): List<CatalogItem> = listOf(
        CatalogItem(
            keywords = setOf("wifi", "wi-fi", "ওয়াইফাই", "ওয়াইফাই"),
            result = settingResult("wifi", "Wi-Fi settings", Settings.ACTION_WIFI_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("bluetooth", "ব্লুটুথ"),
            result = settingResult("bluetooth", "Bluetooth settings", Settings.ACTION_BLUETOOTH_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("display", "screen", "ডিসপ্লে", "স্ক্রিন"),
            result = settingResult("display", "Display settings", Settings.ACTION_DISPLAY_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("sound", "volume", "সাউন্ড", "ভলিউম"),
            result = settingResult("sound", "Sound settings", Settings.ACTION_SOUND_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("location", "gps", "লোকেশন", "জিপিএস"),
            result = settingResult("location", "Location settings", Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("battery", "ব্যাটারি"),
            result = settingResult("battery", "Battery saver settings", Settings.ACTION_BATTERY_SAVER_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("home", "launcher", "হোম", "লঞ্চার"),
            result = settingResult("home", "Default Home app", Settings.ACTION_HOME_SETTINGS)
        ),
        CatalogItem(
            keywords = setOf("settings", "setting", "সেটিংস", "সেটিং"),
            result = settingResult("settings", "Android settings", Settings.ACTION_SETTINGS)
        )
    )

    private fun actionCatalog(): List<CatalogItem> = listOf(
        CatalogItem(
            keywords = setOf("camera", "ক্যামেরা", "photo", "ছবি"),
            result = UniversalSearchResult(
                id = "action:camera",
                title = "Camera",
                subtitle = "Open camera",
                kind = SearchKind.ACTION,
                action = SearchAction.LaunchIntent(Intent("android.media.action.STILL_IMAGE_CAMERA"))
            )
        ),
        CatalogItem(
            keywords = setOf("phone", "dial", "call", "ফোন", "কল"),
            result = UniversalSearchResult(
                id = "action:phone",
                title = "Phone",
                subtitle = "Open dialer",
                kind = SearchKind.ACTION,
                action = SearchAction.LaunchIntent(Intent(Intent.ACTION_DIAL))
            )
        ),
        CatalogItem(
            keywords = setOf("message", "messages", "sms", "মেসেজ", "এসএমএস"),
            result = UniversalSearchResult(
                id = "action:messages",
                title = "Messages",
                subtitle = "Open messaging app",
                kind = SearchKind.ACTION,
                action = SearchAction.LaunchIntent(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                })
            )
        ),
        CatalogItem(
            keywords = setOf("browser", "web", "ব্রাউজার", "ইন্টারনেট"),
            result = UniversalSearchResult(
                id = "action:browser",
                title = "Browser",
                subtitle = "Open web browser",
                kind = SearchKind.ACTION,
                action = SearchAction.LaunchIntent(Intent(Intent.ACTION_VIEW))
            )
        )
    )

    private fun settingResult(id: String, title: String, action: String) = UniversalSearchResult(
        id = "setting:$id",
        title = title,
        subtitle = "Android system setting",
        kind = SearchKind.SETTING,
        action = SearchAction.LaunchIntent(Intent(action))
    )

    private fun stripLaunchCommand(normalized: String): String {
        val prefixes = listOf(
            "open ", "launch ", "start ",
            "খোলো ", "খুলে দাও ", "চালু করো ", "চালাও "
        )
        return prefixes.firstOrNull { normalized.startsWith(it) }
            ?.let { normalized.removePrefix(it).trim() }
            ?: normalized
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()

    private data class CatalogItem(
        val keywords: Set<String>,
        val result: UniversalSearchResult
    )

    private val weatherKeywords = setOf(
        "weather", "temperature", "rain", "forecast",
        "আবহাওয়া", "আবহাওয়া", "তাপমাত্রা", "বৃষ্টি"
    )
}
