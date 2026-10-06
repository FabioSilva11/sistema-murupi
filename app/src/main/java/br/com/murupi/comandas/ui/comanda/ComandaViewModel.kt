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
    val itens: List<ItemComanda> = emptyList()
) {
    val totalCentavos: Long get() = itens.sumOf { it.totalCentavos }
    val quantidadeItens: Int get() = itens.sumOf { it.quantidade }
    val pendentes: Int get() = itens.count { !it.enviadoProducao }
}

sealed interface ComandaEvento {
    class Aviso(val mensagem: Mensagem) : ComandaEvento
    class Impressao(@StringRes val titulo: Int, val resultado: ServicoImpressao.Resultado) : ComandaEvento
    class AbrirComanda(val id: Long) : ComandaEvento
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
        combine(repo.observarComanda(comandaId), repo.observarItens(comandaId)) { comanda, itens ->
            ComandaUiState(carregado = true, comanda = comanda, itens = itens)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComandaUiState())

    private val _imprimindo = MutableStateFlow(false)
    val imprimindo: StateFlow<Boolean> = _imprimindo.asStateFlow()

    private val _eventos = Channel<ComandaEvento>(Channel.BUFFERED)
    val eventos: Flow<ComandaEvento> = _eventos.receiveAsFlow()

    /** Preenchido antes de pagar/unir/liberar, e lido pela tela quando a comanda some do banco. */
    var encerramento: Encerramento? = null
        private set

    fun imprimirProducao(reimprimirTudo: Boolean) = imprimir(R.string.imprimir_producao) { comanda, itens ->
        val alvo = if (reimprimirTudo) itens else itens.filterNot { it.enviadoProducao }
        impressao.imprimirProducao(comanda, alvo, reimpressao = reimprimirTudo)
    }

    fun imprimirEspelho() {
        if (_imprimindo.value) return
        _imprimindo.value = true
        viewModelScope.launch {
            try {
                val comanda = repo.comanda(comandaId) ?: return@launch
                val resultado = impressao.imprimirEspelho(comanda, repo.itens(comandaId))
                // Espelho impresso fecha a conta e libera a mesa na hora.
                if (resultado.sucesso) repo.fecharContaImpressa(comandaId)
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
        val alvo = if (reimprimirTudo) itens else itens.filterNot { it.enviadoProducao }
        return impressao.preverProducao(comanda, alvo, reimprimirTudo)
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
            repo.excluirItem(item)
            _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.item_removido, item.nome)))
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

    fun fecharComanda(forma: FormaPagamento, totalCentavos: Long) {
        val comanda = estado.value.comanda ?: return
        viewModelScope.launch {
            val mesaFicaLivre = comanda.tipo == TipoComanda.MESA && outrasComandasDaMesa(comanda.mesa).isEmpty()
            encerramento = Encerramento.Aviso(
                if (mesaFicaLivre) Mensagem(R.string.comanda_paga_mesa_livre, comanda.numero, comanda.mesa)
                else Mensagem(R.string.comanda_paga, comanda.titulo)
            )
            repo.fecharComanda(comandaId, forma, totalCentavos)
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

    /** Reabre a conta fechada para lançar mais itens (só os novos vão para a produção). */
    fun reabrirComanda() {
        viewModelScope.launch {
            val titulo = estado.value.comanda?.titulo.orEmpty()
            repo.reabrirComanda(comandaId)
            _eventos.send(ComandaEvento.Aviso(Mensagem(R.string.conta_reaberta, titulo)))
        }
    }

    fun novaComandaNaMesa() {
        val mesa = estado.value.comanda?.mesa ?: return
        viewModelScope.launch { _eventos.send(ComandaEvento.AbrirComanda(repo.abrirNovaComanda(mesa).id)) }
    }
}
