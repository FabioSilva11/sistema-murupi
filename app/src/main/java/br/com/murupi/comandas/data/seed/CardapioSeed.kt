package br.com.murupi.comandas.data.seed

import androidx.room.withTransaction
import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.model.Setor

/**
 * Cardápio inicial, gravado na primeira execução. Preços em centavos; [LIVRE] (zero) faz o app
 * pedir o preço na hora do lançamento.
 */
object CardapioSeed {

    private const val LIVRE = 0L

    private fun reais(valor: Int): Long = valor * 100L

    private class ProdutoSeed(
        val nome: String,
        val preco: Long,
        val grupo: String? = null,
        val multiplo: Int = 1,
        val descricao: String = ""
    )

    private class CategoriaSeed(
        val nome: String,
        val cor: String,
        val setor: Setor = Setor.COZINHA,
        val demanda: Boolean = false,
        val perguntarOpcoesSuco: Boolean = false,
        val produtos: List<ProdutoSeed> = emptyList()
    )

    private infix fun String.custa(preco: Long) = ProdutoSeed(this, preco)

    // Tamanhos (ml -> preço): 250ml R$ 5, 300ml R$ 6, 400ml R$ 8, 500ml R$ 10.
    // Todo suco/guaraná/vitamina tem os 4 tamanhos.
    private fun tamanhosCompletos(base: String, grupo: String, descricao: String = "") = listOf(
        ProdutoSeed("$base 250ml", reais(5), grupo = grupo, descricao = descricao),
        ProdutoSeed("$base 300ml", reais(6), grupo = grupo, descricao = descricao),
        ProdutoSeed("$base 400ml", reais(8), grupo = grupo, descricao = descricao),
        ProdutoSeed("$base 500ml", reais(10), grupo = grupo, descricao = descricao)
    )

    /** Sucos simples: pergunta com leite/sem leite (exceto laranja e limão, que saem direto). */
    private val SABORES_SUCO_SIMPLES = listOf(
        "Acerola", "Abacaxi", "Cupuaçu", "Goiaba", "Graviola", "Jenipapo",
        "Manga", "Maracujá", "Taperebá", "Mamão", "Maçã", "Limão", "Laranja"
    )

    private val SUCOS = SABORES_SUCO_SIMPLES.flatMap { sabor ->
        tamanhosCompletos("Suco de $sabor", sabor, descricao = "Polpa de $sabor")
    }

    /**
     * Sucos especiais 046-060 (combinações com leite em pó).
     * Sem título, só número: a composição aparece abaixo do nome.
     */
    private val COMPOSICAO_SUCOS_ESPECIAIS = mapOf(
        "046" to "Laranja, acerola e leite em pó.",
        "047" to "Laranja, mamão e leite em pó.",
        "048" to "Laranja, beterraba e leite em pó.",
        "049" to "Laranja, banana e leite em pó.",
        "050" to "Laranja, abacaxi e leite em pó.",
        "051" to "Cupuaçu, laranja e leite em pó.",
        "052" to "Acerola, açaí e leite em pó.",
        "053" to "Aveia, maracujá e leite em pó.",
        "054" to "Genipapo, beterraba e leite em pó.",
        "055" to "Limão, laranja e leite em pó.",
        "056" to "Cenoura, maçã, laranja e leite em pó.",
        "057" to "Laranja, manga e leite em pó.",
        "058" to "Mamão, goiaba e leite em pó.",
        "059" to "Laranja, maracujá e leite em pó.",
        "060" to "Laranja, açaí e leite em pó."
    )

    private val SUCOS_ESPECIAIS = (46..60).flatMap { numero ->
        val codigo = numero.toString().padStart(3, '0')
        tamanhosCompletos(
            "Suco $codigo",
            codigo,
            descricao = COMPOSICAO_SUCOS_ESPECIAIS[codigo].orEmpty()
        )
    }

