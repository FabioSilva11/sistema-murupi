package br.com.murupi.comandas.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataHora {

    private val LOCALE = Locale.forLanguageTag("pt-BR")

    /** "06/10/2026 14:32" */
    fun completa(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE).format(Date(millis))

    /** "06/10 14:32" */
    fun curta(millis: Long): String = SimpleDateFormat("dd/MM HH:mm", LOCALE).format(Date(millis))
}
