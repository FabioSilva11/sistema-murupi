package br.com.murupi.comandas.data.seed

import androidx.room.withTransaction
import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.model.Setor

/**
 * Cardápio inicial, vindo do JSON padronizado em assets/catalogo_padronizado.json.
 * Preços em centavos vêm das chaves precoCentavos do JSON (preco em reais é só
 * exibição); nenhum preço é inventado aqui.
 *
 * Simples viram 1 produto; produtos com variações viram 1 produto por variação,
 * todos com o mesmo grupo (ex.: Suco de Acerola -> 250ml/300ml/400ml/500ml).
 */
object CardapioSeed {

    private class ProdutoSeed(
        val nome: String,
        val preco: Long,
        val grupo: String? = null,
        val multiplo: Int = 1,
        val descricao: String = "",
        val estoque: Int? = null,
        val ativo: Boolean = true
    )

    private class CategoriaMeta(
        val nome: String,
        val cor: String,
        val setor: Setor = Setor.COZINHA,
        val demanda: Boolean = false,
        val perguntarOpcoesSuco: Boolean = false
    )

    /** Ordem, cor, setor e comportamento de cada categoria (o JSON traz só nome e produtos). */
    private val CATEGORIA_METAS = listOf(
        CategoriaMeta("BEBIDAS RESTAURANTE", "#3949AB", Setor.REFRIGERANTES),
        CategoriaMeta("ISCAS", "#EF6C00"),
        CategoriaMeta("PRATOS MURUPI", "#2E7D32"),
        CategoriaMeta("SOPAS E CALDOS", "#00897B"),
        CategoriaMeta("SUCOS", "#00ACC1", Setor.SUCOS, perguntarOpcoesSuco = true),
        CategoriaMeta("SUCOS ESPECIAIS", "#0277BD", Setor.SUCOS),
        CategoriaMeta("ENTRADAS", "#827717"),
        CategoriaMeta("PORÇÕES EXTRAS", "#FF7043"),
        CategoriaMeta("SOBREMESAS", "#8E24AA"),
        CategoriaMeta("GUARANÁS SIMPLES", "#C62828", Setor.SUCOS),
        CategoriaMeta("GUARANÁS ESPECIAIS", "#AD1457", Setor.SUCOS),
        CategoriaMeta("GUARANÁS SUPER ESPECIAIS", "#6A1B9A", Setor.SUCOS),
        CategoriaMeta("VITAMINAS SIMPLES", "#F06292", Setor.SUCOS),
        CategoriaMeta("VITAMINAS ESPECIAIS", "#EC407A", Setor.SUCOS),
        CategoriaMeta("SUPER VITAMINADAS", "#AD1457", Setor.SUCOS)
    )

    /** Categorias base exibidas no lançamento: podem ser editadas, mas não removidas. */
    val categoriasProtegidas: List<String> get() = CATEGORIA_METAS.map { it.nome }

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

    private fun ItemSeed.paraProdutoSeed() = ProdutoSeed(
        nome = nome,
        preco = precoCentavos,
        grupo = grupo,
        multiplo = multiplo,
        descricao = descricao,
        estoque = estoque,
        ativo = ativo
    )

    private fun ItemSeed.paraProduto(categoriaId: Long) = Produto(
        categoriaId = categoriaId,
        nome = nome,
        precoCentavos = precoCentavos,
        grupo = grupo,
        estoque = estoque,
        ativo = ativo,
        multiplo = multiplo,
        descricao = descricao
    )

    suspend fun popularSeVazio(db: AppDatabase, catalogoJson: String) {
        val catalogo = try {
            parseCatalogoPadronizado(catalogoJson)
        } catch (e: Exception) {
            // JSON inválido: não mexe no banco para não apagar o cardápio existente.
            return
        }
        val itens = catalogo.expandir()
        db.withTransaction {
            // Limpa categorias extintas em instalações antigas (CASCADE apaga os produtos).
            db.categoriaDao().excluirPorNomes(CATEGORIAS_REMOVIDAS)
            if (db.categoriaDao().contar() > 0) {
                garantirCatalogo(itens, db)
                return@withTransaction
            }
            val porCategoria = itens.groupBy { it.categoria }
            val ordem = (CATEGORIA_METAS.map { it.nome } + porCategoria.keys).distinct()
            ordem.forEachIndexed { indice, nomeCategoria ->
                val meta = CATEGORIA_METAS.firstOrNull { it.nome == nomeCategoria }
                val categoriaId = db.categoriaDao().inserir(
                    Categoria(
                        nome = nomeCategoria,
                        cor = meta?.cor ?: "#616161",
                        ordem = indice,
                        setor = meta?.setor ?: Setor.COZINHA,
                        demanda = meta?.demanda ?: false,
                        perguntarOpcoesSuco = meta?.perguntarOpcoesSuco ?: false
                    )
                )
                val produtos = porCategoria[nomeCategoria].orEmpty()
                if (produtos.isNotEmpty()) {
                    db.produtoDao().inserirTodos(produtos.map { it.paraProduto(categoriaId) })
                }
            }
        }
    }

