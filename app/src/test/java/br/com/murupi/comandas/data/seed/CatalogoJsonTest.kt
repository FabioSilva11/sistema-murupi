package br.com.murupi.comandas.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogoJsonTest {

    private fun carregarJson(): String {
        val candidatos = listOf(
            File("src/main/assets/catalogo_padronizado.json"),
            File("app/src/main/assets/catalogo_padronizado.json")
        )
        val arquivo = candidatos.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Asset catalogo_padronizado.json não encontrado")
        return arquivo.readText(Charsets.UTF_8)
    }

    private val catalogo by lazy { parseCatalogoPadronizado(carregarJson()) }

    @Test
    fun respeita_os_totais_do_json() {
        assertEquals(15, catalogo.totalCategorias)
        assertEquals(127, catalogo.totalProdutos)
        assertEquals(390, catalogo.totalVariacoes)
        assertEquals(15, catalogo.categorias.size)
        assertEquals(127, catalogo.produtos.size)
    }

    @Test
    fun separa_simples_de_com_variacoes() {
        val simples = catalogo.produtos.filter { !it.comVariacoes }
        val comVariacoes = catalogo.produtos.filter { it.comVariacoes }
        assertEquals(39, simples.size)
        assertEquals(88, comVariacoes.size)
        assertEquals(390, comVariacoes.sumOf { it.variacoes.size })
    }

    @Test
    fun expande_para_429_itens_de_seed() {
        val itens = catalogo.expandir()
        assertEquals(429, itens.size)
    }

    @Test
    fun atualiza_precos_e_inclui_bebidas_visiveis_nas_fotos() {
        val bebidas = catalogo.expandir().filter { it.categoria == "BEBIDAS RESTAURANTE" }
            .associateBy { it.nome }
        assertEquals(200L, bebidas.getValue("Água sem Gás").precoCentavos)
        assertEquals(300L, bebidas.getValue("Água com Gás").precoCentavos)
        assertEquals(400L, bebidas.getValue("Água Tônica").precoCentavos)
        assertEquals(500L, bebidas.getValue("Coca-Cola Lata").precoCentavos)
        assertEquals(1000L, bebidas.getValue("Coca-Cola 1L").precoCentavos)
        assertEquals(1200L, bebidas.getValue("Coca-Cola 1,5L").precoCentavos)
        assertEquals(500L, bebidas.getValue("Água Saborizada").precoCentavos)
        assertEquals(500L, bebidas.getValue("Coca-Cola Zero Lata").precoCentavos)
        assertEquals(600L, bebidas.getValue("Coca-Cola Zero 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Coca-Cola Zero 1,5L").precoCentavos)
        assertEquals(600L, bebidas.getValue("Fanta Laranja 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Fanta Uva 1,5L").precoCentavos)
        assertEquals(500L, bebidas.getValue("Baré Lata").precoCentavos)
        assertEquals(600L, bebidas.getValue("Guaraná Antarctica 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Flesh Cola 1,5L").precoCentavos)
        assertEquals(600L, bebidas.getValue("Flesh Laranja 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Flesh Uva 1,5L").precoCentavos)
        assertEquals(600L, bebidas.getValue("Regente 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Tauá 1,5L").precoCentavos)
        assertEquals(600L, bebidas.getValue("Tete Cola 1L").precoCentavos)
        assertEquals(800L, bebidas.getValue("Tete Laranja 1,5L").precoCentavos)
        assertEquals(600L, bebidas.getValue("Tete Uva 1L").precoCentavos)
        assertTrue("Bebidas do restaurante não podem ficar com preço zero", bebidas.values.all { it.precoCentavos > 0 })
        assertTrue("Refrigerante Lata não deve ficar sem opções", "Refrigerante Lata" !in bebidas)
        assertTrue("Sabores 6 não deve ficar sem as opções de bebida", "Refrigerante 1L Sabores 6" !in bebidas)
        assertTrue("Sabores 8 não deve ficar sem as opções de bebida", "Refrigerante 1L Sabores 8" !in bebidas)
    }

    @Test
    fun mantem_precos_e_grupos_das_sopas() {
        val sopas = catalogo.expandir().filter { it.categoria == "SOPAS E CALDOS" }
        assertEquals(19, sopas.size)
        val jaba = sopas.filter { it.grupo == "Jabá" }.sortedBy { it.precoCentavos }
        assertEquals(listOf("Jabá 10", "Jabá 12", "Jabá 15"), jaba.map { it.nome })
        assertEquals(listOf(1000L, 1200L, 1500L), jaba.map { it.precoCentavos })
        val galinha = sopas.filter { it.grupo == "Galinha Caipira" }
        assertEquals(listOf("Galinha Caipira"), galinha.map { it.nome })
        assertEquals(1500L, galinha.single().precoCentavos)
    }

    @Test
    fun mantem_preco_unico_dos_pratos() {
        val carne = catalogo.expandir().single { it.nome == "Carne de Sol" }
        assertEquals(2300L, carne.precoCentavos)
        assertNull(carne.grupo)
        assertEquals("PRATOS MURUPI", carne.categoria)
    }

    @Test
    fun agrupa_acerola_do_suco_e_do_guarana() {
        // O id "Acerola" reúne Suco de Acerola (SUCOS) + Guaraná Acerola (GUARANÁS SIMPLES).
        val acerola = catalogo.produtos.single { it.id == "Acerola" }
        assertEquals("Suco de Acerola", acerola.nome)
        assertEquals(10, acerola.variacoes.size)
        val sucos = acerola.variacoes.filter { it.categoria == "SUCOS" }
        val guaranas = acerola.variacoes.filter { it.categoria == "GUARANÁS SIMPLES" }
        assertEquals(6, sucos.size)
        assertEquals(4, guaranas.size)
        assertEquals(
            listOf(500L, 600L, 800L, 1000L, 1500L, 2000L),
            sucos.map { it.precoCentavos }
        )
    }

    @Test
    fun sucos_tem_jarra_750ml_e_1l() {
        val sucos = catalogo.expandir().filter { it.categoria == "SUCOS" }
        val jarras750 = sucos.filter { it.nome.endsWith(" 750ml") }
        val jarras1l = sucos.filter { it.nome.endsWith(" 1L") }
        assertEquals(13, jarras750.size)
        assertEquals(13, jarras1l.size)
        assertTrue(jarras750.all { it.precoCentavos == 1500L })
        assertTrue(jarras1l.all { it.precoCentavos == 2000L })
    }

    @Test
    fun sobremesas_custam_4_reais() {
        val sobremesas = catalogo.expandir().filter { it.categoria == "SOBREMESAS" }
        assertEquals(7, sobremesas.size)
        assertTrue(sobremesas.all { it.precoCentavos == 400L })
    }

    @Test
    fun aceita_variacao_sem_tamanho() {
        val coca = catalogo.produtos.single { it.id == "Coca-Cola" }
        assertTrue(coca.variacoes.all { it.tamanho == null })
        assertEquals(3, coca.variacoes.size)
    }
}
