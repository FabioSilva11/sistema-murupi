package br.com.murupi.comandas.ui.produto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.repo.CardapioRepository
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.EstoqueInsuficienteException
import br.com.murupi.comandas.ui.common.DadosItem
import br.com.murupi.comandas.ui.common.Mensagem
import br.com.murupi.comandas.util.Texto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** O que a tela mostra: a grade de categorias, os produtos de uma categoria ou uma busca. */
sealed interface TelaProdutos {
    data class Categorias(val categorias: List<Categoria>) : TelaProdutos
    data class Produtos(val categoria: Categoria, val linhas: List<LinhaProduto>) : TelaProdutos
    data class Busca(val termo: String, val linhas: List<LinhaProduto>) : TelaProdutos
}

/** Todas as categorias cadastradas ficam disponíveis, inclusive antes de receberem produtos. */
fun categoriasParaLancamento(categorias: List<Categoria>): List<Categoria> = categorias

private data class Navegacao(
    val categoriaId: Long?,
    val termo: String,
    val gruposAbertos: Set<String>,
    val preparoAberto: Long?
)

class IncluirProdutoViewModel(
    private val comandaId: Long,
    cardapio: CardapioRepository,
    private val comandas: ComandaRepository
) : ViewModel() {

    private val categoriaAberta = MutableStateFlow<Long?>(null)
    private val termoBusca = MutableStateFlow("")
    private val gruposAbertos = MutableStateFlow<Set<String>>(emptySet())
    private val preparoAberto = MutableStateFlow<Long?>(null)

    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
    private val ordemAlfabetica = Comparator<String> { a, b -> collator.compare(a, b) }

    private val _eventos = Channel<Mensagem>(Channel.BUFFERED)
    val eventos: Flow<Mensagem> = _eventos.receiveAsFlow()

    val comanda: StateFlow<Comanda?> =
        comandas.observarComanda(comandaId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val categorias = cardapio.categorias()

    /** Só produtos disponíveis para venda, já com a categoria. */
    private val disponiveis: Flow<List<ProdutoItem>> = combine(categorias, cardapio.produtos()) { cats, prods ->
        val porId = cats.associateBy { it.id }
        prods.filter { it.ativo }.mapNotNull { p -> porId[p.categoriaId]?.let { ProdutoItem(p, it) } }
    }

    private val navegacao: Flow<Navegacao> =
        combine(categoriaAberta, termoBusca, gruposAbertos, preparoAberto) { categoria, termo, grupos, preparo ->
            Navegacao(categoria, termo, grupos, preparo)
        }

    val tela: StateFlow<TelaProdutos> = combine(categorias, disponiveis, navegacao) { cats, itens, nav ->
        val busca = Texto.normalizarBusca(nav.termo)
        val categoria = cats.firstOrNull { it.id == nav.categoriaId }
        when {
            busca.isNotEmpty() -> TelaProdutos.Busca(
                nav.termo,
                MontadorLinhas.montar(
                    itens.filter { Texto.normalizarBusca(it.produto.nome).contains(busca) },
                    nav.gruposAbertos, nav.preparoAberto,
                    agrupar = false, mostrarCategoria = true, ordemAlfabetica = ordemAlfabetica
                )
            )
            categoria != null -> TelaProdutos.Produtos(
                categoria,
                MontadorLinhas.montar(
                    itens.filter { it.produto.categoriaId == categoria.id },
                    nav.gruposAbertos, nav.preparoAberto,
                    agrupar = true, mostrarCategoria = false, ordemAlfabetica = ordemAlfabetica
                )
            )
            else -> {
                // Todas as categorias ficam visíveis no lançamento, mesmo sem produto ativo.
                TelaProdutos.Categorias(categoriasParaLancamento(cats))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TelaProdutos.Categorias(emptyList()))

    fun abrirCategoria(categoria: Categoria) {
        categoriaAberta.value = categoria.id
        preparoAberto.value = null
    }

    fun voltarParaCategorias() {
        categoriaAberta.value = null
        preparoAberto.value = null
    }

    fun buscar(termo: String) {
        termoBusca.value = termo
    }

    fun alternarGrupo(chave: String) {
        gruposAbertos.update { if (chave in it) it - chave else it + chave }
    }

    fun alternarPreparo(produtoId: Long) {
        preparoAberto.update { if (it == produtoId) null else produtoId }
    }

    /** Toque numa opção de preparo: lança 1 unidade (ou o múltiplo de venda) com a opção como observação. */
    fun adicionarComPreparo(item: ProdutoItem, preparo: String) = adicionar(
        item,
        DadosItem(quantidade = item.produto.multiplo, precoCentavos = item.produto.precoCentavos, observacao = preparo)
    )

    fun adicionar(item: ProdutoItem, dados: DadosItem) {
        viewModelScope.launch {
            try {
                comandas.adicionarItem(
                    comandaId, item.produto, item.categoria, dados.quantidade, dados.precoCentavos, dados.observacao
                )
                _eventos.send(
                    if (dados.observacao.isEmpty()) {
                        Mensagem(R.string.item_adicionado, dados.quantidade, item.produto.nome)
                    } else {
                        Mensagem(R.string.item_adicionado_obs, dados.quantidade, item.produto.nome, dados.observacao)
                    }
                )
            } catch (e: EstoqueInsuficienteException) {
                _eventos.send(Mensagem(R.string.estoque_insuficiente, e.produto, e.disponivel))
            }
        }
    }
}
