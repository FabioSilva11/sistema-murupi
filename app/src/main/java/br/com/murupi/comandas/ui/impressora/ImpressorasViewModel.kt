package br.com.murupi.comandas.ui.impressora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.data.repo.ImpressoraRepository
import br.com.murupi.comandas.print.DescobertaImpressoras
import br.com.murupi.comandas.print.ServicoImpressao
import br.com.murupi.comandas.ui.common.Mensagem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ImpressorasViewModel(
    private val repo: ImpressoraRepository,
    private val impressao: ServicoImpressao,
    private val descoberta: DescobertaImpressoras
) : ViewModel() {

    val impressoras: StateFlow<List<Impressora>> =
        repo.impressoras().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Papéis obrigatórios ainda sem impressora ativa (refrigerantes é opcional). */
    val papeisFaltando: Flow<List<PapelImpressora>> = impressoras.map { lista ->
        val ativos = lista.filter { it.ativa }.map { it.papel }.toSet()
        PAPEIS_OBRIGATORIOS.filter { it !in ativos }
    }

    private val _eventos = Channel<Mensagem>(Channel.BUFFERED)
    val eventos: Flow<Mensagem> = _eventos.receiveAsFlow()

    fun salvar(impressora: Impressora) {
        viewModelScope.launch {
            repo.salvar(impressora)
            _eventos.send(Mensagem(R.string.impressora_salva, impressora.nome))
        }
    }

    fun excluir(impressora: Impressora) {
        viewModelScope.launch { repo.excluir(impressora) }
    }

    fun testar(impressora: Impressora) {
        viewModelScope.launch {
            val erro = impressao.imprimirTeste(impressora)
            _eventos.send(
                if (erro == null) Mensagem(R.string.teste_ok, impressora.nome)
                else Mensagem(R.string.teste_falhou, impressora.nome, erro)
            )
        }
    }

    fun varrerRede(): Flow<DescobertaImpressoras.Evento> = descoberta.varrer()

    private companion object {
        val PAPEIS_OBRIGATORIOS = listOf(PapelImpressora.ESPELHO, PapelImpressora.COZINHA, PapelImpressora.SUCOS)
    }
}
