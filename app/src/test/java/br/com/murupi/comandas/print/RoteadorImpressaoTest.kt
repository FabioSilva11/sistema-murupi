package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.data.model.PapelImpressora.COZINHA
import br.com.murupi.comandas.data.model.PapelImpressora.ESPELHO
import br.com.murupi.comandas.data.model.PapelImpressora.SUCOS
import br.com.murupi.comandas.data.model.Setor
import org.junit.Assert.assertEquals
import org.junit.Test

class RoteadorImpressaoTest {

    private val tresImpressoras = setOf(ESPELHO, COZINHA, SUCOS)

    private val isca = ItensDeTeste.isca
    private val suco = ItensDeTeste.sucoSobDemanda
    private val refri = ItensDeTeste.refrigerante

    @Test
    fun sucos_vao_so_para_a_impressora_de_sucos() {
        assertEquals(SUCOS, RoteadorImpressao.papelDoSetor(Setor.SUCOS, tresImpressoras))
    }

    @Test
    fun refrigerante_sempre_sai_na_cozinha() {
        val separado = RoteadorImpressao.separarProducao(listOf(isca, suco, refri), tresImpressoras)
        assertEquals(listOf(COZINHA, SUCOS), separado.keys.toList())
        assertEquals(listOf(isca, refri), separado[COZINHA])
        assertEquals(listOf(suco), separado[SUCOS])
    }

    @Test
    fun refrigerante_sai_abaixo_da_comida_no_ticket() {
        assertEquals(0, RoteadorImpressao.ordemNoTicket(Setor.COZINHA))
        assertEquals(1, RoteadorImpressao.ordemNoTicket(Setor.REFRIGERANTES))
    }

    @Test
    fun espelho_nunca_recebe_producao() {
        val papeis: Set<PapelImpressora> = Setor.entries
            .map { RoteadorImpressao.papelDoSetor(it, tresImpressoras) }.toSet()
        assertEquals(false, ESPELHO in papeis)
    }
}
