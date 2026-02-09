package com.example.fundmobile.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val moneyFmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)

    fun formatPercent(value: Double?): String {
        val v = value ?: return "--"
        val prefix = if (v > 0) "+" else ""
        return "$prefix${String.format(Locale.US, "%.2f", v)}%"
    }

    fun formatMoney(value: Double): String {
        return moneyFmt.format(value)
    }

    fun formatNav(value: String?): String {
        val v = value?.toDoubleOrNull() ?: return "--"
        return String.format(Locale.US, "%.4f", v)
    }

    fun formatShare(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    fun todayString(): String {
        return LocalDate.now(chinaZone).format(dayFmt)
    }

    fun formatDate(date: String): String {
        if (date.isBlank()) return ""
        return runCatching {
            if (date.contains("-")) {
                LocalDate.parse(date, dayFmt).format(dayFmt)
            } else if (date.length >= 8) {
                val normalized = date.take(8)
                LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyyMMdd")).format(dayFmt)
            } else {
                date
            }
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(date).toLocalDate().format(dayFmt)
            }.getOrDefault(date)
        }
    }

    fun isAfterHour(hour: Int): Boolean {
        return LocalDateTime.now(chinaZone).hour >= hour
    }
}
