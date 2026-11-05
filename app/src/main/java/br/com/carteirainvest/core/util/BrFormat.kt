package br.com.carteirainvest.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting and parsing helpers. Monetary values are kept as [BigDecimal] until
 * the very last moment, when they are formatted for display.
 */
object BrFormat {

    val BRAZIL: Locale = Locale.forLanguageTag("pt-BR")

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", BRAZIL)

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", BRAZIL)

    private val monthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BRAZIL)

    fun currency(value: BigDecimal): String =
        NumberFormat.getCurrencyInstance(BRAZIL).format(value.setScale(2, RoundingMode.HALF_UP))

    fun currency(value: BigDecimal?, fallback: String = "—"): String =
        value?.let(::currency) ?: fallback

    fun signedCurrency(value: BigDecimal): String {
        val formatted = currency(value.abs())
        return when {
            value > BigDecimal.ZERO -> "+$formatted"
            value < BigDecimal.ZERO -> "-$formatted"
            else -> formatted
        }
    }

    fun compactCurrency(value: BigDecimal): String {
        val abs = value.abs()
        val sign = if (value < BigDecimal.ZERO) "-" else ""
        val prefix = "R$ "
        return when {
            abs >= BigDecimal("1000000000") -> "$sign$prefix${twoDecimals(abs.divide(BigDecimal("1000000000"), 1, RoundingMode.HALF_UP))} bi"
            abs >= BigDecimal("1000000") -> "$sign$prefix${twoDecimals(abs.divide(BigDecimal("1000000"), 1, RoundingMode.HALF_UP))} mi"
            abs >= BigDecimal("1000") -> "$sign$prefix${twoDecimals(abs.divide(BigDecimal("1000"), 1, RoundingMode.HALF_UP))} mil"
            else -> currency(value)
        }
    }

    fun percent(value: BigDecimal, signed: Boolean = false): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        val number = decimal(rounded)
        val prefix = if (signed && rounded > BigDecimal.ZERO) "+" else ""
        return "$prefix$number%"
    }

    fun quantity(value: BigDecimal): String {
        val formatter = NumberFormat.getNumberInstance(BRAZIL)
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 8
        return formatter.format(value)
    }

    fun date(date: LocalDate?): String = date?.format(dateFormatter) ?: "—"

    fun dateTime(epochMillis: Long?): String =
        epochMillis?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .format(dateTimeFormatter)
        } ?: "—"

    fun monthYear(date: LocalDate): String =
        date.format(monthYearFormatter).replaceFirstChar { it.titlecase(BRAZIL) }

    fun relative(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMillis == null) return "Nunca atualizado"
        val diffMinutes = ((now - epochMillis) / 60_000L).coerceAtLeast(0)
        return when {
            diffMinutes < 1 -> "Agora"
            diffMinutes < 60 -> "Há ${diffMinutes}min"
            diffMinutes < 1_440 -> "Há ${diffMinutes / 60}h"
            else -> "Há ${diffMinutes / 1_440}d"
        }
    }

    fun decimal(value: BigDecimal): String {
        val formatter = NumberFormat.getNumberInstance(BRAZIL)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 8
        formatter.isGroupingUsed = true
        return formatter.format(value)
    }

    /**
     * Accepts both Brazilian ("1.234,56") and plain ("1234.56") decimal input.
     * Returns null for empty/invalid values and always returns an absolute value.
     */
    fun parseDecimal(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace("%", "")
        if (value.isEmpty()) return null
        value = when {
            value.contains(',') -> value.replace(".", "").replace(',', '.')
            value.count { it == '.' } > 1 -> value.replace(".", "")
            else -> value
        }
        if (!value.matches(Regex("\\d+(\\.\\d+)?"))) return null
        return value.toBigDecimalOrNull()?.abs()
    }

    fun inputDecimal(value: BigDecimal?): String {
        if (value == null) return ""
        return value.stripTrailingZeros().toPlainString().replace('.', ',')
    }

    private fun twoDecimals(value: BigDecimal): String {
        val formatter = NumberFormat.getNumberInstance(BRAZIL)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(value)
    }
}

fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

fun BigDecimal.percentScale(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

fun BigDecimal.orZero(): BigDecimal = this

fun BigDecimal.safeDivide(divisor: BigDecimal, scale: Int = 8): BigDecimal =
    if (divisor.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
    else divide(divisor, scale, RoundingMode.HALF_UP)

fun percentOf(part: BigDecimal, total: BigDecimal): BigDecimal =
    if (total.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
    else part.multiply(BigDecimal("100")).safeDivide(total)

fun isPositive(value: BigDecimal): Boolean = value > BigDecimal.ZERO

fun isNegative(value: BigDecimal): Boolean = value < BigDecimal.ZERO

fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.toBrDate(): String = format(DateTimeFormatter.ofPattern("dd/MM/yyyy", BrFormat.BRAZIL))
