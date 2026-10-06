package br.com.murupi.comandas.ui.comanda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.StatusComanda
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.databinding.ActivityComandaBinding
import br.com.murupi.comandas.databinding.DialogItemBinding
import br.com.murupi.comandas.databinding.DialogPagamentoBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.FormularioItem
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.pedirTexto
import br.com.murupi.comandas.ui.common.tituloBarra
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.ui.impressora.ImpressorasActivity
import br.com.murupi.comandas.ui.produto.IncluirProdutoActivity
import br.com.murupi.comandas.util.Moeda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Comanda "Mesa X.Y": itens lançados, total e o menu de impressão/pagamento/transferência. */
class ComandaActivity : BaseActivity() {

    private lateinit var binding: ActivityComandaBinding
    private val comandaId by lazy { intent.getLongExtra(EXTRA_COMANDA_ID, 0L) }
    private val viewModel by viewModelsDoApp { ComandaViewModel(comandaId, it.comandas, it.impressao) }
    private val adapter = ItemComandaAdapter(aoTocar = ::editarItem)

    /** Comanda fechada aberta pelo histórico: mostra tudo, mas não deixa alterar nada. */
    private var somenteLeitura = false

    override val ancoraSnackbar: View get() = binding.fab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.barraTotal.aplicarInsets(base = true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
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
            encerrar()
            return
        }
        // Fechou agora (pagamento ou espelho): sai com o aviso. Fechada antiga: abre para consulta.
        if (comanda.status != StatusComanda.ABERTA && viewModel.encerramento != null) {
            encerrar()
            return
        }
        somenteLeitura = comanda.status != StatusComanda.ABERTA
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
        binding.textPendentes.isVisible = estado.pendentes > 0
        binding.textPendentes.text =
            resources.getQuantityString(R.plurals.itens_pendentes, estado.pendentes, estado.pendentes)
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
            is ComandaEvento.AbrirComanda -> {
                startActivity(intentPara(this, evento.id))
                finish()
            }
        }
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
        menu.findItem(R.id.action_reabrir)?.isVisible = somenteLeitura
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
            estado.itens.isEmpty() -> avisar(R.string.comanda_sem_itens)
            estado.pendentes > 0 -> mostrarPreviaProducao(reimprimirTudo = false)
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
            mostrarPrevia(R.string.imprimir_producao, previas) {
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
            avisar(R.string.impressao_ok, resultado.envios.joinToString { it.impressora.nome })
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
        if (!item.enviadoProducao) {
            viewModel.removerItem(item)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remover_item)
            .setMessage(getString(R.string.remover_item_enviado, item.nome))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.remover) { _, _ -> viewModel.removerItem(item) }
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

        val total = estado.totalCentavos
        val b = DialogPagamentoBinding.inflate(layoutInflater)
        b.textTotal.text = Moeda.formatar(total)
        b.textPorPessoa.isVisible = comanda.pessoas > 1
        b.textPorPessoa.text =
            getString(R.string.valor_por_pessoa, comanda.pessoas, Moeda.formatar(Moeda.dividir(total, comanda.pessoas)))
        b.textAvisoPendentes.isVisible = estado.pendentes > 0
        b.textAvisoPendentes.text =
            resources.getQuantityString(R.plurals.aviso_pendentes_pagamento, estado.pendentes, estado.pendentes)

        fun atualizarTroco() {
            val dinheiro = b.grupoForma.checkedChipId == R.id.chipDinheiro
            b.layoutRecebido.isVisible = dinheiro
            val recebido = Moeda.converter(b.editRecebido.text?.toString().orEmpty())
            b.textTroco.isVisible = dinheiro && recebido != null && recebido >= total
            if (recebido != null) b.textTroco.text = getString(R.string.troco, Moeda.formatar(recebido - total))
        }
        b.grupoForma.setOnCheckedStateChangeListener { _, _ ->
            b.textErroForma.isVisible = false
            atualizarTroco()
        }
        b.editRecebido.doAfterTextChanged {
            b.layoutRecebido.error = null
            atualizarTroco()
        }
        atualizarTroco()

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pagamento_comanda, comanda.tituloBarra(this)))
            .setView(b.root)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.confirmar_pagamento, null)
            .create()
        dialogo.aoConfirmar {
            val forma = when (b.grupoForma.checkedChipId) {
                R.id.chipDinheiro -> FormaPagamento.DINHEIRO
                R.id.chipDebito -> FormaPagamento.DEBITO
                R.id.chipCredito -> FormaPagamento.CREDITO
                R.id.chipPix -> FormaPagamento.PIX
                else -> null
            }
            if (forma == null) {
                b.textErroForma.isVisible = true
                return@aoConfirmar false
            }
            val textoRecebido = b.editRecebido.text?.toString().orEmpty()
            if (forma == FormaPagamento.DINHEIRO && textoRecebido.isNotBlank()) {
                val recebido = Moeda.converter(textoRecebido)
                if (recebido == null || recebido < total) {
                    b.layoutRecebido.error = getString(R.string.valor_insuficiente)
                    return@aoConfirmar false
                }
            }
            viewModel.fecharComanda(forma, total)
            true
        }
        dialogo.show()
    }

    companion object {
        private const val EXTRA_COMANDA_ID = "comanda_id"
        private const val MAX_PESSOAS = 99

        fun intentPara(context: Context, comandaId: Long): Intent =
            Intent(context, ComandaActivity::class.java).putExtra(EXTRA_COMANDA_ID, comandaId)
    }
}
