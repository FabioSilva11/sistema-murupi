package br.com.murupi.comandas.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.repo.ComandaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MesaUi(val numero: Int, val comandas: List<ComandaResumo>) {
    val ocupada: Boolean get() = comandas.isNotEmpty()
    val totalCentavos: Long get() = comandas.sumOf { it.totalCentavos }
    val proximaSequencia: Int
        get() = generateSequence(1) { it + 1 }.first { seq -> comandas.none { it.comanda.sequencia == seq } }
}

/** Tela inicial: mesas do salão, balcão e delivery, com o mesmo fluxo de comanda. */
class InicioViewModel(private val repo: ComandaRepository) : ViewModel() {

    /** Garante que a limpeza de comandas vazias nunca apague uma comanda recém-aberta. */
    private val mutex = Mutex()

    val mesas: StateFlow<List<MesaUi>> =
        combine(repo.comandasAbertas(), Restaurante.config) { abertas, config ->
            val porMesa = abertas
                .filter { it.comanda.tipo == TipoComanda.MESA }
                .groupBy { it.comanda.mesa }
            (1..config.totalMesas).map { MesaUi(it, porMesa[it].orEmpty()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balcao: StateFlow<List<ComandaResumo>> = repo.resumosAbertosDoTipo(TipoComanda.BALCAO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val delivery: StateFlow<List<ComandaResumo>> = repo.resumosAbertosDoTipo(TipoComanda.DELIVERY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun abrirNovaComanda(mesa: Int): Comanda = mutex.withLock { repo.abrirNovaComanda(mesa) }

    suspend fun fechadasDaMesa(mesa: Int): List<ComandaResumo> = repo.resumosFechadasDaMesa(mesa)

    suspend fun abrirAvulsa(tipo: TipoComanda, nomeCliente: String, endereco: String): Comanda =
        mutex.withLock { repo.abrirComandaAvulsa(tipo, nomeCliente, endereco) }

    fun limparComandasVazias() {
        viewModelScope.launch { mutex.withLock { repo.limparComandasVazias() } }
    }
}
