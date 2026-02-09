package com.example.fundmobile.domain

import com.example.fundmobile.data.remote.FundApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TradingDayChecker {
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    suspend fun isTradingDay(): Boolean {
        val now = LocalDate.now(chinaZone)
        val weekDay = now.dayOfWeek.value
        val isWeekend = weekDay == 6 || weekDay == 7
        if (isWeekend) return false

        return try {
            val indexDate = FundApi.fetchShanghaiIndexDate()
            if (indexDate == null) {
                true
            } else {
                val currentStr = now.format(dayFmt)
                if (indexDate == currentStr) {
                    true
                } else {
                    val currentTime = LocalTime.now(chinaZone)
                    if (currentTime >= LocalTime.of(9, 30)) false else true
                }
            }
        } catch (_: Exception) {
            true
        }
    }
}
