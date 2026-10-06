package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.Impressora
import kotlinx.coroutines.flow.Flow

@Dao
interface ImpressoraDao {

    @Query("SELECT * FROM impressoras")
    fun observarTodas(): Flow<List<Impressora>>

    @Query("SELECT * FROM impressoras WHERE ativa = 1")
    suspend fun listarAtivas(): List<Impressora>

    @Insert
    suspend fun inserir(impressora: Impressora): Long

    @Update
    suspend fun atualizar(impressora: Impressora)

    @Delete
    suspend fun excluir(impressora: Impressora)

    @Query("UPDATE impressoras SET ultimoUsoEm = :quando WHERE id = :id")
    suspend fun registrarUso(id: Long, quando: Long)
}
