package br.com.murupi.comandas.ui.comanda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.PoliticaReabertura
import br.com.murupi.comandas.data.model.StatusComanda
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.databinding.ActivityComandaBinding
import br.com.murupi.comandas.databinding.DialogItemBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.FormularioItem
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.pedirTexto
import br.com.murupi.comandas.ui.common.tituloBarra
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.ui.impressora.ImpressorasActivity
import br.com.murupi.comandas.ui.inicio.InicioActivity
import br.com.murupi.comandas.ui.produto.IncluirProdutoActivity
import br.com.murupi.comandas.util.Moeda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Comanda "Mesa X.Y": itens lançados, total e o menu de impressão/pagamento/transferência. */
class ComandaActivity : BaseActivity() {

    private lateinit var binding: ActivityComandaBinding
    private val comandaId by lazy { intent.getLongExtra(EXTRA_COMANDA_ID, 0L) }
    private val consultaHistorico by lazy { intent.getBooleanExtra(EXTRA_CONSULTA_HISTORICO, false) }
    private val viewModel by viewModelsDoApp { ComandaViewModel(comandaId, it.comandas, it.impressao) }
    private val adapter = ItemComandaAdapter(aoTocar = ::editarItem)

    /** Comanda fechada aberta pelo histórico: mostra tudo, mas não deixa alterar nada. */
    private var somenteLeitura = false

