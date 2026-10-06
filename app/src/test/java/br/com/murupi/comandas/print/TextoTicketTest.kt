package br.com.murupi.comandas.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoTicketTest {

    @Test
    fun quebra_sem_cortar_palavras() {
        val linhas = TextoTicket.quebrar("Macarrão ao Molho de Camarão", 15)
        assertEquals(listOf("Macarrão ao", "Molho de", "Camarão"), linhas)
        assertEquals(listOf("Macarrão ao", "Molho de Camarão"), TextoTicket.quebrar("Macarrão ao Molho de Camarão", 16))
    }

    @Test
    fun linhas_de_continuacao_recebem_recuo() {
        val linhas = TextoTicket.quebrar("2x Farofa de Jabá com Banana", 16, recuo = "   ")
        assertEquals("2x Farofa de", linhas[0])
        assertTrue(linhas.drop(1).all { it.startsWith("   ") })
        assertTrue(linhas.all { it.length <= 16 })
    }

    @Test
    fun palavra_maior_que_a_linha_e_cortada() {
        val linhas = TextoTicket.quebrar("ABCDEFGHIJKLMNOPQRST", 8)
        assertEquals(listOf("ABCDEFGH", "IJKLMNOP", "QRST"), linhas)
    }

    @Test
    fun texto_vazio_gera_uma_linha_vazia() {
        assertEquals(listOf(""), TextoTicket.quebrar("", 32))
    }

    @Test
    fun colunas_ocupam_a_largura_toda() {
        val linha = TextoTicket.colunas("TOTAL", "R$ 48,00", 32)
        assertEquals(32, linha.length)
        assertTrue(linha.startsWith("TOTAL"))
        assertTrue(linha.endsWith("R$ 48,00"))
    }

    @Test
    fun valor_fica_na_ultima_linha_da_descricao() {
        val linhas = TextoTicket.comValor("1x Macarrão ao Molho de Camarão", "20,00", 24)
        assertTrue(linhas.last().endsWith("20,00"))
        assertTrue(linhas.all { it.length <= 24 })
    }
}
