package br.com.murupi.comandas.ui.comanda

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.data.model.titulo
import br.com.murupi.comandas.data.model.totalCentavos
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.EstoqueInsuficienteException
import br.com.murupi.comandas.data.repo.ResultadoCancelamentoItem
import br.com.murupi.comandas.print.ServicoImpressao
import br.com.murupi.comandas.ui.common.Mensagem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ComandaUiState(
    val carregado: Boolean = false,
    val comanda: Comanda? = null,
    val itens: List<ItemComanda> = emptyList(),
    val cancelamentosPendentes: List<ItemComanda> = emptyList()
) {
    val totalCentavos: Long get() = itens.sumOf { it.totalCentavos }
    val quantidadeItens: Int get() = itens.sumOf { it.quantidade }
    val pendentes: Int get() = itens.count { !it.enviadoProducao }
}

sealed interface ComandaEvento {
    class Aviso(val mensagem: Mensagem) : ComandaEvento
    class Impressao(@StringRes val titulo: Int, val resultado: ServicoImpressao.Resultado) : ComandaEvento
    class AbrirComanda(val id: Long) : ComandaEvento
    data object SolicitarPreviaProducao : ComandaEvento
}

/** Como a comanda deixou de existir/estar aberta, para a tela saber o que fazer ao fechar. */
sealed interface Encerramento {
    class Aviso(val mensagem: Mensagem) : Encerramento
    class Unida(val destinoId: Long) : Encerramento
}

