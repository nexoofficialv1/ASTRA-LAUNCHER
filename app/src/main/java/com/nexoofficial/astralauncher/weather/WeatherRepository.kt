package com.nexoofficial.astralauncher.weather

import android.content.Context
import android.location.Geocoder
import com.nexoofficial.astralauncher.model.CurrentWeather
import com.nexoofficial.astralauncher.model.DailyWeather
import com.nexoofficial.astralauncher.model.HourlyWeather
import com.nexoofficial.astralauncher.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("astra_weather_cache_v2", Context.MODE_PRIVATE)

    suspend fun cached(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return@withContext null
        runCatching { snapshotFromCache(JSONObject(raw)) }.getOrNull()
    }

    fun isFresh(snapshot: WeatherSnapshot, maxAgeMillis: Long = 45 * 60 * 1000L): Boolean =
        System.currentTimeMillis() - snapshot.updatedAtMillis <= maxAgeMillis

    suspend fun refresh(latitude: Double, longitude: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val locationName = resolveLocationName(latitude, longitude)
        val url = buildUrl(latitude, longitude)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ASTRA-Launcher/0.2")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Weather service returned HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val snapshot = parseApi(JSONObject(body), latitude, longitude, locationName)
            prefs.edit().putString(KEY_SNAPSHOT, snapshotToCache(snapshot).toString()).apply()
            snapshot
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(latitude: Double, longitude: Double): String {
        val current = listOf(
            "temperature_2m",
            "apparent_temperature",
            "relative_humidity_2m",
            "weather_code",
            "wind_speed_10m",
            "precipitation",
            "is_day"
        ).joinToString(",")
        val hourly = listOf(
            "temperature_2m",
            "precipitation_probability",
            "weather_code"
        ).joinToString(",")
        val daily = listOf(
            "weather_code",
            "temperature_2m_max",
            "temperature_2m_min",
            "precipitation_probability_max"
        ).joinToString(",")

        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude" +
            "&longitude=$longitude" +
            "&current=${URLEncoder.encode(current, "UTF-8")}" +
            "&hourly=${URLEncoder.encode(hourly, "UTF-8")}" +
            "&daily=${URLEncoder.encode(daily, "UTF-8")}" +
            "&forecast_days=5&timezone=auto"
    }

    private fun parseApi(
        root: JSONObject,
        latitude: Double,
        longitude: Double,
        locationName: String
    ): WeatherSnapshot {
        val currentJson = root.getJSONObject("current")
        val currentTime = LocalDateTime.parse(currentJson.getString("time"))
        val current = CurrentWeather(
            time = currentTime,
            temperatureC = currentJson.optDouble("temperature_2m", 0.0),
            apparentTemperatureC = currentJson.optDouble("apparent_temperature", 0.0),
            relativeHumidity = currentJson.optInt("relative_humidity_2m", 0),
            weatherCode = currentJson.optInt("weather_code", -1),
            windKmh = currentJson.optDouble("wind_speed_10m", 0.0),
            precipitationMm = currentJson.optDouble("precipitation", 0.0),
            isDay = currentJson.optInt("is_day", 1) == 1
        )

        val hourlyJson = root.getJSONObject("hourly")
        val hourlyTimes = hourlyJson.getJSONArray("time")
        val hourlyTemps = hourlyJson.getJSONArray("temperature_2m")
        val hourlyRain = hourlyJson.getJSONArray("precipitation_probability")
        val hourlyCodes = hourlyJson.getJSONArray("weather_code")
        val hourly = mutableListOf<HourlyWeather>()
        for (i in 0 until hourlyTimes.length()) {
            val time = LocalDateTime.parse(hourlyTimes.getString(i))
            if (time.isBefore(currentTime.minusMinutes(1))) continue
            hourly += HourlyWeather(
                time = time,
                temperatureC = hourlyTemps.safeDouble(i),
                precipitationProbability = hourlyRain.safeInt(i),
                weatherCode = hourlyCodes.safeInt(i)
            )
            if (hourly.size == 8) break
        }

        val dailyJson = root.getJSONObject("daily")
        val dates = dailyJson.getJSONArray("time")
        val maxTemps = dailyJson.getJSONArray("temperature_2m_max")
        val minTemps = dailyJson.getJSONArray("temperature_2m_min")
        val maxRain = dailyJson.getJSONArray("precipitation_probability_max")
        val dailyCodes = dailyJson.getJSONArray("weather_code")
        val daily = (0 until dates.length()).map { i ->
            DailyWeather(
                date = LocalDate.parse(dates.getString(i)),
                temperatureMaxC = maxTemps.safeDouble(i),
                temperatureMinC = minTemps.safeDouble(i),
                precipitationProbabilityMax = maxRain.safeInt(i),
                weatherCode = dailyCodes.safeInt(i)
            )
        }

        return WeatherSnapshot(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            current = current,
            hourly = hourly,
            daily = daily,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveLocationName(latitude: Double, longitude: Double): String {
        if (!Geocoder.isPresent()) return "Current location"
        return runCatching {
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
            address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
                ?: "Current location"
        }.getOrDefault("Current location")
    }

    private fun snapshotToCache(snapshot: WeatherSnapshot): JSONObject = JSONObject().apply {
        put("latitude", snapshot.latitude)
        put("longitude", snapshot.longitude)
        put("locationName", snapshot.locationName)
        put("updatedAtMillis", snapshot.updatedAtMillis)
        put("current", JSONObject().apply {
            put("time", snapshot.current.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            put("temperatureC", snapshot.current.temperatureC)
            put("apparentTemperatureC", snapshot.current.apparentTemperatureC)
            put("relativeHumidity", snapshot.current.relativeHumidity)
            put("weatherCode", snapshot.current.weatherCode)
            put("windKmh", snapshot.current.windKmh)
            put("precipitationMm", snapshot.current.precipitationMm)
            put("isDay", snapshot.current.isDay)
        })
        put("hourly", JSONArray().apply {
            snapshot.hourly.forEach { item ->
                put(JSONObject().apply {
                    put("time", item.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    put("temperatureC", item.temperatureC)
                    put("precipitationProbability", item.precipitationProbability)
                    put("weatherCode", item.weatherCode)
                })
            }
        })
        put("daily", JSONArray().apply {
            snapshot.daily.forEach { item ->
                put(JSONObject().apply {
                    put("date", item.date.toString())
                    put("temperatureMaxC", item.temperatureMaxC)
                    put("temperatureMinC", item.temperatureMinC)
                    put("precipitationProbabilityMax", item.precipitationProbabilityMax)
                    put("weatherCode", item.weatherCode)
                })
            }
        })
    }

    private fun snapshotFromCache(root: JSONObject): WeatherSnapshot {
        val currentJson = root.getJSONObject("current")
        val current = CurrentWeather(
            time = LocalDateTime.parse(currentJson.getString("time")),
            temperatureC = currentJson.getDouble("temperatureC"),
            apparentTemperatureC = currentJson.getDouble("apparentTemperatureC"),
            relativeHumidity = currentJson.getInt("relativeHumidity"),
            weatherCode = currentJson.getInt("weatherCode"),
            windKmh = currentJson.getDouble("windKmh"),
            precipitationMm = currentJson.getDouble("precipitationMm"),
            isDay = currentJson.getBoolean("isDay")
        )
        val hourlyJson = root.getJSONArray("hourly")
        val hourly = (0 until hourlyJson.length()).map { i ->
            val item = hourlyJson.getJSONObject(i)
            HourlyWeather(
                time = LocalDateTime.parse(item.getString("time")),
                temperatureC = item.getDouble("temperatureC"),
                precipitationProbability = item.getInt("precipitationProbability"),
                weatherCode = item.getInt("weatherCode")
            )
        }
        val dailyJson = root.getJSONArray("daily")
        val daily = (0 until dailyJson.length()).map { i ->
            val item = dailyJson.getJSONObject(i)
            DailyWeather(
                date = LocalDate.parse(item.getString("date")),
                temperatureMaxC = item.getDouble("temperatureMaxC"),
                temperatureMinC = item.getDouble("temperatureMinC"),
                precipitationProbabilityMax = item.getInt("precipitationProbabilityMax"),
                weatherCode = item.getInt("weatherCode")
            )
        }
        return WeatherSnapshot(
            latitude = root.getDouble("latitude"),
            longitude = root.getDouble("longitude"),
            locationName = root.getString("locationName"),
            current = current,
            hourly = hourly,
            daily = daily,
            updatedAtMillis = root.getLong("updatedAtMillis")
        )
    }

    private fun JSONArray.safeDouble(index: Int): Double =
        if (isNull(index)) 0.0 else optDouble(index, 0.0)

    private fun JSONArray.safeInt(index: Int): Int =
        if (isNull(index)) 0 else optInt(index, 0)

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
