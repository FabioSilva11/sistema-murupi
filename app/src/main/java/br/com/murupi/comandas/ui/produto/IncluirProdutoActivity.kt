package br.com.murupi.comandas.ui.produto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.model.esgotado
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.data.model.precoLivre
import br.com.murupi.comandas.data.model.rotuloNoGrupo
import br.com.murupi.comandas.databinding.ActivityIncluirProdutoBinding
import br.com.murupi.comandas.databinding.DialogItemBinding
import br.com.murupi.comandas.ui.catalogo.CatalogoActivity
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.FormularioItem
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.pedirTexto
import br.com.murupi.comandas.ui.common.tituloBarra
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.util.Moeda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Incluir Produto: categorias coloridas -> produtos. Produtos agrupados (ex.: Graviola) expandem
 * para os tamanhos; nos sucos, o tamanho expande as opções de preparo e cada toque lança 1.
 */
class IncluirProdutoActivity : BaseActivity() {

    private lateinit var binding: ActivityIncluirProdutoBinding
    private val comandaId by lazy { intent.getLongExtra(EXTRA_COMANDA_ID, 0L) }
    private val viewModel by viewModelsDoApp { IncluirProdutoViewModel(comandaId, it.cardapio, it.comandas) }

    private val categoriasAdapter = CategoriaAdapter { viewModel.abrirCategoria(it) }
    private val linhasAdapter = LinhasProdutoAdapter(
        aoTocarGrupo = ::abrirGrupo,
        aoTocarItem = ::aoTocarItem,
        aoEscolherPreparo = ::aoEscolherPreparo
    )
    private val gradeCategorias by lazy { GridLayoutManager(this, resources.getInteger(R.integer.colunas_categorias)) }
    private val listaProdutos by lazy { LinearLayoutManager(this) }

    private var itemBusca: MenuItem? = null

    /** Para rolar até as linhas que acabaram de expandir, sem rolar ao trocar de tela. */
    private var telaAnterior: TelaProdutos? = null
    private var chavesAnteriores: Set<String> = emptySet()

    /** Voltar fecha a busca ou volta para as categorias antes de sair da tela. */
    private val voltar = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val busca = itemBusca
            if (busca != null && busca.isActionViewExpanded) busca.collapseActionView()
            else viewModel.voltarParaCategorias()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncluirProdutoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.recycler.aplicarInsets(base = true)
        binding.recycler.itemAnimator = null
        onBackPressedDispatcher.addCallback(this, voltar)

