package br.com.murupi.comandas.data.model

/** Reabertura só existe para espelhos fechados sem baixa e fora do histórico de vendas. */
object PoliticaReabertura {

    fun podeReabrir(comanda: Comanda?, consultaHistorico: Boolean): Boolean =
        !consultaHistorico &&
            comanda?.status == StatusComanda.FECHADA &&
            comanda.formaPagamento == null
}