    private val protegerRascunho = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = confirmarSaidaDoPedido()
    }

    override val ancoraSnackbar: View get() = binding.fab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.barraTotal.aplicarInsets(base = true)
        onBackPressedDispatcher.addCallback(this, protegerRascunho)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { startActivity(IncluirProdutoActivity.intentPara(this, comandaId)) }

        coletar {
            launch { viewModel.estado.collect(::renderizar) }
            launch { viewModel.imprimindo.collect { binding.progresso.isVisible = it } }
            launch { viewModel.eventos.collect(::tratarEvento) }
        }
    }

    private fun renderizar(estado: ComandaUiState) {
        if (!estado.carregado) return
        val comanda = estado.comanda
        if (comanda == null) {
            protegerRascunho.isEnabled = false
            encerrar()
            return
        }
        // Fechou agora (pagamento ou espelho): sai com o aviso. Fechada antiga: abre para consulta.
        if (comanda.status != StatusComanda.ABERTA && viewModel.encerramento != null) {
            encerrar()
            return
        }
        somenteLeitura = comanda.status != StatusComanda.ABERTA
        protegerRascunho.isEnabled = !consultaHistorico &&
            comanda.status == StatusComanda.ABERTA &&
            estado.itens.isNotEmpty() &&
            estado.itens.none { it.enviadoProducao }
        invalidateOptionsMenu()
        binding.fab.isVisible = !somenteLeitura
        supportActionBar?.title = comanda.tituloBarra(this)
        supportActionBar?.subtitle = listOfNotNull(
            getString(R.string.conta_fechada).takeIf { somenteLeitura },
            comanda.pessoas.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.pessoas, it, it) },
            if (estado.quantidadeItens == 0) getString(R.string.sem_itens)
            else resources.getQuantityString(R.plurals.itens, estado.quantidadeItens, estado.quantidadeItens)
        ).joinToString(" · ")

        adapter.submitList(estado.itens)
        binding.textVazio.isVisible = estado.itens.isEmpty()
        binding.textTotal.text = Moeda.formatar(estado.totalCentavos)
        binding.textPendentes.isVisible = estado.pendentes > 0 || estado.cancelamentosPendentes.isNotEmpty()
        binding.textPendentes.text = listOfNotNull(
            if (estado.pendentes > 0) resources.getQuantityString(
                R.plurals.itens_pendentes, estado.pendentes, estado.pendentes
            ) else null,
            if (estado.cancelamentosPendentes.isNotEmpty()) resources.getQuantityString(
                R.plurals.cancelamentos_pendentes,
                estado.cancelamentosPendentes.size,
                estado.cancelamentosPendentes.size
            ) else null
        ).joinToString(" · ")
    }

    /** A comanda foi paga, unida a outra ou cancelada: avisa e sai da tela. */
    private fun encerrar() {
        if (isFinishing) return
        when (val fim = viewModel.encerramento) {
            is Encerramento.Unida -> startActivity(intentPara(this, fim.destinoId))
            is Encerramento.Aviso -> Toast.makeText(applicationContext, fim.mensagem.texto(this), Toast.LENGTH_LONG).show()
            null -> Unit
        }
        finish()
    }

    private fun tratarEvento(evento: ComandaEvento) {
        when (evento) {
            is ComandaEvento.Aviso -> avisar(evento.mensagem)
            is ComandaEvento.Impressao -> mostrarResultadoImpressao(evento)
            ComandaEvento.SolicitarPreviaProducao -> mostrarPreviaProducao(reimprimirTudo = false)
            is ComandaEvento.AbrirComanda -> {
                startActivity(intentPara(this, evento.id))
                finish()
            }
        }
    }

    private fun confirmarSaidaDoPedido() {
        val comanda = viewModel.estado.value.comanda ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pedido_nao_impresso_titulo)
            .setMessage(getString(R.string.pedido_nao_impresso_mensagem, comanda.tituloBarra(this)))
            .setNegativeButton(R.string.continuar_pedido, null)
            .setPositiveButton(R.string.sair_excluir_pedido) { _, _ ->
                protegerRascunho.isEnabled = false
                viewModel.excluirPedidoNaoImpresso()
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comanda, menu)
        return true
    }

    /** Transferir e nova comanda só fazem sentido para mesas do salão; nada muda em consulta. */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val comanda = viewModel.estado.value.comanda
        val ehMesaAberta = comanda?.tipo == br.com.murupi.comandas.data.model.TipoComanda.MESA && !somenteLeitura
        menu.findItem(R.id.action_transferir)?.isVisible = ehMesaAberta
        menu.findItem(R.id.action_nova_comanda)?.isVisible = ehMesaAberta
        menu.findItem(R.id.action_imprimir_producao)?.isVisible = !somenteLeitura
        menu.findItem(R.id.action_pagamento)?.isVisible = !somenteLeitura
        menu.findItem(R.id.action_pessoas)?.isVisible = !somenteLeitura
        menu.findItem(R.id.action_reabrir)?.isVisible =
            PoliticaReabertura.podeReabrir(comanda, consultaHistorico)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_imprimir_producao -> imprimirProducao()
            R.id.action_imprimir_espelho -> imprimirEspelho()
            R.id.action_pagamento -> efetuarPagamento()
            R.id.action_transferir -> transferir()
            R.id.action_pessoas -> informarPessoas()
            R.id.action_nova_comanda -> viewModel.novaComandaNaMesa()
            R.id.action_reabrir -> viewModel.reabrirComanda()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- Impressão ----------

    private fun imprimirProducao() {
        val estado = viewModel.estado.value
        when {
            estado.itens.isEmpty() && estado.cancelamentosPendentes.isEmpty() -> avisar(R.string.comanda_sem_itens)
            estado.pendentes > 0 || estado.cancelamentosPendentes.isNotEmpty() -> mostrarPreviaProducao(reimprimirTudo = false)
            else -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.imprimir_producao)
                .setMessage(R.string.reimprimir_pergunta)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.pre_visualizar) { _, _ -> mostrarPreviaProducao(reimprimirTudo = true) }
                .show()
        }
    }

    private fun mostrarPreviaProducao(reimprimirTudo: Boolean) {
        lifecycleScope.launch {
            val previas = viewModel.previaProducao(reimprimirTudo)
            if (previas.isEmpty()) {
                avisar(R.string.nada_para_imprimir)
                return@launch
            }
            val titulo = if (viewModel.estado.value.cancelamentosPendentes.isNotEmpty()) {
                R.string.atualizacao_producao
            } else {
                R.string.imprimir_producao
            }
            mostrarPrevia(titulo, previas) {
                viewModel.imprimirProducao(reimprimirTudo = reimprimirTudo)
            }
        }
    }

    private fun imprimirEspelho() {
        if (viewModel.estado.value.itens.isEmpty()) {
            avisar(R.string.comanda_sem_itens)
            return
        }
        lifecycleScope.launch {
            val previa = viewModel.previaEspelho() ?: run {
                avisar(R.string.nada_para_imprimir)
                return@launch
            }
            mostrarPrevia(R.string.imprimir_espelho, listOf(previa)) { viewModel.imprimirEspelho() }
        }
    }

    /** Mostra o texto exato dos tickets e só imprime depois de aprovar. */
    private fun mostrarPrevia(
        titulo: Int,
        previas: List<br.com.murupi.comandas.print.ServicoImpressao.Previa>,
        aoAprovar: () -> Unit
    ) {
        val b = br.com.murupi.comandas.databinding.DialogPreviewBinding.inflate(layoutInflater)
        b.textPreview.text = previas.joinToString("\n\n") {
            "${it.papel} → ${it.impressoras}\n${it.texto}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setView(b.root)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.imprimir) { _, _ -> aoAprovar() }
            .show()
    }

    private fun mostrarResultadoImpressao(evento: ComandaEvento.Impressao) {
        val resultado = evento.resultado
        if (resultado.vazio) {
            avisar(R.string.nada_para_imprimir)
            return
        }
        if (resultado.sucesso) {
            val mensagem = getString(R.string.impressao_ok, resultado.envios.joinToString { it.impressora.nome })
            if (evento.titulo == R.string.imprimir_producao || evento.titulo == R.string.atualizacao_producao) {
                Toast.makeText(applicationContext, mensagem, Toast.LENGTH_SHORT).show()
                startActivity(InicioActivity.intentParaMesas(this))
                finish()
            } else {
                avisar(mensagem)
            }
            return
        }
        val linhas = buildList {
            resultado.envios.forEach { envio ->
                val imp = envio.impressora
                add(
                    if (envio.erro == null) getString(R.string.envio_ok, imp.nome, imp.ip)
                    else getString(R.string.envio_falhou, imp.nome, imp.ip, envio.erro)
                )
            }
            resultado.papeisSemImpressora.forEach { add(getString(R.string.sem_impressora_papel, it.descricao)) }
            if (evento.titulo == R.string.imprimir_producao) add(getString(R.string.pendentes_continuam))
            if (evento.titulo == R.string.atualizacao_producao) add(getString(R.string.cancelamento_aguarda_impressao))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(evento.titulo)
            .setMessage(linhas.joinToString("\n\n"))
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.impressoras) { _, _ ->
                startActivity(Intent(this, ImpressorasActivity::class.java))
            }
            .show()
    }

    // ---------- Itens ----------

    private fun editarItem(item: ItemComanda) {
        if (somenteLeitura) return
        val b = DialogItemBinding.inflate(layoutInflater)
        b.textInfo.text = item.categoriaNome
        b.textAviso.isVisible = item.enviadoProducao
        // Dividir: vira itens de 1 unidade para observar cada um (ex.: um para viagem).
        b.btnDividir.isVisible = item.quantidade > 1
        val formulario = FormularioItem(
            b,
            quantidadeInicial = item.quantidade,
            precoCentavos = item.precoUnitarioCentavos,
            precoEditavel = item.precoLivre,
            observacaoInicial = item.observacao,
            perguntarOpcoesSuco = false,
            passo = item.multiplo
        )
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(item.nome)
            .setView(b.root)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .setNeutralButton(R.string.remover) { _, _ -> confirmarRemocao(item) }
            .create()
        dialogo.aoConfirmar {
            val dados = formulario.validar() ?: return@aoConfirmar false
            viewModel.atualizarItem(
                item.copy(
                    quantidade = dados.quantidade,
                    precoUnitarioCentavos = dados.precoCentavos,
                    observacao = dados.observacao
                )
            )
            true
        }
        b.btnDividir.setOnClickListener {
            dialogo.dismiss()
            viewModel.dividirItem(item)
        }
        dialogo.show()
    }

    private fun confirmarRemocao(item: ItemComanda) {
        MaterialAlertDialogBuilder(this)
            .setTitle(if (item.enviadoProducao) R.string.cancelar_item else R.string.remover_item)
            .setMessage(
                getString(
                    if (item.enviadoProducao) R.string.cancelar_item_enviado_confirmacao
                    else R.string.remover_item_confirmacao,
                    item.nome
                )
            )
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(if (item.enviadoProducao) R.string.cancelar_item else R.string.remover) { _, _ ->
                viewModel.removerItem(item)
            }
            .show()
    }

    // ---------- Pessoas ----------

    private fun informarPessoas() {
        val comanda = viewModel.estado.value.comanda ?: return
        pedirTexto(
            titulo = getString(R.string.informar_pessoas),
            rotulo = getString(R.string.quantidade_pessoas),
            valorInicial = comanda.pessoas.takeIf { it > 0 }?.toString().orEmpty(),
            tipoEntrada = InputType.TYPE_CLASS_NUMBER,
            validar = { texto -> if (texto.toIntOrNull() in 0..MAX_PESSOAS) null else getString(R.string.numero_invalido) }
        ) { texto -> viewModel.definirPessoas(texto.toInt()) }
    }

    // ---------- Transferência ----------

    private fun transferir() {
        val comanda = viewModel.estado.value.comanda ?: return
        pedirTexto(
            titulo = getString(R.string.transferir_comanda_n, comanda.numero),
            rotulo = getString(R.string.mesa_destino),
            tipoEntrada = InputType.TYPE_CLASS_NUMBER,
            validar = { texto ->
                if (texto.toIntOrNull() in 1..Restaurante.TOTAL_MESAS) null
                else getString(R.string.mesa_invalida, Restaurante.TOTAL_MESAS)
            }
        ) { texto -> escolherDestino(mesaAtual = comanda.mesa, mesaDestino = texto.toInt()) }
    }

    /** Se a mesa de destino já tem comanda, pergunta se junta os itens ou mantém separada. */
    private fun escolherDestino(mesaAtual: Int, mesaDestino: Int) {
        lifecycleScope.launch {
            val abertas = viewModel.outrasComandasDaMesa(mesaDestino)
            val mesmaMesa = mesaDestino == mesaAtual
            if (abertas.isEmpty()) {
                if (mesmaMesa) avisar(R.string.mesma_mesa) else viewModel.transferir(mesaDestino, juntarComId = null)
                return@launch
            }
            val opcoes = abertas.map {
                getString(R.string.juntar_com, it.comanda.numero, Moeda.formatar(it.totalCentavos))
            } + if (mesmaMesa) emptyList() else listOf(getString(R.string.manter_separada))
            MaterialAlertDialogBuilder(this@ComandaActivity)
                .setTitle(getString(R.string.mesa_ja_ocupada, mesaDestino))
                .setItems(opcoes.toTypedArray()) { _, indice ->
                    viewModel.transferir(mesaDestino, abertas.getOrNull(indice)?.comanda?.id)
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
    }

    // ---------- Pagamento ----------

    private fun efetuarPagamento() {
        val estado = viewModel.estado.value
        val comanda = estado.comanda ?: return
        if (estado.itens.isEmpty()) {
            val ehMesa = comanda.tipo == br.com.murupi.comandas.data.model.TipoComanda.MESA
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.pagamento_comanda, comanda.tituloBarra(this)))
                .setMessage(if (ehMesa) R.string.liberar_sem_consumo else R.string.cancelar_sem_consumo)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(
                    if (ehMesa) R.string.liberar_mesa else R.string.cancelar_comanda
                ) { _, _ -> viewModel.liberarSemConsumo() }
                .show()
            return
        }

        val formas = FormaPagamento.values()
        var formaSelecionada = FormaPagamento.DINHEIRO
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pagamento_comanda, comanda.tituloBarra(this)))
            .setMessage(getString(R.string.pagamento_total_e_forma, Moeda.formatar(estado.totalCentavos)))
            .setSingleChoiceItems(formas.map { it.descricao }.toTypedArray(), 0) { _, indice ->
                formaSelecionada = formas[indice]
            }
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.confirmar_pagamento) { _, _ ->
                viewModel.fecharComanda(listOf(formaSelecionada), estado.totalCentavos)
            }
            .show()
    }

    companion object {
        private const val EXTRA_COMANDA_ID = "comanda_id"
        private const val EXTRA_CONSULTA_HISTORICO = "consulta_historico"
        private const val MAX_PESSOAS = 99

        fun intentPara(context: Context, comandaId: Long, consultaHistorico: Boolean = false): Intent =
            Intent(context, ComandaActivity::class.java)
                .putExtra(EXTRA_COMANDA_ID, comandaId)
                .putExtra(EXTRA_CONSULTA_HISTORICO, consultaHistorico)
    }
}
