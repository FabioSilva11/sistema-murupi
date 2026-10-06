package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.data.model.Setor

/**
 * Regras de qual impressora recebe cada item da produção:
 *  - SUCOS vai para a impressora de sucos;
 *  - COZINHA vai para a cozinha;
 *  - REFRIGERANTES (bebidas) sai na cozinha, abaixo dos pratos.
 * O espelho (conta) não passa por aqui: ele sempre leva todos os itens.
 */
object RoteadorImpressao {

    fun papelDoSetor(setor: Setor, papeisConfigurados: Set<PapelImpressora>): PapelImpressora = when (setor) {
        Setor.COZINHA -> PapelImpressora.COZINHA
        Setor.SUCOS -> PapelImpressora.SUCOS
        Setor.REFRIGERANTES -> PapelImpressora.COZINHA
    }

    /** Ordem no ticket da cozinha: pratos primeiro, bebidas por último. */
    fun ordemNoTicket(setor: Setor): Int = when (setor) {
        Setor.COZINHA -> 0
        else -> 1
    }

    fun separarProducao(
        itens: List<ItemComanda>,
        papeisConfigurados: Set<PapelImpressora>
    ): Map<PapelImpressora, List<ItemComanda>> =
        itens.groupBy { papelDoSetor(it.setor, papeisConfigurados) }.toSortedMap()
}
