package br.com.murupi.comandas.data.repo

import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.GrupoProduto
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.seed.CardapioSeed
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class CardapioRepository(private val db: AppDatabase) {

    private val categoriaDao = db.categoriaDao()
    private val produtoDao = db.produtoDao()

    fun categorias(): Flow<List<Categoria>> = categoriaDao.observarTodas()

    fun produtos(): Flow<List<Produto>> = produtoDao.observarTodos()

    fun observarContagemProdutos(categoriaId: Long): Flow<Int> = produtoDao.observarContagemCategoria(categoriaId)

    suspend fun salvarProduto(produto: Produto) {
        if (produto.id == 0L) produtoDao.inserir(produto) else produtoDao.atualizar(produto)
    }

    suspend fun excluirProduto(produto: Produto) = produtoDao.excluir(produto)

    /** Salva todas as opções de uma subcategoria de uma vez, sem perder estoque das existentes. */
    suspend fun salvarGrupo(grupo: GrupoProduto, idsOriginais: Set<Long>) = db.withTransaction {
        val idsParaAtualizar = grupo.opcoes.map { it.id }.filter { it in idsOriginais }.distinct()
        val bases = if (idsParaAtualizar.isEmpty()) emptyMap() else {
            produtoDao.listarPorIds(idsParaAtualizar).associateBy { it.id }
        }
        grupo.paraProdutos(bases).forEach { produto ->
            if (produto.id == 0L) produtoDao.inserir(produto) else produtoDao.atualizar(produto)
        }
        val removidos = grupo.idsRemovidos(idsOriginais)
        if (removidos.isNotEmpty()) produtoDao.excluirPorIds(removidos.toList())
    }

    suspend fun atualizarCategoria(categoria: Categoria) = categoriaDao.atualizar(categoria)

    suspend fun salvarCategoria(categoria: Categoria) {
        if (categoria.id == 0L) {
            val ordem = (categoriaDao.maiorOrdem() ?: 0) + 1
            categoriaDao.inserir(categoria.copy(ordem = ordem))
        } else {
            categoriaDao.atualizar(categoria)
        }
    }

    /** Quantidade de produtos da categoria, para avisar antes de excluir. */
    suspend fun produtosDaCategoria(categoria: Categoria): Int = categoriaDao.contarProdutos(categoria.id)

    /** Exclui a categoria; os produtos vão junto (CASCADE) e itens já lançados não mudam. */
    suspend fun excluirCategoria(categoria: Categoria) {
        categoriaDao.excluir(categoria.id, CardapioSeed.categoriasProtegidas)
    }

    suspend fun excluirCategoriasVazias(): Int = categoriaDao.excluirVazias(CardapioSeed.categoriasProtegidas)

    suspend fun contarCategoriasVazias(): Int = categoriaDao.contarVazias(CardapioSeed.categoriasProtegidas)

    fun categoriaProtegida(categoria: Categoria): Boolean = categoria.nome in CardapioSeed.categoriasProtegidas
}
