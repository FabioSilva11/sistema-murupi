package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.Categoria
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categorias ORDER BY ordem")
    fun observarTodas(): Flow<List<Categoria>>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int

    @Insert
    suspend fun inserir(categoria: Categoria): Long

    @Query("DELETE FROM categorias WHERE nome IN (:nomes)")
    suspend fun excluirPorNomes(nomes: List<String>)

    @Query("SELECT id FROM categorias WHERE nome = :nome LIMIT 1")
    suspend fun buscarIdPorNome(nome: String): Long?

    @Query("SELECT * FROM categorias")
    suspend fun listarTodas(): List<Categoria>

    @Query("SELECT COUNT(*) FROM produtos WHERE categoriaId = :categoriaId")
    suspend fun contarProdutos(categoriaId: Long): Int

    @Query("SELECT MAX(ordem) FROM categorias")
    suspend fun maiorOrdem(): Int?

    @Update
    suspend fun atualizar(categoria: Categoria)

    @Query("DELETE FROM categorias WHERE id = :id AND nome NOT IN (:protegidas)")
    suspend fun excluir(id: Long, protegidas: List<String>)

    @Query("DELETE FROM categorias WHERE nome NOT IN (:protegidas) AND NOT EXISTS (SELECT 1 FROM produtos WHERE produtos.categoriaId = categorias.id)")
    suspend fun excluirVazias(protegidas: List<String>): Int

    @Query("SELECT COUNT(*) FROM categorias WHERE nome NOT IN (:protegidas) AND NOT EXISTS (SELECT 1 FROM produtos WHERE produtos.categoriaId = categorias.id)")
    suspend fun contarVazias(protegidas: List<String>): Int
}
