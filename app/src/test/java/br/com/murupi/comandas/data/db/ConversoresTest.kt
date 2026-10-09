package br.com.murupi.comandas.data.db

import br.com.murupi.comandas.data.model.PapelImpressora
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversoresTest {

    private val conversores = Conversores()

    @Test
    fun salva_papeis_ordenados_por_nome() {
        assertEquals(
            "COZINHA,SUCOS",
            conversores.papeisParaTexto(setOf(PapelImpressora.SUCOS, PapelImpressora.COZINHA))
        )
    }

    @Test
    fun conjunto_vazio_vira_texto_vazio_e_volta() {
        assertEquals("", conversores.papeisParaTexto(emptySet()))
        assertEquals(emptySet<PapelImpressora>(), conversores.textoParaPapeis(""))
        assertEquals(emptySet<PapelImpressora>(), conversores.textoParaPapeis(null))
    }

    @Test
    fun migracao_preserva_o_papel_unico_antigo() {
        // A migração copia a coluna antiga: um texto simples vira o conjunto com aquele papel.
        assertEquals(
            setOf(PapelImpressora.ESPELHO),
            conversores.textoParaPapeis("ESPELHO")
        )
    }

    @Test
    fun ignora_nomes_desconhecidos() {
        assertEquals(
            setOf(PapelImpressora.COZINHA),
            conversores.textoParaPapeis("COZINHA,REFRIGERANTES")
        )
    }
}
