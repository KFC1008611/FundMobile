package com.example.fundmobile.domain

import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.HoldingPosition
import java.time.ZonedDateTime
import java.time.ZoneId

object PortfolioCalculator {
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

    data class HoldingProfit(
        val amount: Double,
        val profitToday: Double?,
        val profitTotal: Double?
    )

    fun getHoldingProfit(
        fund: FundData,
        holding: HoldingPosition?,
        isTradingDay: Boolean,
        todayStr: String
    ): HoldingProfit? {
        if (holding == null) return null

        val isAfter9am = ZonedDateTime.now(chinaZone).hour >= 9
        val hasTodayData = fund.jzrq == todayStr
        val hasTodayValuation = fund.gztime?.startsWith(todayStr) == true
        val canCalcTodayProfit = hasTodayData || hasTodayValuation
        val useValuation = isTradingDay && isAfter9am && !hasTodayData

        val currentNav: Double
        val profitToday: Double?

        if (!useValuation) {
            currentNav = fund.dwjz?.toDoubleOrNull() ?: return null
            if (canCalcTodayProfit) {
                val amount = holding.share * currentNav
                val rate = fund.zzl ?: fund.gszzl ?: 0.0
                profitToday = amount - (amount / (1 + rate / 100))
            } else {
                profitToday = null
            }
        } else {
            val hasEstimatedPrice = (fund.estPricedCoverage > 0.05) && fund.estGsz != null
            currentNav = if (hasEstimatedPrice) {
                fund.estGsz
            } else {
                fund.gsz?.toDoubleOrNull() ?: fund.dwjz?.toDoubleOrNull()
            } ?: return null
            if (canCalcTodayProfit) {
                val amount = holding.share * currentNav
                val rate = if (hasEstimatedPrice) {
                    fund.estGszzl ?: 0.0
                } else {
                    fund.gszzl ?: 0.0
                }
                profitToday = amount - (amount / (1 + rate / 100))
            } else {
                profitToday = null
            }
        }

        val amount = holding.share * currentNav
        val profitTotal = (currentNav - holding.cost) * holding.share

        return HoldingProfit(
            amount = amount,
            profitToday = profitToday,
            profitTotal = profitTotal
        )
    }

    data class GroupSummary(
        val totalAsset: Double,
        val totalProfitToday: Double,
        val totalHoldingReturn: Double,
        val returnRate: Double,
        val hasHolding: Boolean
    )

    fun calculateGroupSummary(
        funds: List<FundData>,
        holdings: Map<String, HoldingPosition>,
        isTradingDay: Boolean,
        todayStr: String
    ): GroupSummary {
        var totalAsset = 0.0
        var totalProfitToday = 0.0
        var totalHoldingReturn = 0.0
        var totalCost = 0.0
        var hasHolding = false

        funds.forEach { fund ->
            val holding = holdings[fund.code]
            val profit = getHoldingProfit(fund, holding, isTradingDay, todayStr)
            if (profit != null) {
                hasHolding = true
                totalAsset += profit.amount
                totalProfitToday += profit.profitToday ?: 0.0
                totalHoldingReturn += profit.profitTotal ?: 0.0
                if (holding != null) {
                    totalCost += holding.cost * holding.share
                }
            }
        }

        val returnRate = if (totalCost > 0) (totalHoldingReturn / totalCost) * 100 else 0.0

        return GroupSummary(
            totalAsset = totalAsset,
            totalProfitToday = totalProfitToday,
            totalHoldingReturn = totalHoldingReturn,
            returnRate = returnRate,
            hasHolding = hasHolding
        )
    }
}
