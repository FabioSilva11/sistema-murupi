package br.com.murupi.comandas.data.repo

import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import kotlinx.coroutines.flow.Flow

class CardapioRepository(db: AppDatabase) {

    private val categoriaDao = db.categoriaDao()
    private val produtoDao = db.produtoDao()

    fun categorias(): Flow<List<Categoria>> = categoriaDao.observarTodas()

    fun produtos(): Flow<List<Produto>> = produtoDao.observarTodos()

    suspend fun salvarProduto(produto: Produto) {
        if (produto.id == 0L) produtoDao.inserir(produto) else produtoDao.atualizar(produto)
    }

    suspend fun excluirProduto(produto: Produto) = produtoDao.excluir(produto)

    suspend fun atualizarCategoria(categoria: Categoria) = categoriaDao.atualizar(categoria)
}