class ComandaViewModel(
    private val comandaId: Long,
    private val repo: ComandaRepository,
    private val impressao: ServicoImpressao
) : ViewModel() {

    val estado: StateFlow<ComandaUiState> =
        combine(
            repo.observarComanda(comandaId),
            repo.observarItens(comandaId),
            repo.observarCancelamentosPendentes(comandaId)
        ) { comanda, itens, cancelamentos ->
            ComandaUiState(carregado = true, comanda = comanda, itens = itens, cancelamentosPendentes = cancelamentos)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComandaUiState())

    private val _imprimindo = MutableStateFlow(false)
    val imprimindo: StateFlow<Boolean> = _imprimindo.asStateFlow()

    private val _eventos = Channel<ComandaEvento>(Channel.BUFFERED)
    val eventos: Flow<ComandaEvento> = _eventos.receiveAsFlow()

    /** Preenchido antes de pagar/unir/liberar, e lido pela tela quando a comanda some do banco. */
    var encerramento: Encerramento? = null
        private set

    fun imprimirProducao(reimprimirTudo: Boolean) {
        val titulo = if (estado.value.cancelamentosPendentes.isNotEmpty()) {
            R.string.atualizacao_producao
        } else {
            R.string.imprimir_producao
        }
        imprimir(titulo) { comanda, itens ->
            impressao.imprimirProducao(
                comanda,
                itens,
                reimpressao = reimprimirTudo,
                cancelamentosPendentes = repo.cancelamentosPendentes(comandaId)
            )
        }
    }

    fun imprimirEspelho() {
        if (_imprimindo.value) return
        _imprimindo.value = true
        viewModelScope.launch {
            try {
                val comanda = repo.comanda(comandaId) ?: return@launch
                val resultado = impressao.imprimirEspelho(comanda, repo.itens(comandaId))
                // Espelho é apenas conferência e NÃO fecha a conta. O fechamento é feito na tela de pagamento.
                _eventos.send(ComandaEvento.Impressao(R.string.imprimir_espelho, resultado))
            } finally {
                _imprimindo.value = false
            }
        }
    }

    /** Prévia da produção para conferir na tela antes de aprovar a impressão. */
    suspend fun previaProducao(reimprimirTudo: Boolean): List<ServicoImpressao.Previa> {
        val comanda = repo.comanda(comandaId) ?: return emptyList()
        val itens = repo.itens(comandaId)
        return impressao.preverProducao(
            comanda,
            itens,
            reimprimirTudo,
            repo.cancelamentosPendentes(comandaId)
        )
    }

    /** Prévia do espelho para conferir na tela antes de aprovar a impressão. */
    suspend fun previaEspelho(): ServicoImpressao.Previa? {
        val comanda = repo.comanda(comandaId) ?: return null
        val itens = repo.itens(comandaId)
        if (itens.isEmpty()) return null
        return impressao.preverEspelho(comanda, itens)
    }

    private fun imprimir(
        @StringRes titulo: Int,
        acao: suspend (Comanda, List<ItemComanda>) -> ServicoImpressao.Resultado
    ) {
        if (_imprimindo.value) return
        _imprimindo.value = true
        viewModelScope.launch {
            try {
                val comanda = repo.comanda(comandaId) ?: return@launch
                _eventos.send(ComandaEvento.Impressao(titulo, acao(comanda, repo.itens(comandaId))))
            } finally {
                _imprimindo.value = false
            }
        }
    }

    fun atualizarItem(item: ItemComanda) {
        viewModelScope.launch {
            try {
                repo.atualizarItem(item)
            } catch (e: EstoqueInsuficienteException) {
                _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.estoque_insuficiente, e.produto, e.disponivel)))
            }
        }
    }

    fun removerItem(item: ItemComanda) {
        viewModelScope.launch {
            when (repo.cancelarItem(item)) {
                ResultadoCancelamentoItem.REMOVIDO_PENDENTE ->
                    _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.item_removido, item.nome)))
                ResultadoCancelamentoItem.CANCELADO_ENVIADO ->
                    _eventos.send(ComandaEvento.SolicitarPreviaProducao)
                ResultadoCancelamentoItem.NAO_ENCONTRADO ->
                    _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.item_nao_pode_cancelar)))
            }
        }
    }

    /** Divide o item em unidades avulsas para observar cada uma separadamente. */
    fun dividirItem(item: ItemComanda) {
        viewModelScope.launch {
            repo.dividirItem(item)
            _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.item_dividido, item.nome)))
        }
    }

    fun definirPessoas(pessoas: Int) {
        viewModelScope.launch { repo.definirPessoas(comandaId, pessoas) }
    }

    /** Comandas abertas na mesa de destino, sem contar esta. */
    suspend fun outrasComandasDaMesa(mesa: Int): List<ComandaResumo> =
        repo.resumosAbertosDaMesa(mesa).filter { it.comanda.id != comandaId }

    fun transferir(mesaDestino: Int, juntarComId: Long?) {
        viewModelScope.launch {
            if (juntarComId != null) encerramento = Encerramento.Unida(juntarComId)
            repo.transferir(comandaId, mesaDestino, juntarComId)
            if (juntarComId == null) {
                _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.comanda_transferida, mesaDestino)))
            }
        }
    }

    fun fecharComanda(formas: List<FormaPagamento>, totalCentavos: Long) {
        val comanda = estado.value.comanda ?: return
        viewModelScope.launch {
            val mesaFicaLivre = comanda.tipo == TipoComanda.MESA && outrasComandasDaMesa(comanda.mesa).isEmpty()
            encerramento = Encerramento.Aviso(
                if (mesaFicaLivre) Mensagem(R.string.comanda_paga_mesa_livre, comanda.numero, comanda.mesa)
                else Mensagem(R.string.comanda_paga, comanda.titulo)
            )
            repo.fecharComanda(comandaId, formas, totalCentavos)
        }
    }

    /** Comanda sem consumo: apaga em vez de registrar um pagamento de R$ 0,00. */
    fun liberarSemConsumo() {
        val comanda = estado.value.comanda ?: return
        viewModelScope.launch {
            encerramento = Encerramento.Aviso(Mensagem(R.string.comanda_cancelada, comanda.titulo))
            repo.excluirComanda(comandaId)
        }
    }

    /** Exclui somente pedidos que ainda não tiveram nenhum item enviado à produção. */
    fun excluirPedidoNaoImpresso() {
        val comanda = estado.value.comanda ?: return
        viewModelScope.launch {
            encerramento = Encerramento.Aviso(Mensagem(R.string.pedido_excluido, comanda.titulo))
            if (!repo.excluirPedidoNaoImpresso(comandaId)) {
                encerramento = null
                _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.pedido_nao_pode_excluir)))
            }
        }
    }

    fun reabrirComanda() {
        viewModelScope.launch {
            val titulo = estado.value.comanda?.titulo.orEmpty()
            val reabriu = repo.reabrirComanda(comandaId)
            if (reabriu) {
                _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.conta_reaberta, titulo)))
            } else {
                _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.conta_paga_nao_reabre, titulo)))
            }
        }
    }

    fun novaComandaNaMesa() {
        val mesa = estado.value.comanda?.mesa ?: return
        viewModelScope.launch { _eventos.send(ComandaEvento.AbrirComanda(repo.abrirNovaComanda(mesa).id)) }
    }
}