    private val SABORES_GUARANA_SIMPLES = listOf(
        "Acerola", "Açaí", "Abacate", "Abacaxi", "Banana", "Cupuaçu", "Goiaba",
        "Graviola", "Mel e Limão", "Laranja", "Manga", "Maracujá", "Aveia",
        "Castanha de Caju", "Farinha Láctea", "Granola", "Leite", "Mixto",
        "Chocolate", "Taperebá", "Chocpu"
    )

    private val GUARANAS_SIMPLES = SABORES_GUARANA_SIMPLES.flatMap { sabor ->
        tamanhosCompletos("Guaraná $sabor", sabor, descricao = "Guaraná + $sabor")
    }

    private val GUARANAS_ESPECIAIS_DEFS = listOf(
        Triple("022", "Enermestre", "Xarope de guaraná, banana, açaí, marapuama, leite em pó e guaraná em pó."),
        Triple("023", "Cris", "Xarope de guaraná, farinha láctea, catuaba, abacaxi, leite em pó e guaraná em pó."),
        Triple("024", "Gisela", "Xarope de guaraná, amendoim, chocolate, marapuama, leite em pó e guaraná em pó."),
        Triple("025", "Salada", "Xarope de guaraná, farinha láctea, banana, mamão, leite em pó e guaraná em pó."),
        Triple("026", "Cenouranja", "Xarope de guaraná, cenoura, laranja, catuaba, leite em pó e guaraná em pó."),
        Triple("027", "Aceranja", "Xarope de guaraná, acerola, laranja, marapuama, leite em pó e guaraná em pó.")
    )

    private val GUARANAS_ESPECIAIS = GUARANAS_ESPECIAIS_DEFS.flatMap { (codigo, nome, composicao) ->
        val grupo = "$codigo-$nome"
        tamanhosCompletos("Guaraná $grupo", grupo, descricao = composicao)
    }

    private val GUARANAS_SUPER_DEFS = listOf(
        Triple("028", "Turbomestre", "Xarope de guaraná, catuaba, mirantã, marapuama, granola, amendoim, ovo de codorna, castanha de caju, açaí, leite em pó e guaraná em pó."),
        Triple("029", "Overdose", "Xarope de guaraná, catuaba, mirantã, marapuama, granola, ovo de codorna, banana, açaí e guaraná em pó."),
        Triple("030", "Embananado", "Xarope de guaraná, catuaba, mirantã, amendoim, aveia, ovo de codorna, castanha de caju, banana, leite em pó e guaraná em pó."),
        Triple("031", "Completo", "Xarope de guaraná, catuaba, mirantã, marapuama, granola, amendoim, aveia, ovo de codorna, castanha de caju, banana, açaí, leite em pó e guaraná em pó."),
        Triple("032", "Fulminante", "Xarope de guaraná, catuaba, mirantã, marapuama, farinha láctea, abacate, amendoim, leite em pó e guaraná em pó."),
        Triple("033", "Pedreira", "Xarope de guaraná, catuaba, mirantã, marapuama, beterraba, aveia, ovo de codorna, banana, açaí, leite em pó e guaraná em pó.")
    )

    private val GUARANAS_SUPER = GUARANAS_SUPER_DEFS.flatMap { (codigo, nome, composicao) ->
        val grupo = "$codigo-$nome"
        tamanhosCompletos("Guaraná $grupo", grupo, descricao = composicao)
    }

    private val VITAMINAS_SIMPLES_COMBOS = listOf(
        "Banana, aveia e leite em pó",
        "Abacate, farinha láctea e leite em pó",
        "Mamão, neston e leite em pó",
        "Maçã, neston e leite em pó"
    )

    private val VITAMINAS_SIMPLES = VITAMINAS_SIMPLES_COMBOS.flatMap { combo ->
        tamanhosCompletos("Vitamina $combo", combo)
    }

