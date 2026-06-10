package com.popcorn.inventory.ui

import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val moneyFormat = DecimalFormat("$#,##0.00")
private val unitsFormat = DecimalFormat("#,##0")
private val spanishLocale = Locale("es", "MX")

fun Double.formatoDinero(): String = moneyFormat.format(this)

fun Int.formatoUnidades(): String = unitsFormat.format(this)

fun Long.aFechaLocal(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.formatoFechaNegocio(): String {
    val mes = month.getDisplayName(java.time.format.TextStyle.FULL, spanishLocale)
        .replaceFirstChar { it.titlecase(spanishLocale) }
    return "%02d/%s/%02d".format(dayOfMonth, mes, year % 100)
}
