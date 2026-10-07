package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.Produto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {

    /** O cardápio é pequeno: a filtragem por categoria e a busca são feitas em memória. */
    @Query("SELECT * FROM produtos")
    fun observarTodos(): Flow<List<Produto>>

    @Query("SELECT COUNT(*) FROM produtos WHERE categoriaId = :categoriaId")
    fun observarContagemCategoria(categoriaId: Long): Flow<Int>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun buscar(id: Long): Produto?

    @Query("SELECT * FROM produtos WHERE id IN (:ids)")
    suspend fun listarPorIds(ids: List<Long>): List<Produto>

    @Query("SELECT * FROM produtos WHERE categoriaId = :categoriaId")
    suspend fun listarPorCategoria(categoriaId: Long): List<Produto>

    @Query("DELETE FROM produtos WHERE categoriaId = :categoriaId AND nome IN (:nomes)")
    suspend fun excluirPorNomes(categoriaId: Long, nomes: List<String>)

    @Query("DELETE FROM produtos WHERE id IN (:ids)")
    suspend fun excluirPorIds(ids: List<Long>)

    @Query("UPDATE produtos SET descricao = :descricao WHERE categoriaId = :categoriaId AND grupo = :grupo AND (descricao IS NULL OR descricao = '')")
    suspend fun preencherDescricaoVazia(categoriaId: Long, grupo: String, descricao: String)

    @Insert
    suspend fun inserir(produto: Produto): Long

    @Insert
    suspend fun inserirTodos(produtos: List<Produto>)

    @Update
    suspend fun atualizar(produto: Produto)

    @Delete
    suspend fun excluir(produto: Produto)
}