        coletar {
            launch { viewModel.tela.collect(::renderizar) }
            launch {
                viewModel.comanda.collect { comanda ->
                    supportActionBar?.subtitle = comanda?.tituloBarra(this@IncluirProdutoActivity)
                }
            }
            launch {
                viewModel.eventos.collect { mensagem ->
                    avisar(mensagem).setAction(R.string.ver_comanda) { finish() }
                }
            }
        }
    }

    private fun renderizar(tela: TelaProdutos) {
        atualizarVoltar()
        when (tela) {
            is TelaProdutos.Categorias -> {
                supportActionBar?.title = getString(R.string.incluir_produto)
                mostrar(gradeCategorias, categoriasAdapter)
                categoriasAdapter.submitList(tela.categorias)
                binding.textVazio.setText(R.string.cardapio_vazio)
                binding.textVazio.isVisible = tela.categorias.isEmpty()
            }
            is TelaProdutos.Produtos -> {
                supportActionBar?.title = tela.categoria.nome
                mostrar(listaProdutos, linhasAdapter)
                val anterior = telaAnterior
                mostrarLinhas(tela.linhas, mesmaTela = anterior is TelaProdutos.Produtos && anterior.categoria.id == tela.categoria.id)
                binding.textVazio.setText(R.string.categoria_vazia)
                binding.textVazio.isVisible = tela.linhas.isEmpty()
            }
            is TelaProdutos.Busca -> {
                supportActionBar?.title = getString(R.string.buscar)
                mostrar(listaProdutos, linhasAdapter)
                val anterior = telaAnterior
                mostrarLinhas(tela.linhas, mesmaTela = anterior is TelaProdutos.Busca && anterior.termo == tela.termo)
                binding.textVazio.setText(R.string.nenhum_produto_encontrado)
                binding.textVazio.isVisible = tela.linhas.isEmpty()
            }
        }
        telaAnterior = tela
    }

    /** Ao expandir um grupo ou um suco no fim da tela, rola para mostrar o que abriu. */
    private fun mostrarLinhas(linhas: List<LinhaProduto>, mesmaTela: Boolean) {
        val ultimaNova = linhas.indices.lastOrNull { linhas[it].chave !in chavesAnteriores }
        chavesAnteriores = linhas.map { it.chave }.toSet()
        linhasAdapter.submitList(linhas) {
            if (mesmaTela && ultimaNova != null) binding.recycler.smoothScrollToPosition(ultimaNova)
        }
    }

    private fun mostrar(gerenciador: RecyclerView.LayoutManager, adapter: RecyclerView.Adapter<*>) {
        if (binding.recycler.adapter === adapter) return
        binding.recycler.layoutManager = gerenciador
        binding.recycler.adapter = adapter
    }

    private fun atualizarVoltar() {
        voltar.isEnabled = viewModel.tela.value !is TelaProdutos.Categorias || itemBusca?.isActionViewExpanded == true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_incluir_produto, menu)
        val busca = menu.findItem(R.id.action_buscar)
        itemBusca = busca
        val campo = busca.actionView as SearchView
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
        busca.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                voltar.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.buscar("")
                voltar.isEnabled = viewModel.tela.value !is TelaProdutos.Categorias
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_concluir -> {
            finish()
            true
        }
        R.id.action_catalogo -> {
            startActivity(Intent(this, CatalogoActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun aoTocarItem(linha: LinhaProduto.Item) {
        val produto = linha.item.produto
        when {
            produto.esgotado -> avisar(R.string.produto_esgotado, produto.nome)
            linha.pedePreparo -> viewModel.alternarPreparo(produto.id)
            else -> lancarProduto(linha.item)
        }
    }

    /** Grupo com tamanhos/sabores abre um painel inferior com opções, quantidade e observação. */
    private fun abrirGrupo(grupo: LinhaProduto.Grupo) {
        val disponiveis = grupo.opcoes.filterNot { it.produto.esgotado }
        if (disponiveis.isEmpty()) {
            avisar(R.string.produto_esgotado, grupo.nome)
            return
        }
        lancarProduto(disponiveis.first(), variantes = grupo.opcoes)
    }

    /** Toque numa opção de preparo: abre a janela de lançamento com a opção já preenchida. */
    private fun aoEscolherPreparo(item: ProdutoItem, preparo: String) {
        if (preparo != FormularioItem.OPCAO_OUTRO) {
            lancarProduto(item, observacaoInicial = preparo, perguntarOpcoesSuco = false)
            return
        }
        pedirTexto(
            titulo = item.produto.nome,
            rotulo = getString(R.string.descreva_opcao),
            tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            validar = { texto -> if (texto.isEmpty()) getString(R.string.descreva_opcao) else null }
        ) { texto -> lancarProduto(item, observacaoInicial = texto, perguntarOpcoesSuco = false) }
    }

    /** Janela de lançamento: quantidade, preço (se livre), opções do suco e observação. */
    private fun lancarProduto(
        item: ProdutoItem,
        observacaoInicial: String = "",
        perguntarOpcoesSuco: Boolean = item.categoria.perguntarOpcoesSuco &&
            FormularioItem.exigeOpcaoLeite(item.produto.nome, item.produto.grupo),
        variantes: List<ProdutoItem> = emptyList()
    ) {
        val produto = item.produto
        val b = DialogItemBinding.inflate(layoutInflater)
        val formularioContainer = b.root.getChildAt(0) as LinearLayout
        var itemSelecionado = item
        var formulario = criarFormulario(b, itemSelecionado, observacaoInicial, perguntarOpcoesSuco)

        if (variantes.size > 1) {
            val cabecalho = TextView(this).apply {
                text = variantes.first().produto.grupo.orEmpty()
                setTextColor(getColor(R.color.texto_primario))
                textSize = 20f
                setPadding(0, dp(12), 0, dp(4))
            }
            val dica = TextView(this).apply {
                text = getString(R.string.escolha_opcao_produto)
                setTextColor(getColor(R.color.texto_secundario))
                textSize = 14f
                setPadding(0, 0, 0, dp(8))
            }
            val selecao = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            formularioContainer.addView(cabecalho, 0)
            formularioContainer.addView(dica, 1)
            formularioContainer.addView(selecao, 2)
            val botoes = mutableListOf<RadioButton>()
            variantes.forEach { opcao ->
                val botao = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = "${opcao.produto.rotuloNoGrupo()}   ·   ${Moeda.formatar(opcao.produto.precoCentavos)}"
                    isEnabled = !opcao.produto.esgotado
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    setOnClickListener {
                        if (opcao.produto.esgotado) return@setOnClickListener
                        botoes.forEach { it.isChecked = it === this }
                        itemSelecionado = opcao
                        b.grupoOpcoes.removeAllViews()
                        b.textInfo.text = textoInfo(opcao)
                        val pedePreparo = opcao.categoria.perguntarOpcoesSuco &&
                            FormularioItem.exigeOpcaoLeite(opcao.produto.nome, opcao.produto.grupo)
                        formulario = criarFormulario(b, opcao, observacaoInicial, pedePreparo)
                    }
                }
                botoes += botao
                selecao.addView(botao)
                if (opcao == item) botao.isChecked = true
            }
        }

        b.textInfo.text = textoInfo(itemSelecionado)
        if (variantes.size > 1) {
            b.textInfo.setPadding(0, dp(6), 0, 0)
        }

        if (variantes.size > 1) {
            lateinit var sheet: BottomSheetDialog
            val botaoAdicionar = MaterialButton(this).apply {
                text = getString(R.string.adicionar)
                icon = getDrawable(R.drawable.ic_check)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                setOnClickListener {
                    val dados = formulario.validar() ?: return@setOnClickListener
                    viewModel.adicionar(itemSelecionado, dados)
                    sheet.dismiss()
                }
            }
            formularioContainer.addView(botaoAdicionar)
            sheet = BottomSheetDialog(this)
            sheet.setContentView(b.root)
            sheet.setOnShowListener {
                sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                sheet.behavior.skipCollapsed = true
            }
            sheet.show()
        } else {
            val dialogo = MaterialAlertDialogBuilder(this)
                .setTitle(produto.nome)
                .setView(b.root)
                .setPositiveButton(R.string.adicionar, null)
                .setNegativeButton(R.string.cancelar, null)
                .create()
            dialogo.aoConfirmar {
                val dados = formulario.validar() ?: return@aoConfirmar false
                viewModel.adicionar(itemSelecionado, dados)
                true
            }
            dialogo.show()
        }
    }

    private fun criarFormulario(
        b: DialogItemBinding,
        item: ProdutoItem,
        observacao: String,
        perguntarOpcoesSuco: Boolean
    ) = FormularioItem(
        b,
        quantidadeInicial = 1,
        precoCentavos = item.produto.precoCentavos,
        precoEditavel = item.produto.precoLivre,
        observacaoInicial = observacao,
        perguntarOpcoesSuco = perguntarOpcoesSuco,
        quantidadeMaxima = item.produto.estoque ?: FormularioItem.QUANTIDADE_MAXIMA,
        passo = item.produto.multiplo
    )

    private fun textoInfo(item: ProdutoItem): String = listOfNotNull(
        getString(R.string.info_produto, item.categoria.nome, Moeda.formatar(item.produto.precoCentavos)),
        item.produto.estoque?.let { getString(R.string.restam_n, it) }
    ).joinToString(" · ")

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_COMANDA_ID = "comanda_id"

        fun intentPara(context: Context, comandaId: Long): Intent =
            Intent(context, IncluirProdutoActivity::class.java).putExtra(EXTRA_COMANDA_ID, comandaId)
    }
}
