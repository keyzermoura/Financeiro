package br.com.carteirainvest.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun nowMillis(): Long = System.currentTimeMillis()

fun epochMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

fun localDateToMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

fun daysBetween(from: LocalDate, to: LocalDate): Long =
    ChronoUnit.DAYS.between(from, to)

fun isMaturitySoon(maturity: LocalDate?, today: LocalDate = LocalDate.now(), days: Long = 30): Boolean {
    if (maturity == null) return false
    val remaining = daysBetween(today, maturity)
    return remaining in 0..days
}
