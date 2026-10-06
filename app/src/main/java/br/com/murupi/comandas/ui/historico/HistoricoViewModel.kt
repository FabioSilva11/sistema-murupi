package br.com.murupi.comandas.ui.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.ImpressoraRepository
import br.com.murupi.comandas.print.ServicoImpressao
import br.com.murupi.comandas.ui.common.Mensagem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Histórico de vendas: contas fechadas, com impressão completa + saldo total. */
class HistoricoViewModel(
    private val repo: ComandaRepository,
    private val impressao: ServicoImpressao,
    private val impressoras: ImpressoraRepository
) : ViewModel() {

    val contas: StateFlow<List<ComandaResumo>> = repo.historico()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _eventos = Channel<Mensagem>(Channel.BUFFERED)
    val eventos: Flow<Mensagem> = _eventos.receiveAsFlow()

    suspend fun impressorasAtivas(): List<Impressora> =
        impressoras.impressoras().first().filter { it.ativa }

    fun imprimirHistorico(impressora: Impressora) {
        viewModelScope.launch {
            val resumos = repo.historico().first()
            if (resumos.isEmpty()) {
                _eventos.send(Mensagem(R.string.nada_para_imprimir))
                return@launch
            }
            val contas = resumos.map { it.comanda to repo.itens(it.comanda.id) }
            val erro = impressao.imprimirHistorico(impressora, contas)
            _eventos.send(
                if (erro == null) Mensagem(R.string.historico_impresso, impressora.nome)
                else Mensagem(R.string.historico_falhou, impressora.nome, erro)
            )
        }
    }
}
