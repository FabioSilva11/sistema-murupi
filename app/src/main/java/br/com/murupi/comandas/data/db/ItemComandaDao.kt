package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.ItemComanda
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemComandaDao {

    @Query("SELECT * FROM itens_comanda WHERE comandaId = :comandaId ORDER BY criadoEm, id")
    fun observarDaComanda(comandaId: Long): Flow<List<ItemComanda>>

    @Query("SELECT * FROM itens_comanda WHERE comandaId = :comandaId ORDER BY criadoEm, id")
    suspend fun listarDaComanda(comandaId: Long): List<ItemComanda>

    @Query("SELECT * FROM itens_comanda WHERE id = :id")
    suspend fun buscar(id: Long): ItemComanda?

    /** Item ainda não impresso com o mesmo produto, preço e observação (para somar a quantidade). */
    @Query(
        """
        SELECT * FROM itens_comanda
        WHERE comandaId = :comandaId AND produtoId = :produtoId AND precoUnitarioCentavos = :precoCentavos
          AND observacao = :observacao AND enviadoProducao = 0
        LIMIT 1
        """
    )
    suspend fun pendenteIgual(comandaId: Long, produtoId: Long, precoCentavos: Long, observacao: String): ItemComanda?

    @Insert
    suspend fun inserir(item: ItemComanda): Long

    @Update
    suspend fun atualizar(item: ItemComanda)

    @Delete
    suspend fun excluir(item: ItemComanda)

    @Query("UPDATE itens_comanda SET enviadoProducao = 1 WHERE id IN (:ids)")
    suspend fun marcarEnviados(ids: List<Long>)

    @Query("UPDATE itens_comanda SET comandaId = :destino WHERE comandaId = :origem")
    suspend fun moverItens(origem: Long, destino: Long)
}