    private val VITAMINAS_ESPECIAIS_COMBOS = listOf(
        "065 - Banana, neston, aveia, abacate e leite em pó",
        "066 - Abacate, aveia, mamão, neston e leite em pó",
        "067 - Mamão, farinha láctea, aveia, banana, leite em pó",
        "068 - Maçã, farinha láctea, mamão, neston e leite em pó",
        "069 - Beterraba, aveia, banana, neston e leite em pó",
        "070 - Maçã, banana, aveia, farinha láctea e leite em pó"
    )

    private val VITAMINAS_ESPECIAIS = VITAMINAS_ESPECIAIS_COMBOS.flatMap { combo ->
        tamanhosCompletos("Vitamina $combo", combo)
    }

    private val SUPER_VITAMINADAS_COMBOS = listOf(
        "Banana, neston, abacate, mamão, aveia, farinha láctea e leite em pó",
        "Abacate, banana, mamão, mel de abelha e leite em pó",
        "Mamão, neston, banana, farinha láctea, aveia, maçã e leite em pó",
        "Açaí, neston, cenoura, beterraba, laranja e leite em pó"
    )

    private val SUPER_VITAMINADAS = SUPER_VITAMINADAS_COMBOS.flatMap { combo ->
        tamanhosCompletos("Super Vitamina $combo", combo)
    }

    /** Sabores de refrigerante; cada um em lata, 1L e 1,5L (preço livre, digitado no lançamento). */
    private val SABORES_REFRIGERANTE = listOf(
        "Coca-Cola", "Coca-Cola Zero", "Magistral", "Fanta Uva", "Fanta Laranja"
    )

    private val REFRIGERANTES = SABORES_REFRIGERANTE.flatMap { sabor ->
        listOf(
            ProdutoSeed("$sabor Lata", LIVRE, grupo = sabor),
            ProdutoSeed("$sabor 1L", LIVRE, grupo = sabor),
            ProdutoSeed("$sabor 1,5L", LIVRE, grupo = sabor)
        )
    }

    private val BEBIDAS_RESTAURANTE = listOf(
        "Água" custa LIVRE,
        "Água com Gás" custa LIVRE,
        "Água Tônica" custa LIVRE
    ) + REFRIGERANTES

    private val SOPAS_E_CALDOS = listOf("Mocotó", "Caldo de Pinto", "Feijoada").flatMap { prato ->
        listOf(10, 15, 20).map { preco ->
            ProdutoSeed("$prato $preco", reais(preco), grupo = prato)
        }
    }

