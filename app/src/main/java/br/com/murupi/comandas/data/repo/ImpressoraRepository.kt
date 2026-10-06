package br.com.murupi.comandas.data.repo

import br.com.murupi.comandas.data.db.ImpressoraDao
import br.com.murupi.comandas.data.model.Impressora
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ImpressoraRepository(private val dao: ImpressoraDao) {

    fun impressoras(): Flow<List<Impressora>> = dao.observarTodas().map { lista ->
        lista.sortedWith(compareBy({ it.papel.ordinal }, { it.nome.lowercase() }))
    }

    suspend fun salvar(impressora: Impressora) {
        if (impressora.id == 0L) dao.inserir(impressora) else dao.atualizar(impressora)
    }

    suspend fun excluir(impressora: Impressora) = dao.excluir(impressora)
}
