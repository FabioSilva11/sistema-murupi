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

    @Update
    suspend fun atualizar(categoria: Categoria)
}
