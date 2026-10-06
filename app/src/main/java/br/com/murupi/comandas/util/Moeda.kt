package br.com.murupi.comandas.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Valores em dinheiro ficam em centavos (Long) para não haver erro de arredondamento. */
object Moeda {

    /** 1234 -> "R$ 12,34". Sem espaço especial, para sair igual na tela e na impressora. */
    fun formatar(centavos: Long, comSimbolo: Boolean = true): String {
        val absoluto = abs(centavos)
        val reais = (absoluto / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val valor = "$reais,${(absoluto % 100).toString().padStart(2, '0')}"
        val sinal = if (centavos < 0) "-" else ""
        return if (comSimbolo) "${sinal}R$ $valor" else "$sinal$valor"
    }

    /** Aceita "12", "12,5", "12,50", "1.234,56", "R$ 12,00" e "12.50". Null se inválido ou negativo. */
    fun converter(texto: String): Long? {
        var limpo = texto.replace("R$", "").replace(" ", "").replace(" ", "").trim()
        if (limpo.isEmpty()) return null
        if (limpo.contains(',')) limpo = limpo.replace(".", "").replace(',', '.')
        return try {
            BigDecimal(limpo).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                .takeIf { it >= 0 }
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Valor para preencher um campo de edição: "16,00", ou vazio quando zero. */
    fun paraCampo(centavos: Long): String = if (centavos <= 0) "" else formatar(centavos, comSimbolo = false)

    /** Divide a conta arredondando para cima, para a soma das partes cobrir o total. */
    fun dividir(totalCentavos: Long, partes: Int): Long =
        if (partes <= 1) totalCentavos else (totalCentavos + partes - 1) / partes
}
