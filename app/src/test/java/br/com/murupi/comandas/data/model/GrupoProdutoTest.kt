package br.com.murupi.comandas.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrupoProdutoTest {

    @Test
    fun cria_uma_variante_por_opcao_com_mesma_categoria_grupo_e_preco() {
        val grupo = GrupoProduto(
            categoriaId = 7,
            nome = "Caldos",
            descricao = "Servido quente",
            opcoes = listOf(
                OpcaoGrupoProduto(nome = "Mocotó", precoCentavos = 1000),
                OpcaoGrupoProduto(nome = "Caldo de Pinto", precoCentavos = 1500)
            )
        )

        val produtos = grupo.paraProdutos()

        assertEquals(2, produtos.size)
        assertEquals("Mocotó", produtos[0].nome)
        assertEquals(1000, produtos[0].precoCentavos)
        assertEquals(7, produtos[0].categoriaId)
        assertEquals("Caldos", produtos[0].grupo)
        assertEquals("Servido quente", produtos[0].descricao)
        assertEquals("Caldo de Pinto", produtos[1].nome)
        assertEquals(1500, produtos[1].precoCentavos)
    }

    @Test
    fun edicao_de_grupo_preserva_controle_de_estoque_da_variante_existente() {
        val produtoExistente = Produto(
            id = 11,
            categoriaId = 7,
            nome = "Suco de Acerola 300ml",
            precoCentavos = 800,
            grupo = "Sucos",
            estoque = 12,
            ativo = false,
            multiplo = 2
        )
        val grupo = GrupoProduto(
            categoriaId = 7,
            nome = "Sucos naturais",
            opcoes = listOf(OpcaoGrupoProduto(id = 11, nome = "Suco de Acerola 300ml", precoCentavos = 1000))
        )

        val produtoEditado = grupo.paraProdutos(mapOf(11L to produtoExistente)).single()

        assertEquals(12, produtoEditado.estoque)
        assertTrue(!produtoEditado.ativo)
        assertEquals(2, produtoEditado.multiplo)
        assertEquals("Sucos naturais", produtoEditado.grupo)
        assertEquals(1000, produtoEditado.precoCentavos)
    }

    @Test
    fun identifica_as_variantes_removidas_na_edicao_do_grupo() {
        val grupo = GrupoProduto(
            categoriaId = 7,
            nome = "Caldos",
            opcoes = listOf(
                OpcaoGrupoProduto(id = 3, nome = "Mocotó", precoCentavos = 1000),
                OpcaoGrupoProduto(id = 5, nome = "Feijoada", precoCentavos = 2000)
            )
        )

        assertEquals(setOf(4L), grupo.idsRemovidos(setOf(3L, 4L, 5L)))
    }
}
