package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.PapelImpressora
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketsTest {

    private val comanda = ItensDeTeste.comanda

    /** Texto impresso do ticket, sem os comandos ESC/POS (ESC @ tem 2 bytes; os demais usados, 3). */
    private fun texto(bytes: ByteArray): String {
        val saida = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0x1B || b == 0x1D) {
                i += if (bytes.getOrNull(i + 1)?.toInt() == '@'.code) 2 else 3
            } else {
                saida.append(b.toChar())
                i++
            }
        }
        return saida.toString()
    }

    @Test
    fun producao_tem_quantidade_e_nome_mas_nao_tem_preco() {
        val ticket = texto(
            Tickets.producao(
                ItensDeTeste.impressora(PapelImpressora.COZINHA), PapelImpressora.COZINHA, comanda,
                listOf(ItensDeTeste.isca, ItensDeTeste.refrigerante), reimpressao = false
            )
        )
        assertTrue(ticket.contains("COZINHA"))
        assertTrue(ticket.contains("MESA 9.1"))
        assertTrue(ticket.contains("2x Isca de Frango"))
        assertTrue(ticket.contains("2x Refrigerante Lata"))
        assertFalse(ticket.contains("R$"))
        assertFalse(ticket.contains("16,00"))
    }

    @Test
    fun producao_destaca_demanda_e_observacao_sem_acentos() {
        val ticket = texto(
            Tickets.producao(
                ItensDeTeste.impressora(PapelImpressora.SUCOS), PapelImpressora.SUCOS, comanda,
                listOf(ItensDeTeste.sucoSobDemanda), reimpressao = false
            )
        )
        assertTrue(ticket.contains(">>> 1x Suco de Maracuja 300ml"))
        assertTrue(ticket.contains("OBS: COM LEITE"))
    }

    @Test
    fun producao_junta_itens_iguais_lancados_separados() {
        val ticket = texto(
            Tickets.producao(
                ItensDeTeste.impressora(PapelImpressora.COZINHA), PapelImpressora.COZINHA, comanda,
                listOf(ItensDeTeste.isca, ItensDeTeste.isca.copy(id = 10, quantidade = 1)), reimpressao = true
            )
        )
        assertTrue(ticket.contains("3x Isca de Frango"))
        assertTrue(ticket.contains("REIMPRESSAO"))
    }

    @Test
    fun espelho_tem_precos_total_e_valor_por_pessoa() {
        val itens = listOf(ItensDeTeste.isca, ItensDeTeste.sucoSobDemanda, ItensDeTeste.refrigerante)
        val ticket = texto(Tickets.espelho(ItensDeTeste.impressora(PapelImpressora.ESPELHO), comanda, itens))
        assertTrue(ticket.contains("MURUPI RESTAURANTE"))
        assertTrue(ticket.contains("2x Isca de Frango"))
        assertTrue(ticket.contains("32,00"))
        assertTrue(ticket.contains("R$ 48,00"))
        assertTrue(ticket.contains("R$ 24,00"))
        assertTrue(ticket.contains("NAO E DOCUMENTO FISCAL"))
    }

    @Test
    fun linhas_respeitam_a_bobina_de_58mm() {
        val impressora = ItensDeTeste.impressora(PapelImpressora.ESPELHO, colunas = Impressora.COLUNAS_58MM)
        val itens = listOf(ItensDeTeste.isca.copy(nome = "Macarrão ao Molho de Camarão com Queijo Extra"))
        val linhas = texto(Tickets.espelho(impressora, comanda, itens)).split('\n')
        assertTrue(linhas.joinToString("\n"), linhas.all { it.length <= Impressora.COLUNAS_58MM })
    }
}