    private val CATEGORIAS = listOf(
        CategoriaSeed(
            "BEBIDAS RESTAURANTE", "#3949AB", Setor.REFRIGERANTES,
            produtos = BEBIDAS_RESTAURANTE
        ),
        CategoriaSeed(
            "ISCAS", "#EF6C00",
            produtos = listOf(
                "Isca de Carne" custa reais(20),
                "Isca de Frango" custa reais(16),
                "Isca Mista" custa reais(20)
            )
        ),
        CategoriaSeed(
            "PRATOS MURUPI", "#2E7D32",
            produtos = listOf(
                "Carne de Sol" custa reais(23),
                "Strogonoff Carne" custa reais(20),
                "Strogonoff Frango" custa reais(18),
                "Creme Camarão" custa reais(20),
                "Macarrão ao Molho de Camarão" custa reais(20),
                "Lasanha de Carne" custa reais(20),
                "Lasanha de Frango" custa reais(20),
                "Farofa de Jabá com Banana" custa reais(20),
                "Farofa de Carne Seca" custa reais(20),
                "Picanha Suína" custa reais(25),
                "Picanha Chapeada" custa reais(40),
                "Frango Chapeado" custa reais(16),
                "Bife Acebolado" custa reais(20),
                "Bife a Cavalo" custa reais(22),
                "Costela Desfiada" custa reais(20),
                "Fricassê" custa reais(20)
            )
        ),
        CategoriaSeed("SOPAS E CALDOS", "#00897B", produtos = SOPAS_E_CALDOS),
        CategoriaSeed("SUCOS", "#00ACC1", Setor.SUCOS, perguntarOpcoesSuco = true, produtos = SUCOS),
        CategoriaSeed("SUCOS ESPECIAIS", "#0277BD", Setor.SUCOS, produtos = SUCOS_ESPECIAIS),
        CategoriaSeed(
            "ENTRADAS", "#827717",
            // R$ 1,00 a unidade, mas só vendidos de 5 em 5.
            produtos = listOf(
                ProdutoSeed("Bolinho de Pirarucu", reais(1), multiplo = 5),
                ProdutoSeed("Dadinho de Tapioca", reais(1), multiplo = 5),
                ProdutoSeed("Bolinha de Queijo", reais(1), multiplo = 5)
            )
        ),
        CategoriaSeed(
            "PORÇÕES EXTRAS", "#FF7043",
            // Prefixo "Extra" para a cozinha não confundir com os pratos de mesmo nome.
            produtos = listOf(
                "Extra Lasanha" custa reais(15),
                "Extra Batata" custa reais(5),
                "Extra Farofa de Carne Seca" custa reais(15),
                "Extra Farofa de Jabá" custa reais(15),
                "Extra Maionese" custa reais(5),
                "Extra Feijão Tropeiro" custa reais(5)
            )
        ),
        CategoriaSeed(
            "SOBREMESAS", "#8E24AA",
            produtos = listOf(
                "Torta de Maracujá" custa LIVRE,
                "Torta de Cupuaçu" custa LIVRE,
                "Torta de Limão" custa LIVRE,
                "Torta Sonho de Valsa" custa LIVRE,
                "Torta de Prestígio" custa LIVRE,
                "Torta de Chocolate" custa LIVRE,
                "Pudim" custa LIVRE
            )
        ),
        CategoriaSeed("GUARANÁS SIMPLES", "#C62828", Setor.SUCOS, produtos = GUARANAS_SIMPLES),
        CategoriaSeed("GUARANÁS ESPECIAIS", "#AD1457", Setor.SUCOS, produtos = GUARANAS_ESPECIAIS),
        CategoriaSeed("GUARANÁS SUPER ESPECIAIS", "#6A1B9A", Setor.SUCOS, produtos = GUARANAS_SUPER),
        CategoriaSeed("VITAMINAS SIMPLES", "#F06292", Setor.SUCOS, produtos = VITAMINAS_SIMPLES),
        CategoriaSeed("VITAMINAS ESPECIAIS", "#EC407A", Setor.SUCOS, produtos = VITAMINAS_ESPECIAIS),
        CategoriaSeed("SUPER VITAMINADAS", "#AD1457", Setor.SUCOS, produtos = SUPER_VITAMINADAS)
    )

    /** Categorias base exibidas no lançamento: podem ser editadas, mas não removidas. */
    val categoriasProtegidas: List<String> get() = CATEGORIAS.map { it.nome }

    /** Categorias extintas: removidas do seed e apagadas de bancos já instalados. */
    private val CATEGORIAS_REMOVIDAS = listOf(
        "DIVERSOS",
        "BEBIDAS LANCHONETE",
        "DOCES LANCHONETE",
        "SUCOS SOB DEMANDA",
        "DEMANDA",
        "GUARANAS",
        "VITAMINAS"
    )

    /** Bebidas do cardápio atual, por categoria: cria a categoria se faltar e completa os produtos. */
    private fun mapaBebidas(): Map<String, CategoriaSeed> {
        val mapa = mutableMapOf<String, CategoriaSeed>()
        CATEGORIAS.forEach { seed ->
            when (seed.nome) {
                "BEBIDAS RESTAURANTE",
                "SUCOS", "SUCOS ESPECIAIS", "SOPAS E CALDOS",
                "GUARANÁS SIMPLES", "GUARANÁS ESPECIAIS", "GUARANÁS SUPER ESPECIAIS",
                "VITAMINAS SIMPLES", "VITAMINAS ESPECIAIS", "SUPER VITAMINADAS" -> mapa[seed.nome] = seed
            }
        }
        return mapa
    }

