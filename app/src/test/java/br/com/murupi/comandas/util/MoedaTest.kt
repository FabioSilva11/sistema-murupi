package br.com.murupi.comandas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoedaTest {

    @Test
    fun formata_em_reais() {
        assertEquals("R$ 16,00", Moeda.formatar(1600))
        assertEquals("R$ 0,05", Moeda.formatar(5))
        assertEquals("R$ 1.234,56", Moeda.formatar(123456))
        assertEquals("-R$ 2,50", Moeda.formatar(-250))
        assertEquals("40,00", Moeda.formatar(4000, comSimbolo = false))
    }

    @Test
    fun converte_texto_digitado() {
        assertEquals(1200L, Moeda.converter("12"))
        assertEquals(1250L, Moeda.converter("12,5"))
        assertEquals(1250L, Moeda.converter("12.50"))
        assertEquals(123400L, Moeda.converter("1.234"))
        assertEquals(123456L, Moeda.converter("1.234,56"))
        assertEquals(1600L, Moeda.converter("R$ 16,00"))
        assertEquals(0L, Moeda.converter("0"))
    }

    @Test
    fun rejeita_valor_invalido() {
        assertNull(Moeda.converter(""))
        assertNull(Moeda.converter("abc"))
        assertNull(Moeda.converter("-3"))
    }

    @Test
    fun campo_volta_para_o_mesmo_valor() {
        assertEquals(123456L, Moeda.converter(Moeda.paraCampo(123456)))
        assertEquals("", Moeda.paraCampo(0))
    }

    @Test
    fun divisao_por_pessoa_arredonda_para_cima() {
        assertEquals(334L, Moeda.dividir(1000, 3))
        assertEquals(2400L, Moeda.dividir(4800, 2))
        assertEquals(4800L, Moeda.dividir(4800, 0))
    }

    @Test
    fun taxa_servico_soma_o_percentual() {
        assertEquals(528L, Moeda.taxaServico(5280, 10))
        assertEquals(0L, Moeda.taxaServico(5280, 0))
        assertEquals(6L, Moeda.taxaServico(63, 10))
    }
}