    /**
     * Bancos antigos: cria as categorias que faltam, insere os produtos novos
     * e preenche a composição de quem estava sem. Nas sopas, remove as opções
     * obsoletas para valer o cardápio atual. Itens já lançados não mudam.
     */
    private suspend fun garantirCatalogo(itens: List<ItemSeed>, db: AppDatabase) {
        val categoriaDao = db.categoriaDao()
        val produtoDao = db.produtoDao()
        var proximaOrdem = (categoriaDao.listarTodas().maxOfOrNull { it.ordem } ?: 0) + 1
        val porCategoria = itens.groupBy { it.categoria }
        porCategoria.forEach { (nome, seeds) ->
            var categoriaId = categoriaDao.buscarIdPorNome(nome)
            if (categoriaId == null) {
                val meta = CATEGORIA_METAS.firstOrNull { it.nome == nome }
                categoriaId = categoriaDao.inserir(
                    Categoria(
                        nome = nome,
                        cor = meta?.cor ?: "#616161",
                        ordem = proximaOrdem++,
                        setor = meta?.setor ?: Setor.COZINHA,
                        demanda = meta?.demanda ?: false,
                        perguntarOpcoesSuco = meta?.perguntarOpcoesSuco ?: false
                    )
                )
            }
            if (nome == "SUCOS") {
                produtoDao.excluirPorNomes(categoriaId, listOf("Abacatada 300ml", "Abacatada 500ml"))
            }
            if (nome == "BEBIDAS RESTAURANTE") {
                produtoDao.excluirPorNomes(categoriaId, listOf("Refrigerante 1L"))
                produtoDao.excluirPorNomes(
                    categoriaId,
                    listOf(
                        "Refrigerante Lata",
                        "Refrigerante 1L Sabores 6",
                        "Refrigerante 1L Sabores 8"
                    )
                )
                val produtosAtuais = produtoDao.listarPorCategoria(categoriaId)
                seeds.forEach { seed ->
                    val existente = produtosAtuais.firstOrNull { it.nome == seed.nome }
                        ?: if (seed.nome == "Água sem Gás") produtosAtuais.firstOrNull { it.nome == "Água" } else null
                    if (existente != null) {
                        val atualizado = existente.copy(
                            nome = seed.nome,
                            grupo = if (existente.grupo.isNullOrBlank() && !seed.grupo.isNullOrBlank()) {
                                seed.grupo
                            } else {
                                existente.grupo
                            },
                            precoCentavos = if (existente.precoCentavos <= 0 && seed.precoCentavos > 0) {
                                seed.precoCentavos
                            } else {
                                existente.precoCentavos
                            }
                        )
                        if (atualizado != existente) produtoDao.atualizar(atualizado)
                    }
                }
            }
            if (nome == "SOPAS E CALDOS") {
                val novos = seeds.map { it.nome }.toSet()
                val obsoletas = produtoDao.listarPorCategoria(categoriaId)
                    .map { it.nome }
                    .filter { it !in novos }
                if (obsoletas.isNotEmpty()) produtoDao.excluirPorNomes(categoriaId, obsoletas)
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
            val faltantes = seeds.filter { it.nome !in existentes }
            if (faltantes.isNotEmpty()) {
                produtoDao.inserirTodos(faltantes.map { it.paraProduto(categoriaId) })
            }
            // Preenche a composição de produtos antigos que estavam sem.
            seeds.filter { it.descricao.isNotEmpty() && it.grupo != null }
                .distinctBy { it.grupo }
                .forEach { produtoDao.preencherDescricaoVazia(categoriaId, it.grupo!!, it.descricao) }
        }
    }
}