    suspend fun popularSeVazio(db: AppDatabase) {
        db.withTransaction {
            // Limpa categorias extintas em instalações antigas (CASCADE apaga os produtos).
            db.categoriaDao().excluirPorNomes(CATEGORIAS_REMOVIDAS)
            if (db.categoriaDao().contar() > 0) {
                garantirCatalogoBebidas(db)
                return@withTransaction
            }
            CATEGORIAS.forEachIndexed { ordem, seed ->
                val categoriaId = db.categoriaDao().inserir(
                    Categoria(
                        nome = seed.nome,
                        cor = seed.cor,
                        ordem = ordem,
                        setor = seed.setor,
                        demanda = seed.demanda,
                        perguntarOpcoesSuco = seed.perguntarOpcoesSuco
                    )
                )
                db.produtoDao().inserirTodos(
                    seed.produtos.map {
                        Produto(
                            categoriaId = categoriaId,
                            nome = it.nome,
                            precoCentavos = it.preco,
                            grupo = it.grupo,
                            multiplo = it.multiplo,
                            descricao = it.descricao
                        )
                    }
                )
            }
        }
    }

    /**
     * Bancos antigos: cria as categorias de bebidas que faltam, insere os produtos
     * novos (tamanhos e sabores) e preenche a composição de quem estava sem.
     * Remove itens provisórios substituídos pelo cardápio completo.
     */
    private suspend fun garantirCatalogoBebidas(db: AppDatabase) {
        val categoriaDao = db.categoriaDao()
        val produtoDao = db.produtoDao()
        var proximaOrdem = (categoriaDao.listarTodas().maxOfOrNull { it.ordem } ?: 0) + 1
        mapaBebidas().forEach { (nome, seed) ->
            var categoriaId = categoriaDao.buscarIdPorNome(nome)
            if (categoriaId == null) {
                categoriaId = categoriaDao.inserir(
                    Categoria(
                        nome = seed.nome,
                        cor = seed.cor,
                        ordem = proximaOrdem++,
                        setor = seed.setor,
                        demanda = seed.demanda,
                        perguntarOpcoesSuco = seed.perguntarOpcoesSuco
                    )
                )
            }
            if (nome == "SUCOS") {
                produtoDao.excluirPorNomes(categoriaId, listOf("Abacatada 300ml", "Abacatada 500ml"))
            }
            if (nome == "BEBIDAS RESTAURANTE") {
                produtoDao.excluirPorNomes(categoriaId, listOf("Refrigerante Lata", "Refrigerante 1L"))
            }
            // Renomeia provisórios antigos ("Suco Especial 046") para o padrão atual ("Suco 046").
            if (nome == "SUCOS ESPECIAIS") {
                produtoDao.listarPorCategoria(categoriaId)
                    .filter { it.nome.startsWith("Suco Especial ") }
                    .forEach { antigo ->
                        produtoDao.atualizar(antigo.copy(nome = antigo.nome.replace("Suco Especial ", "Suco ")))
                    }
            }
            val existentes = produtoDao.listarPorCategoria(categoriaId).map { it.nome }.toSet()
            val faltantes = seed.produtos.filter { it.nome !in existentes }
            if (faltantes.isNotEmpty()) {
                produtoDao.inserirTodos(
                    faltantes.map {
                        Produto(
                            categoriaId = categoriaId,
                            nome = it.nome,
                            precoCentavos = it.preco,
                            grupo = it.grupo,
                            multiplo = it.multiplo,
                            descricao = it.descricao
                        )
                    }
                )
            }
            // Preenche a composição de produtos antigos que estavam sem.
            seed.produtos
                .filter { it.descricao.isNotEmpty() && it.grupo != null }
                .distinctBy { it.grupo }
                .forEach { produtoDao.preencherDescricaoVazia(categoriaId, it.grupo!!, it.descricao) }
        }
    }
}
