package br.com.murupi.comandas.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.murupi.comandas.data.model.ConfigRestaurante
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Query("SELECT * FROM config_restaurante WHERE id = 1")
    fun observar(): Flow<ConfigRestaurante?>

    @Query("SELECT * FROM config_restaurante WHERE id = 1")
    suspend fun buscar(): ConfigRestaurante?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(config: ConfigRestaurante)

    @Update
    suspend fun atualizar(config: ConfigRestaurante)
}
