package com.example.fundmobile.util

import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun formatPercent_positive_hasPlus() {
        assertEquals("+1.23%", Formatters.formatPercent(1.23))
    }

    @Test
    fun formatPercent_negative_hasMinus() {
        assertEquals("-0.45%", Formatters.formatPercent(-0.45))
    }

    @Test
    fun formatPercent_zero_noSign() {
        assertEquals("0.00%", Formatters.formatPercent(0.0))
    }

    @Test
    fun formatPercent_null_returnsDash() {
        assertEquals("--", Formatters.formatPercent(null))
    }

    @Test
    fun formatPercent_largeValue() {
        assertEquals("+99.99%", Formatters.formatPercent(99.99))
    }

    @Test
    fun formatMoney_positive() {
        val expected = NumberFormat.getCurrencyInstance(Locale.CHINA).format(1234.56)
        assertEquals(expected, Formatters.formatMoney(1234.56))
    }

    @Test
    fun formatMoney_zero() {
        val expected = NumberFormat.getCurrencyInstance(Locale.CHINA).format(0.0)
        assertEquals(expected, Formatters.formatMoney(0.0))
    }

    @Test
    fun formatMoney_negative() {
        val expected = NumberFormat.getCurrencyInstance(Locale.CHINA).format(-12.34)
        assertEquals(expected, Formatters.formatMoney(-12.34))
    }

    @Test
    fun formatNav_normal() {
        assertEquals("1.5000", Formatters.formatNav("1.5"))
    }

    @Test
    fun formatNav_null_returnsDash() {
        assertEquals("--", Formatters.formatNav(null))
    }

    @Test
    fun formatNav_invalidString_returnsDash() {
        assertEquals("--", Formatters.formatNav("abc"))
    }

    @Test
    fun formatNav_alreadyFourDecimals() {
        assertEquals("1.5000", Formatters.formatNav("1.5000"))
    }

    @Test
    fun formatShare_normal() {
        assertEquals("123.46", Formatters.formatShare(123.456))
    }

    @Test
    fun formatShare_zero() {
        assertEquals("0.00", Formatters.formatShare(0.0))
    }

    @Test
    fun formatDate_isoFormat() {
        assertEquals("2024-01-15", Formatters.formatDate("2024-01-15"))
    }

    @Test
    fun formatDate_compactFormat() {
        assertEquals("2024-01-15", Formatters.formatDate("20240115"))
    }

    @Test
    fun formatDate_emptyString() {
        assertEquals("", Formatters.formatDate(""))
    }

    @Test
    fun formatDate_invalidFormat_returnsOriginal() {
        assertEquals("xyz", Formatters.formatDate("xyz"))
    }

    @Test
    fun todayString_returnsValidFormat() {
        val value = Formatters.todayString()
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value))
    }
}
