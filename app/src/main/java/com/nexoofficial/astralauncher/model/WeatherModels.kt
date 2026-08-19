package com.nexoofficial.astralauncher.model

import java.time.LocalDate
import java.time.LocalDateTime

data class WeatherSnapshot(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val updatedAtMillis: Long
)

data class CurrentWeather(
    val time: LocalDateTime,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val relativeHumidity: Int,
    val weatherCode: Int,
    val windKmh: Double,
    val precipitationMm: Double,
    val isDay: Boolean
)

data class HourlyWeather(
    val time: LocalDateTime,
    val temperatureC: Double,
    val precipitationProbability: Int,
    val weatherCode: Int
)

data class DailyWeather(
    val date: LocalDate,
    val temperatureMaxC: Double,
    val temperatureMinC: Double,
    val precipitationProbabilityMax: Int,
    val weatherCode: Int
)
