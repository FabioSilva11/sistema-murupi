package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.TipoComanda
import kotlinx.coroutines.flow.Flow

@Dao
interface ComandaDao {

    @Query(
        """
        SELECT c.*, COALESCE(SUM(i.quantidade * i.precoUnitarioCentavos), 0) AS totalCentavos,
               COALESCE(SUM(i.quantidade), 0) AS qtdItens
        FROM comandas c LEFT JOIN itens_comanda i ON i.comandaId = c.id AND i.cancelado = 0
        WHERE c.status = 'ABERTA'
        GROUP BY c.id
        ORDER BY c.mesa, c.sequencia
        """
    )
    fun observarAbertas(): Flow<List<ComandaResumo>>

    @Query(
        """
        SELECT c.*, COALESCE(SUM(i.quantidade * i.precoUnitarioCentavos), 0) AS totalCentavos,
               COALESCE(SUM(i.quantidade), 0) AS qtdItens
        FROM comandas c LEFT JOIN itens_comanda i ON i.comandaId = c.id AND i.cancelado = 0
        WHERE c.status = 'ABERTA' AND c.mesa = :mesa
        GROUP BY c.id
        ORDER BY c.sequencia
        """
    )
    suspend fun resumosAbertosDaMesa(mesa: Int): List<ComandaResumo>

    @Query(
        """
        SELECT c.*, COALESCE(SUM(i.quantidade * i.precoUnitarioCentavos), 0) AS totalCentavos,
               COALESCE(SUM(i.quantidade), 0) AS qtdItens
        FROM comandas c LEFT JOIN itens_comanda i ON i.comandaId = c.id AND i.cancelado = 0
        WHERE c.tipo = :tipo AND (c.status = 'ABERTA' OR (c.status = 'FECHADA' AND c.formaPagamento IS NULL))
        GROUP BY c.id
        ORDER BY c.abertaEm, c.id
        """
    )
    fun observarAbertasDoTipo(tipo: TipoComanda): Flow<List<ComandaResumo>>

    @Query("SELECT * FROM comandas WHERE id = :id")
    fun observar(id: Long): Flow<Comanda?>

    @Query("SELECT * FROM comandas WHERE id = :id")
    suspend fun buscar(id: Long): Comanda?

    @Query("SELECT * FROM comandas WHERE status = 'ABERTA'")
    suspend fun listarAbertas(): List<Comanda>

    @Query("SELECT * FROM comandas WHERE mesa = :mesa AND status = 'ABERTA' ORDER BY sequencia")
    suspend fun abertasDaMesa(mesa: Int): List<Comanda>

    @Query("SELECT * FROM comandas WHERE tipo = :tipo AND mesa = :mesa AND status = 'ABERTA' ORDER BY sequencia")
    suspend fun abertasDoTipo(tipo: TipoComanda, mesa: Int): List<Comanda>

    /**
     * Últimas contas fechadas da mesa, para mostrar em vermelho no diálogo
     * depois que o espelho libera a mesa.
     */
    @Query(
        """
        SELECT c.*, COALESCE(SUM(i.quantidade * i.precoUnitarioCentavos), 0) AS totalCentavos,
               COALESCE(SUM(i.quantidade), 0) AS qtdItens
        FROM comandas c LEFT JOIN itens_comanda i ON i.comandaId = c.id AND i.cancelado = 0
        WHERE c.status = 'FECHADA' AND c.formaPagamento IS NULL AND c.tipo = 'MESA' AND c.mesa = :mesa
        GROUP BY c.id
        ORDER BY c.fechadaEm DESC, c.id DESC
        LIMIT :limite
        """
    )
    suspend fun resumosFechadasDaMesa(mesa: Int, limite: Int): List<ComandaResumo>

    /** Histórico de vendas: todas as contas fechadas, das mais recentes às mais antigas. */
    @Query(
        """
        SELECT c.*, COALESCE(c.totalPagoCentavos, SUM(i.quantidade * i.precoUnitarioCentavos), 0) AS totalCentavos,
               COALESCE(SUM(i.quantidade), 0) AS qtdItens
        FROM comandas c LEFT JOIN itens_comanda i ON i.comandaId = c.id AND i.cancelado = 0
        WHERE c.status = 'FECHADA' AND c.formaPagamento IS NOT NULL
        GROUP BY c.id
        ORDER BY c.fechadaEm DESC, c.id DESC
        """
    )
    fun observarFechadas(): Flow<List<ComandaResumo>>

    @Insert
    suspend fun inserir(comanda: Comanda): Long

    @Update
    suspend fun atualizar(comanda: Comanda)

    @Delete
    suspend fun excluir(comanda: Comanda)

    @Query("DELETE FROM comandas")
    suspend fun excluirTodas(): Int

    /** Comandas abertas por engano (sem itens, sem pessoas e sem cliente) são apagadas. */
    @Query(
        """
        DELETE FROM comandas
        WHERE status = 'ABERTA' AND pessoas = 0 AND nomeCliente = ''
          AND id NOT IN (SELECT comandaId FROM itens_comanda)
        """
    )
    suspend fun excluirAbertasVazias(): Int
}
