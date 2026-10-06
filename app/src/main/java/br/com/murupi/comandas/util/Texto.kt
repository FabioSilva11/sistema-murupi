package br.com.murupi.comandas.util

import java.text.Normalizer
import java.util.Locale

object Texto {

    private val MARCAS = Regex("\\p{Mn}+")

    /** "Açaí com limão" -> "Acai com limao". */
    fun semAcentos(texto: String): String =
        MARCAS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "")

    /** Forma usada para comparar na busca: sem acento, minúscula e sem espaços nas pontas. */
    fun normalizarBusca(texto: String): String = semAcentos(texto).lowercase(Locale.ROOT).trim()
}
