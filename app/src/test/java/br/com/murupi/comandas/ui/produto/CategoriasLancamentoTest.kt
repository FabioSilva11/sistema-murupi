package br.com.murupi.comandas.ui.produto

import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Setor
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriasLancamentoTest {

    @Test
    fun mostra_categoria_cadastrada_mesmo_sem_produto_ativo() {
        val sucos = Categoria(id = 1, nome = "SUCOS", cor = "#1565C0", ordem = 1, setor = Setor.SUCOS)
        val novaCategoria = Categoria(id = 2, nome = "NOVIDADES", cor = "#00897B", ordem = 2, setor = Setor.COZINHA)

        assertEquals(listOf(sucos, novaCategoria), categoriasParaLancamento(listOf(sucos, novaCategoria)))
    }
}
