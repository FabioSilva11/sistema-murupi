package br.com.murupi.comandas.ui.categoria

import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Setor
import br.com.murupi.comandas.databinding.ActivityCategoriasBinding
import br.com.murupi.comandas.databinding.DialogCategoriaBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.pedirTexto
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Gestão de categorias: criar, editar (nome, cor, setor, demanda, opções de suco)
 * e excluir. A ordem segue a lista; nova categoria entra no fim.
 */
class CategoriasActivity : BaseActivity() {

    private lateinit var binding: ActivityCategoriasBinding
    private val viewModel by viewModelsDoApp { CategoriasViewModel(it.cardapio) }
    private val adapter = CategoriasAdapter(::editar)

    override val ancoraSnackbar: View get() = binding.fab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.root.aplicarInsets(base = true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { editar(null) }

        coletar {
            launch {
                viewModel.produtosPorCategoria.collect { contagens ->
                    adapter.setContagens(contagens)
                }
            }
            launch {
                viewModel.categorias.collect { lista ->
                    adapter.submitList(lista)
                    binding.textVazio.isVisible = lista.isEmpty()
                }
            }
            launch { viewModel.eventos.collect { avisar(it) } }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_categorias, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_excluir_categorias_vazias -> {
            confirmarExclusaoVazias()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmarExclusaoVazias() {
        lifecycleScope.launch {
            val vazias = viewModel.quantidadeCategoriasVazias()
            if (vazias == 0) {
                avisar(R.string.sem_categorias_vazias)
                return@launch
            }
            MaterialAlertDialogBuilder(this@CategoriasActivity)
                .setTitle(R.string.excluir_categorias_vazias)
                .setMessage(resources.getQuantityString(R.plurals.confirmar_excluir_categorias_vazias, vazias, vazias))
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluirCategoriasVazias() }
                .show()
        }
    }

    /** Cadastro (categoria nula) ou edição. */
    private fun editar(categoria: Categoria?) {
        val b = DialogCategoriaBinding.inflate(layoutInflater)
        b.editNome.setText(categoria?.nome.orEmpty())

        // Cor: paleta fixa do seed + personalizada via campo texto (última opção do menu).
        var cor = categoria?.cor ?: CORES_PADRAO.first()
        val opcoesCor = CORES_PADRAO + getString(R.string.outra_cor)
        b.autoCor.setSimpleItems(opcoesCor.toTypedArray())
        b.autoCor.setText(nomeDaCor(cor), false)
        b.autoCor.setOnItemClickListener { _, _, posicao, _ ->
            if (posicao == CORES_PADRAO.size) {
                pedirCor { hex ->
                    cor = hex
                    b.autoCor.setText(nomeDaCor(hex), false)
                }
            } else {
                opcoesCor.getOrNull(posicao)?.let { cor = it }
            }
        }

        val setores = Setor.entries
        b.autoSetor.setSimpleItems(setores.map { it.descricao }.toTypedArray())
        var setor = categoria?.setor ?: Setor.COZINHA
        b.autoSetor.setText(setor.descricao, false)
        b.autoSetor.setOnItemClickListener { _, _, posicao, _ ->
            setores.getOrNull(posicao)?.let { setor = it }
        }

        b.switchDemanda.isChecked = categoria?.demanda ?: false
        b.switchOpcoesSuco.isChecked = categoria?.perguntarOpcoesSuco ?: false

        val construtor = MaterialAlertDialogBuilder(this)
            .setTitle(if (categoria == null) R.string.nova_categoria else R.string.editar_categoria)
            .setView(b.root)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
        if (categoria != null && !viewModel.categoriaProtegida(categoria)) {
            construtor.setNeutralButton(R.string.excluir) { _, _ -> confirmarExclusao(categoria) }
        }
        val dialogo = construtor.create()

        dialogo.aoConfirmar {
            val nome = b.editNome.text?.toString().orEmpty().trim()
            if (nome.isEmpty()) {
                b.layoutNome.error = getString(R.string.informe_nome)
                return@aoConfirmar false
            }
            viewModel.salvar(
                (categoria ?: Categoria(nome = nome, cor = cor, ordem = 0, setor = setor)).copy(
                    nome = nome,
                    cor = cor,
                    setor = setor,
                    demanda = b.switchDemanda.isChecked,
                    perguntarOpcoesSuco = b.switchOpcoesSuco.isChecked
                )
            )
            true
        }
        dialogo.show()
    }

    /** Pedido de cor fora da paleta, validando o formato "#RRGGBB". */
    private fun pedirCor(aoEscolher: (String) -> Unit) {
        pedirTexto(
            titulo = getString(R.string.cor_categoria),
            rotulo = getString(R.string.cor_personalizada_hint),
            tipoEntrada = android.text.InputType.TYPE_CLASS_TEXT,
            validar = { texto ->
                if (texto.matches(Regex("#[0-9a-fA-F]{6}"))) null
                else getString(R.string.cor_invalida)
            }
        ) { hex -> aoEscolher(hex.uppercase()) }
    }

    private fun nomeDaCor(hex: String): String =
        NOMES_CORES[hex.uppercase()] ?: hex

    private fun confirmarExclusao(categoria: Categoria) {
        lifecycleScope.launch {
            val produtos = viewModel.produtosDaCategoria(categoria)
            val mensagem = if (produtos > 0) getString(R.string.excluir_categoria_pergunta, categoria.nome, produtos)
            else getString(R.string.excluir_categoria_sem_produtos, categoria.nome)
            MaterialAlertDialogBuilder(this@CategoriasActivity)
                .setTitle(R.string.excluir_categoria)
                .setMessage(mensagem)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir(categoria) }
                .show()
        }
    }

    private companion object {
        val CORES_PADRAO = listOf(
            "#2E7D32", "#3949AB", "#C62828", "#AD1457", "#6A1B9A", "#6D4C41",
            "#EF6C00", "#F9A825", "#9CCC65", "#FFB300", "#00897B", "#00ACC1",
            "#0277BD", "#F06292", "#EC407A", "#D84315", "#827717", "#FF7043", "#8E24AA"
        )
        val NOMES_CORES = mapOf(
            "#2E7D32" to "Verde", "#3949AB" to "Índigo", "#C62828" to "Vermelho",
            "#AD1457" to "Rosa", "#6A1B9A" to "Roxo", "#6D4C41" to "Marrom",
            "#EF6C00" to "Laranja", "#F9A825" to "Amarelo", "#9CCC65" to "Verde claro",
            "#FFB300" to "Âmbar", "#00897B" to "Verde-azulado", "#00ACC1" to "Ciano",
            "#0277BD" to "Azul claro", "#F06292" to "Rosa claro", "#EC407A" to "Rosa forte",
            "#D84315" to "Ferrugem", "#827717" to "Oliva", "#FF7043" to "Coral", "#8E24AA" to "Lilás"
        )
    }
}
