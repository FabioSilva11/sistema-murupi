package br.com.murupi.comandas.ui.categoria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.repo.CardapioRepository
import br.com.murupi.comandas.ui.common.Mensagem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CategoriasViewModel(private val cardapio: CardapioRepository) : ViewModel() {

    private val _eventos = Channel<Mensagem>(Channel.BUFFERED)
    val eventos: Flow<Mensagem> = _eventos.receiveAsFlow()

    val categorias: StateFlow<List<Categoria>> =
        cardapio.categorias().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val produtosPorCategoria: StateFlow<Map<Long, Int>> = combine(categorias, cardapio.produtos()) { lista, produtos ->
        lista.associate { categoria -> categoria.id to produtos.count { it.categoriaId == categoria.id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun salvar(categoria: Categoria) {
        viewModelScope.launch {
            cardapio.salvarCategoria(categoria)
            _eventos.send(Mensagem(R.string.categoria_salva, categoria.nome))
        }
    }

    fun excluir(categoria: Categoria) {
        viewModelScope.launch {
            cardapio.excluirCategoria(categoria)
            _eventos.send(Mensagem(R.string.categoria_excluida, categoria.nome))
        }
    }

    /** Quantidade de produtos da categoria, para o aviso de exclusão. */
    suspend fun produtosDaCategoria(categoria: Categoria): Int = cardapio.produtosDaCategoria(categoria)

    fun categoriaProtegida(categoria: Categoria): Boolean = cardapio.categoriaProtegida(categoria)

    fun contagemProdutos(categoriaId: Long): Int = produtosPorCategoria.value[categoriaId] ?: 0

    suspend fun quantidadeCategoriasVazias(): Int = cardapio.contarCategoriasVazias()

    fun excluirCategoriasVazias() {
        viewModelScope.launch {
            val quantidade = cardapio.excluirCategoriasVazias()
            _eventos.send(Mensagem(R.string.categorias_vazias_excluidas, quantidade))
        }
    }
}
