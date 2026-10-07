package br.com.murupi.comandas.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Valores em dinheiro ficam em centavos (Long) para não haver erro de arredondamento. */
object Moeda {

    private val localeBr = Locale("pt", "BR")
    private val simbolos = DecimalFormatSymbols(localeBr)
    private val formatador = DecimalFormat("#,##0.00", simbolos)

    /** Formata centavos em reais, ex: 1234 -> "R$ 12,34" ou "12,34". */
    fun formatar(centavos: Long, comSimbolo: Boolean = true): String {
        val absoluto = kotlin.math.abs(centavos)
        val valor = formatador.format(absoluto / 100.0)
        val sinal = if (centavos < 0) "-" else ""
        return if (comSimbolo) "${sinal}R$ $valor" else "$sinal$valor"
    }

    /**
     * Converte o valor digitado pelo usuário para centavos (Long).
     * Aceita números com vírgula ou ponto, com ou sem R$.
     */
    fun converter(texto: String): Long? {
        val limpo = texto.replace("R$", "").replace(" ", "").replace(" ", "").trim()
        if (limpo.isEmpty()) return null

        return try {
            // Se tem vírgula, usa o parse do padrão pt-BR
            val numero = if (limpo.contains(',')) {
                formatador.parse(limpo)?.toDouble()
            } else {
                // Se só tem dígitos ou ponto, pode ser número inteiro ou decimal com ponto
                if (limpo.contains('.')) {
                    val partes = limpo.split('.')
                    if (partes.size == 2 && partes[1].length == 3) {
                        // Milhar sem centavos (ex.: "1.234")
                        (partes[0] + partes[1]).toDouble()
                    } else if (partes.size > 2) {
                        limpo.replace(".", "").toDouble()
                    } else {
                        limpo.toDouble()
                    }
                } else {
                    limpo.toDouble()
                }
            }
            if (numero != null && numero >= 0) {
                BigDecimal.valueOf(numero).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Valor para preencher um campo de edição: "16,00", ou vazio quando zero. */
    fun paraCampo(centavos: Long): String = if (centavos <= 0) "" else formatar(centavos, comSimbolo = false)

    /** Divide a conta arredondando para cima, para a soma das partes cobrir o total. */
    fun dividir(totalCentavos: Long, partes: Int): Long =
        if (partes <= 1) totalCentavos else (totalCentavos + partes - 1) / partes

    /** Valor da taxa percentual sobre o total, arredondada para o centavo. */
    fun taxaServico(totalCentavos: Long, percentual: Int): Long =
        if (percentual <= 0) 0L else Math.round(totalCentavos * percentual / 100.0)
}
