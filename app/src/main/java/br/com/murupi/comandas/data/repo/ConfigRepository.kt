package br.com.murupi.comandas.data.repo

import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.ConfigRestaurante
import kotlinx.coroutines.flow.Flow

/** Lê e grava a configuração do estabelecimento (linha única no banco). */
class ConfigRepository(db: AppDatabase) {

    private val dao = db.configDao()

    fun observar(): Flow<ConfigRestaurante?> = dao.observar()

    suspend fun carregar(): ConfigRestaurante? = dao.buscar()

    /** Insere ou atualiza a configuração (linha única, id 1). */
    suspend fun salvar(config: ConfigRestaurante) = dao.inserir(config.copy(id = 1))
}
