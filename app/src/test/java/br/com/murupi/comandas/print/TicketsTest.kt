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
    fun atualizacao_de_producao_reimprime_estado_atual_e_destaca_item_cancelado() {
        val ticket = texto(
            Tickets.atualizacaoProducao(
                ItensDeTeste.impressora(PapelImpressora.COZINHA),
                PapelImpressora.COZINHA,
                comanda,
                itensAtuais = listOf(ItensDeTeste.isca, ItensDeTeste.refrigerante.copy(id = 20, enviadoProducao = false)),
                cancelamentos = listOf(ItensDeTeste.refrigerante.copy(id = 21, quantidade = 1, cancelado = true))
            )
        )
        val pedidoAtual = ticket.substringBefore("CANCELAMENTOS")
        val aviso = ticket.substringAfter("CANCELAMENTOS")

        assertTrue(ticket, ticket.contains("ATUALIZACAO COMPLETA"))
        assertTrue(pedidoAtual, pedidoAtual.contains("2x Isca de Frango"))
        assertTrue(pedidoAtual, pedidoAtual.contains("2x Refrigerante Lata"))
        assertTrue(aviso, aviso.contains("CANCELADO: 1x Refrigerante Lata"))
        assertTrue(ticket, ticket.contains("NAO REPREPARAR ITENS JA PRODUZIDOS"))
        assertFalse(ticket, ticket.contains("R$"))
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

    @Test
    fun historico_mostra_valor_pago_e_formas_de_pagamento_divididas() {
        val venda = comanda.copy(
            formaPagamento = br.com.murupi.comandas.data.model.FormaPagamento.PIX,
            formasPagamentoCsv = "PIX,DINHEIRO",
            totalPagoCentavos = 3_000L
        )
        val ticket = texto(
            Tickets.historico(
                ItensDeTeste.impressora(PapelImpressora.ESPELHO),
                listOf(venda to listOf(ItensDeTeste.isca)),
                agora = 0L
            )
        )

        assertTrue(ticket, ticket.contains("Pagamento: PIX + Dinheiro"))
        assertTrue(ticket.contains("SALDO TOTAL"))
        assertTrue(ticket.contains("30,00"))
    }

    @Test
    fun produtos_de_setores_diferentes_saem_nas_impressoras_corretas() {
        val todos = listOf(ItensDeTeste.isca, ItensDeTeste.refrigerante, ItensDeTeste.sucoSobDemanda)
        val separados = RoteadorImpressao.separarProducao(
            todos,
            setOf(PapelImpressora.COZINHA, PapelImpressora.SUCOS)
        )
        val cozinha = texto(
            Tickets.producao(
                ItensDeTeste.impressora(PapelImpressora.COZINHA), PapelImpressora.COZINHA,
                comanda, separados.getValue(PapelImpressora.COZINHA), reimpressao = false
            )
        )
        val sucos = texto(
            Tickets.producao(
                ItensDeTeste.impressora(PapelImpressora.SUCOS), PapelImpressora.SUCOS,
                comanda, separados.getValue(PapelImpressora.SUCOS), reimpressao = false
            )
        )
        val espelho = texto(
            Tickets.espelho(ItensDeTeste.impressora(PapelImpressora.ESPELHO), comanda, todos)
        )

        assertTrue(cozinha.contains("Isca de Frango"))
        assertTrue(cozinha.contains("Refrigerante Lata"))
        assertFalse(cozinha.contains("Suco de Maracuja"))
        assertTrue(sucos.contains("Suco de Maracuja"))
        assertFalse(sucos.contains("Isca de Frango"))
        assertFalse(sucos.contains("Refrigerante Lata"))
        assertTrue(espelho.contains("Isca de Frango"))
        assertTrue(espelho.contains("Refrigerante Lata"))
        assertTrue(espelho.contains("Suco de Maracuja"))
    }

    @Test
    fun historico_soma_as_vendas_e_lista_todos_os_produtos() {
        val vendaMesa = comanda.copy(
            totalPagoCentavos = 3_000L,
            formaPagamento = br.com.murupi.comandas.data.model.FormaPagamento.PIX
        )
        val vendaDelivery = comanda.copy(
            id = 2,
            tipo = br.com.murupi.comandas.data.model.TipoComanda.DELIVERY,
            nomeCliente = "Cliente teste",
            totalPagoCentavos = 600L,
            formaPagamento = br.com.murupi.comandas.data.model.FormaPagamento.DINHEIRO
        )
        val ticket = texto(
            Tickets.historico(
                ItensDeTeste.impressora(PapelImpressora.ESPELHO),
                listOf(
                    vendaMesa to listOf(ItensDeTeste.isca),
                    vendaDelivery to listOf(ItensDeTeste.sucoSobDemanda)
                ),
                agora = 0L
            )
        )

        assertTrue(ticket.contains("Isca de Frango"))
        assertTrue(ticket.contains("Suco de Maracuja"))
        assertTrue(ticket.contains("SALDO TOTAL"))
        assertTrue(ticket.contains("36,00"))
    }
}
