package br.com.murupi.comandas.ui.catalogo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.databinding.ActivityCatalogoBinding
import br.com.murupi.comandas.databinding.DialogProdutoBinding
import br.com.murupi.comandas.ui.categoria.CategoriasActivity
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.util.Moeda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Catálogo: cadastro manual de produtos, grupo, preço, estoque e se aparece para venda. */
class CatalogoActivity : BaseActivity() {

    private lateinit var binding: ActivityCatalogoBinding
    private val viewModel by viewModelsDoApp { CatalogoViewModel(it.cardapio) }
    private val adapter = CatalogoAdapter { editar(it.produto) }

    override val ancoraSnackbar: View get() = binding.fab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.conteudo.aplicarInsets(base = true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { editar(null) }
        binding.autoFiltro.setOnItemClickListener { _, _, posicao, _ ->
            viewModel.filtrar(viewModel.categorias.value.getOrNull(posicao - 1)?.id)
        }

        coletar {
            launch { viewModel.categorias.collect(::preencherFiltro) }
            launch {
                viewModel.produtos.collect { lista ->
                    adapter.submitList(lista)
                    binding.textVazio.isVisible = lista.isEmpty()
                }
            }
            launch { viewModel.eventos.collect { avisar(it) } }
        }
    }

    private fun preencherFiltro(categorias: List<Categoria>) {
        val opcoes = listOf(getString(R.string.todas_categorias)) + categorias.map { it.nome }
        binding.autoFiltro.setSimpleItems(opcoes.toTypedArray())
        val atual = categorias.firstOrNull { it.id == viewModel.filtroAtual }?.nome ?: opcoes.first()
        binding.autoFiltro.setText(atual, false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_catalogo, menu)
        val campo = menu.findItem(R.id.action_buscar).actionView as SearchView
        campo.queryHint = getString(R.string.buscar_produto)
        campo.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(texto: String?): Boolean {
                campo.clearFocus()
                return true
            }

            override fun onQueryTextChange(texto: String?): Boolean {
                viewModel.buscar(texto.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_categorias -> {
            startActivity(Intent(this, CategoriasActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Cadastro (produto nulo) ou edição de um produto. */
    private fun editar(produto: Produto?) {
        val categorias = viewModel.categorias.value
        if (categorias.isEmpty()) return
        val b = DialogProdutoBinding.inflate(layoutInflater)

        var categoria: Categoria? = categorias.firstOrNull { it.id == (produto?.categoriaId ?: viewModel.filtroAtual) }
        b.autoCategoria.setSimpleItems(categorias.map { it.nome }.toTypedArray())
        categoria?.let { b.autoCategoria.setText(it.nome, false) }
        b.autoCategoria.setOnItemClickListener { _, _, posicao, _ ->
            categoria = categorias[posicao]
            b.layoutCategoria.error = null
        }

        b.autoGrupo.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, viewModel.grupos.value))
        b.editNome.setText(produto?.nome)
        b.autoGrupo.setText(produto?.grupo.orEmpty(), false)
        b.editDescricao.setText(produto?.descricao.orEmpty())
        b.editPreco.setText(produto?.let { Moeda.paraCampo(it.precoCentavos) })
        b.editMultiplo.setText((produto?.multiplo ?: 1).toString())
        b.switchEstoque.isChecked = produto?.estoque != null
        b.layoutEstoque.isVisible = b.switchEstoque.isChecked
        b.editEstoque.setText(produto?.estoque?.toString())
        b.switchEstoque.setOnCheckedChangeListener { _, marcado ->
            b.layoutEstoque.isVisible = marcado
            if (marcado) b.editEstoque.requestFocus()
        }
        b.switchAtivo.isChecked = produto?.ativo ?: true

        val construtor = MaterialAlertDialogBuilder(this)
            .setTitle(if (produto == null) R.string.novo_produto else R.string.editar_produto)
            .setView(b.root)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
        if (produto != null) construtor.setNeutralButton(R.string.excluir) { _, _ -> confirmarExclusao(produto) }
        val dialogo = construtor.create()

        dialogo.aoConfirmar {
            val nome = b.editNome.text?.toString().orEmpty().trim()
            if (nome.isEmpty()) {
                b.layoutNome.error = getString(R.string.informe_nome)
                return@aoConfirmar false
            }
            val escolhida = categoria
            if (escolhida == null) {
                b.layoutCategoria.error = getString(R.string.informe_categoria)
                return@aoConfirmar false
            }
            val textoPreco = b.editPreco.text?.toString().orEmpty()
            val preco = if (textoPreco.isBlank()) 0L else Moeda.converter(textoPreco)
            if (preco == null) {
                b.layoutPreco.error = getString(R.string.valor_invalido)
                return@aoConfirmar false
            }
            val multiplo = b.editMultiplo.text?.toString()?.toIntOrNull()?.takeIf { it in 1..MULTIPLO_MAXIMO }
            if (multiplo == null) {
                b.layoutMultiplo.error = getString(R.string.multiplo_invalido, MULTIPLO_MAXIMO)
                return@aoConfirmar false
            }
            val estoque = if (b.switchEstoque.isChecked) {
                b.editEstoque.text?.toString()?.toIntOrNull()?.takeIf { it >= 0 } ?: run {
                    b.layoutEstoque.error = getString(R.string.informe_quantidade)
                    return@aoConfirmar false
                }
            } else {
                null
            }
            val base = produto ?: Produto(categoriaId = escolhida.id, nome = nome, precoCentavos = preco)
            viewModel.salvar(
                base.copy(
                    categoriaId = escolhida.id,
                    nome = nome,
                    precoCentavos = preco,
                    grupo = b.autoGrupo.text?.toString()?.trim()?.ifEmpty { null },
                    descricao = b.editDescricao.text?.toString().orEmpty().trim(),
                    estoque = estoque,
                    ativo = b.switchAtivo.isChecked,
                    multiplo = multiplo
                )
            )
            true
        }
        dialogo.show()
    }

    private companion object {
        const val MULTIPLO_MAXIMO = 100
    }

    private fun confirmarExclusao(produto: Produto) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.excluir_produto)
            .setMessage(getString(R.string.excluir_produto_pergunta, produto.nome))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir(produto) }
            .show()
    }
}
