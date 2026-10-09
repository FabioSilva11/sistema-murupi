package br.com.murupi.comandas.ui.produto

import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.model.Setor
import br.com.murupi.comandas.data.model.rotuloNoGrupo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MontadorLinhasTest {

    private val sucos = Categoria(id = 1, nome = "SUCOS", cor = "#00ACC1", ordem = 0, setor = Setor.SUCOS, perguntarOpcoesSuco = true)
    private val pratos = Categoria(id = 2, nome = "PRATOS MURUPI", cor = "#2E7D32", ordem = 1, setor = Setor.COZINHA)

    private fun suco(id: Long, fruta: String, tamanho: String, preco: Long, estoque: Int? = null) =
        ProdutoItem(Produto(id, sucos.id, "Suco de $fruta $tamanho", preco, grupo = fruta, estoque = estoque), sucos)

    private val graviola300 = suco(1, "Graviola", "300ml", 600)
    private val graviola500 = suco(2, "Graviola", "500ml", 1000)
    private val cupuacu300 = suco(3, "Cupuaçu", "300ml", 600)
    private val avulso = ProdutoItem(Produto(4, sucos.id, "Água de Coco", 500), sucos)
    private val ordem = Comparator<String> { a, b -> a.compareTo(b) }

    private fun montar(grupos: Set<String> = emptySet(), preparo: Long? = null, agrupar: Boolean = true) =
        MontadorLinhas.montar(
            listOf(graviola500, cupuacu300, avulso, graviola300), grupos, preparo,
            agrupar = agrupar, mostrarCategoria = false, ordemAlfabetica = ordem
        )

    @Test
    fun grupos_fechados_mostram_uma_linha_por_fruta() {
        val linhas = montar()
        assertEquals(3, linhas.size) // Cupuaçu, Graviola e Água de Coco
        val graviola = linhas.filterIsInstance<LinhaProduto.Grupo>().first { it.nome == "Graviola" }
        assertEquals(2, graviola.variantes)
        assertEquals(600L, graviola.menorPrecoCentavos)
        assertFalse(graviola.expandido)
    }

    @Test
    fun grupo_aberto_mostra_os_tamanhos_em_ordem_e_recuados() {
        val linhas = montar(grupos = setOf(LinhaProduto.chaveGrupo(sucos.id, "Graviola")))
        val itens = linhas.filterIsInstance<LinhaProduto.Item>().filter { it.recuado }
        assertEquals(listOf(graviola300, graviola500), itens.map { it.item })
    }

    @Test
    fun variantes_saem_do_menor_para_o_maior_preco() {
        val jarra750 = suco(5, "Acerola", "750ml", 1500)
        val jarra1l = suco(6, "Acerola", "1L", 2000)
        val base250 = suco(7, "Acerola", "250ml", 500)
        val linhas = MontadorLinhas.montar(
            listOf(jarra1l, base250, jarra750),
            setOf(LinhaProduto.chaveGrupo(sucos.id, "Acerola")), null,
            agrupar = true, mostrarCategoria = false, ordemAlfabetica = ordem
        )
        val itens = linhas.filterIsInstance<LinhaProduto.Item>()
        assertEquals(listOf(base250, jarra750, jarra1l), itens.map { it.item })
    }

    @Test
    fun tamanho_aberto_mostra_as_opcoes_de_preparo_logo_abaixo() {
        val linhas = montar(grupos = setOf(LinhaProduto.chaveGrupo(sucos.id, "Graviola")), preparo = graviola300.produto.id)
        val indice = linhas.indexOfFirst { it is LinhaProduto.Item && it.item == graviola300 }
        assertTrue(linhas[indice + 1] is LinhaProduto.Preparo)
        assertEquals(1, linhas.count { it is LinhaProduto.Preparo })
    }

    @Test
    fun produto_esgotado_nao_abre_preparo() {
        val esgotado = suco(9, "Goiaba", "300ml", 600, estoque = 0)
        val linhas = MontadorLinhas.montar(listOf(esgotado), emptySet(), esgotado.produto.id, false, false, ordem)
        assertEquals(1, linhas.size)
        assertFalse((linhas.first() as LinhaProduto.Item).pedePreparo)
    }

    @Test
    fun produto_de_cozinha_nao_pede_preparo() {
        val prato = ProdutoItem(Produto(10, pratos.id, "Carne de Sol", 2300), pratos)
        val linha = MontadorLinhas.montar(listOf(prato), emptySet(), prato.produto.id, true, false, ordem).single()
        assertFalse((linha as LinhaProduto.Item).pedePreparo)
    }

    @Test
    fun busca_nao_agrupa() {
        val linhas = montar(agrupar = false)
        assertEquals(4, linhas.size)
        assertTrue(linhas.all { it is LinhaProduto.Item })
    }

    @Test
    fun rotulo_curto_dentro_do_grupo() {
        assertEquals("300ml", graviola300.produto.rotuloNoGrupo())
        assertEquals("Água de Coco", avulso.produto.rotuloNoGrupo())
        assertEquals("Torta de Limão", Produto(1, 1, "Torta de Limão", 0, grupo = "Tortas").rotuloNoGrupo())
    }
}
