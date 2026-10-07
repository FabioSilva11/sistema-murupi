package br.com.murupi.comandas.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaReaberturaTest {

    @Test
    fun conta_paga_consultada_pelo_historico_nunca_pode_reabrir() {
        val contaPaga = Comanda(
            id = 42,
            mesa = 9,
            sequencia = 1,
            status = StatusComanda.FECHADA,
            formaPagamento = FormaPagamento.PIX,
            totalPagoCentavos = 3500
        )

        assertFalse(PoliticaReabertura.podeReabrir(contaPaga, consultaHistorico = true))
    }

    @Test
    fun conta_fechada_sem_pagamento_pode_reabrir_fora_do_historico() {
        val espelhoFechado = Comanda(
            id = 43,
            mesa = 9,
            sequencia = 2,
            status = StatusComanda.FECHADA
        )

        assertTrue(PoliticaReabertura.podeReabrir(espelhoFechado, consultaHistorico = false))
    }
}
