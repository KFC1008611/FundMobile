package com.example.fundmobile.domain

import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.HoldingPosition
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioCalculatorTest {

    private fun fund(
        code: String = "161725",
        dwjz: String? = "1.50",
        gsz: String? = null,
        gztime: String? = null,
        jzrq: String? = "2024-01-15",
        gszzl: Double? = null,
        zzl: Double? = null,
        estGsz: Double? = null,
        estGszzl: Double? = null,
        estPricedCoverage: Double = 0.0
    ): FundData {
        return FundData(
            code = code,
            name = "Test Fund",
            dwjz = dwjz,
            gsz = gsz,
            gztime = gztime,
            jzrq = jzrq,
            gszzl = gszzl,
            zzl = zzl,
            estGsz = estGsz,
            estGszzl = estGszzl,
            estPricedCoverage = estPricedCoverage,
            noValuation = false,
            holdings = emptyList()
        )
    }

    @Test
    fun getHoldingProfit_nullHolding_returnsNull() {
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(),
            holding = null,
            isTradingDay = true,
            todayStr = "2024-01-15"
        )
        assertNull(result)
    }

    @Test
    fun getHoldingProfit_withConfirmedNav_calculatesCorrectly() {
        val today = "2024-01-15"
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(dwjz = "1.5", jzrq = today, zzl = 2.0),
            holding = HoldingPosition(share = 1000.0, cost = 1.3),
            isTradingDay = true,
            todayStr = today
        )

        assertNotNull(result)
        assertEquals(1500.0, result!!.amount, 1e-6)
        assertEquals(200.0, result.profitTotal!!, 1e-6)
        val expectedToday = 1500.0 - (1500.0 / 1.02)
        assertEquals(expectedToday, result.profitToday!!, 1e-6)
    }

    @Test
    fun getHoldingProfit_withEstimatedNav_useValuation() {
        val today = "2024-01-15"
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(
                dwjz = "1.4",
                gsz = "1.45",
                jzrq = "2024-01-14",
                gztime = "$today 14:30",
                gszzl = 3.57
            ),
            holding = HoldingPosition(share = 1000.0, cost = 1.2),
            isTradingDay = true,
            todayStr = today
        )

        assertNotNull(result)
        val isAfter9amChina = LocalDate.now(ZoneId.of("Asia/Shanghai")) != null &&
            java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).hour >= 9
        val expectedNav = if (isAfter9amChina) 1.45 else 1.4
        assertEquals(expectedNav * 1000.0, result!!.amount, 1e-6)
        assertEquals((expectedNav - 1.2) * 1000.0, result.profitTotal!!, 1e-6)
        val expectedToday = result.amount - (result.amount / (1 + 3.57 / 100))
        assertEquals(expectedToday, result.profitToday!!, 1e-6)
    }

    @Test
    fun getHoldingProfit_withEstimatedHoldingPrice_prefersEstimatedFields() {
        val today = "2024-01-15"
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(
                dwjz = "1.40",
                gsz = "1.45",
                jzrq = "2024-01-14",
                gztime = "$today 14:30",
                gszzl = 3.57,
                estGsz = 1.60,
                estGszzl = 14.28,
                estPricedCoverage = 0.20
            ),
            holding = HoldingPosition(share = 1000.0, cost = 1.2),
            isTradingDay = true,
            todayStr = today
        )

        assertNotNull(result)
        val isAfter9amChina = java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).hour >= 9
        val expectedNav = if (isAfter9amChina) 1.60 else 1.40
        assertEquals(expectedNav * 1000.0, result!!.amount, 1e-6)
        assertEquals((expectedNav - 1.2) * 1000.0, result.profitTotal!!, 1e-6)
    }

    @Test
    fun getHoldingProfit_nonTradingDay_usesConfirmedNav() {
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(dwjz = "1.40", gsz = "1.45", jzrq = "2024-01-14", gztime = "2024-01-15 14:30", gszzl = 3.57),
            holding = HoldingPosition(share = 1000.0, cost = 1.2),
            isTradingDay = false,
            todayStr = "2024-01-15"
        )

        assertNotNull(result)
        assertEquals(1400.0, result!!.amount, 1e-6)
        assertEquals(200.0, result.profitTotal!!, 1e-6)
    }

    @Test
    fun getHoldingProfit_nullDwjz_returnsNull() {
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(dwjz = null, gsz = null),
            holding = HoldingPosition(share = 1000.0, cost = 1.2),
            isTradingDay = false,
            todayStr = "2024-01-15"
        )
        assertNull(result)
    }

    @Test
    fun getHoldingProfit_noTodayData_profitTodayIsNull() {
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(dwjz = "1.4", jzrq = "2024-01-10", gztime = "2024-01-14 15:00", zzl = 2.0),
            holding = HoldingPosition(share = 1000.0, cost = 1.2),
            isTradingDay = false,
            todayStr = "2024-01-15"
        )

        assertNotNull(result)
        assertNull(result!!.profitToday)
    }

    @Test
    fun getHoldingProfit_zeroCostPrice() {
        val result = PortfolioCalculator.getHoldingProfit(
            fund = fund(dwjz = "1.5", jzrq = "2024-01-15", zzl = 2.0),
            holding = HoldingPosition(share = 1000.0, cost = 0.0),
            isTradingDay = true,
            todayStr = "2024-01-15"
        )

        assertNotNull(result)
        assertEquals(1500.0, result!!.profitTotal!!, 1e-6)
    }

    @Test
    fun calculateGroupSummary_empty_hasHoldingFalse() {
        val result = PortfolioCalculator.calculateGroupSummary(
            funds = emptyList(),
            holdings = emptyMap(),
            isTradingDay = true,
            todayStr = "2024-01-15"
        )

        assertFalse(result.hasHolding)
        assertEquals(0.0, result.totalAsset, 1e-6)
        assertEquals(0.0, result.totalProfitToday, 1e-6)
        assertEquals(0.0, result.totalHoldingReturn, 1e-6)
        assertEquals(0.0, result.returnRate, 1e-6)
    }

    @Test
    fun calculateGroupSummary_multipleFunds_aggregatesCorrectly() {
        val today = "2024-01-15"
        val funds = listOf(
            fund(code = "A", dwjz = "2.0", jzrq = today, zzl = 1.0),
            fund(code = "B", dwjz = "1.5", jzrq = today, zzl = -0.5),
            fund(code = "C", dwjz = "1.2", jzrq = today, zzl = 0.0)
        )
        val holdings = mapOf(
            "A" to HoldingPosition(share = 100.0, cost = 1.5),
            "B" to HoldingPosition(share = 200.0, cost = 1.0)
        )

        val result = PortfolioCalculator.calculateGroupSummary(funds, holdings, isTradingDay = true, todayStr = today)

        assertTrue(result.hasHolding)
        assertEquals(500.0, result.totalAsset, 1e-6)
        assertEquals(150.0, result.totalHoldingReturn, 1e-6)
        assertTrue(abs(result.totalProfitToday) > 0.0)
        assertEquals(42.857142857, result.returnRate, 1e-6)
    }

    @Test
    fun calculateGroupSummary_mixedWithAndWithoutHoldings() {
        val funds = listOf(
            fund(code = "A", dwjz = "2.0", jzrq = "2024-01-15", zzl = 1.0),
            fund(code = "B", dwjz = "1.5", jzrq = "2024-01-15", zzl = 1.0),
            fund(code = "C", dwjz = "1.2", jzrq = "2024-01-15", zzl = 1.0)
        )
        val holdings = mapOf("B" to HoldingPosition(share = 100.0, cost = 1.0))

        val result = PortfolioCalculator.calculateGroupSummary(funds, holdings, isTradingDay = true, todayStr = "2024-01-15")

        assertTrue(result.hasHolding)
        assertEquals(150.0, result.totalAsset, 1e-6)
        assertEquals(50.0, result.totalHoldingReturn, 1e-6)
    }

    @Test
    fun calculateGroupSummary_returnRate_isPercentage() {
        val funds = listOf(
            fund(code = "X", dwjz = "1.05", jzrq = "2024-01-15", zzl = 0.0)
        )
        val holdings = mapOf(
            "X" to HoldingPosition(share = 10000.0, cost = 1.0)
        )

        val result = PortfolioCalculator.calculateGroupSummary(funds, holdings, isTradingDay = true, todayStr = "2024-01-15")

        assertEquals(500.0, result.totalHoldingReturn, 1e-6)
        assertEquals(5.0, result.returnRate, 1e-6)
    }
}
