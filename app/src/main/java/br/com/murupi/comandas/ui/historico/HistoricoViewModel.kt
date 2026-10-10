package br.com.murupi.comandas.ui.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.ImpressoraRepository
import br.com.murupi.comandas.print.ServicoImpressao
import br.com.murupi.comandas.ui.common.Mensagem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Totais do período para o cartão-resumo do histórico. */
data class ResumoVendas(val totalCentavos: Long, val qtd: Int) {
    val ticketMedioCentavos: Long get() = if (qtd == 0) 0L else totalCentavos / qtd
}

/** Dados da venda e produtos vendidos, usados na prévia compacta de cada linha do histórico. */
data class VendaHistorico(val resumo: ComandaResumo, val itens: List<ItemComanda>)

/** Histórico de vendas: contas fechadas, com impressão completa + saldo total. */
class HistoricoViewModel(
    private val repo: ComandaRepository,
    private val impressao: ServicoImpressao,
    private val impressoras: ImpressoraRepository
) : ViewModel() {

    val contas: StateFlow<List<VendaHistorico>> = repo.historico()
        .map { resumos ->
            if (resumos.isEmpty()) emptyList()
            else {
                val porComanda = repo.itensDasComandas(resumos.map { it.comanda.id }).groupBy { it.comandaId }
                resumos.map { resumo -> VendaHistorico(resumo, porComanda[resumo.comanda.id].orEmpty()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val resumo: StateFlow<ResumoVendas> = repo.historico()
        .map { lista -> ResumoVendas(lista.sumOf { it.totalCentavos }, lista.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumoVendas(0, 0))

    /** Total por forma de pagamento, na ordem fixa (Dinheiro, Débito, Crédito, PIX). */
    val porForma: StateFlow<List<Pair<FormaPagamento, Long>>> = contas
        .map { vendas ->
            val totais = vendas.groupBy { it.resumo.comanda.formaPagamento }
                .mapValues { (_, lista) -> lista.sumOf { it.resumo.totalCentavos } }
            FormaPagamento.entries.map { forma -> forma to (totais[forma] ?: 0L) }
        }
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

    fun excluir(comandaId: Long) {
        viewModelScope.launch { repo.excluirComanda(comandaId) }
    }
}
