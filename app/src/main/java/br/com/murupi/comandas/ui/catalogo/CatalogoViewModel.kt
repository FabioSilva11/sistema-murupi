package br.com.murupi.comandas.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.repo.CardapioRepository
import br.com.murupi.comandas.ui.common.Mensagem
import br.com.murupi.comandas.util.Texto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class CatalogoViewModel(private val cardapio: CardapioRepository) : ViewModel() {

    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
    private val filtroCategoria = MutableStateFlow<Long?>(null)
    private val termoBusca = MutableStateFlow("")

    private val _eventos = Channel<Mensagem>(Channel.BUFFERED)
    val eventos: Flow<Mensagem> = _eventos.receiveAsFlow()

    val categorias: StateFlow<List<Categoria>> =
        cardapio.categorias().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Todos os produtos (inclusive ocultos), na ordem das categorias e com os grupos juntos. */
    val produtos: StateFlow<List<ProdutoItem>> =
        combine(categorias, cardapio.produtos(), filtroCategoria, termoBusca) { cats, prods, filtro, termo ->
            val porId = cats.associateBy { it.id }
            val busca = Texto.normalizarBusca(termo)
            prods.mapNotNull { p -> porId[p.categoriaId]?.let { ProdutoItem(p, it) } }
                .filter { filtro == null || it.categoria.id == filtro }
                .filter {
                    busca.isEmpty() ||
                        Texto.normalizarBusca(it.produto.nome).contains(busca) ||
                        Texto.normalizarBusca(it.produto.grupo.orEmpty()).contains(busca)
                }
                .sortedWith(
                    compareBy<ProdutoItem> { it.categoria.ordem }
                        .thenComparator { a, b -> collator.compare(chaveOrdem(a.produto), chaveOrdem(b.produto)) }
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Grupos já usados, para sugerir no cadastro. */
    val grupos: StateFlow<List<String>> = cardapio.produtos()
        .map { lista ->
            lista.mapNotNull { it.grupo?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .sortedWith { a, b -> collator.compare(a, b) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filtroAtual: Long? get() = filtroCategoria.value

    private fun chaveOrdem(produto: Produto) = "${produto.grupo.orEmpty()} ${produto.nome}"

    fun filtrar(categoriaId: Long?) {
        filtroCategoria.value = categoriaId
    }

    fun buscar(termo: String) {
        termoBusca.value = termo
    }

    fun salvar(produto: Produto) {
        viewModelScope.launch {
            cardapio.salvarProduto(produto)
            _eventos.send(Mensagem(R.string.produto_salvo, produto.nome))
        }
    }

    fun excluir(produto: Produto) {
        viewModelScope.launch {
            cardapio.excluirProduto(produto)
            _eventos.send(Mensagem(R.string.produto_excluido, produto.nome))
        }
    }
}
