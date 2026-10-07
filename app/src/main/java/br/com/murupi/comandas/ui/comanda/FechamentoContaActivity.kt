package br.com.murupi.comandas.ui.comanda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.model.rotuloImpressao
import br.com.murupi.comandas.databinding.ActivityFechamentoContaBinding
import br.com.murupi.comandas.databinding.ItemPagamentoParcialBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.util.Moeda
import kotlinx.coroutines.launch

/**
 * Activity dedicada para conferência, aplicação de descontos / taxa de serviço
 * e recebimento de pagamentos (inclusive divididos em múltiplas formas).
 */
class FechamentoContaActivity : BaseActivity() {

    private lateinit var binding: ActivityFechamentoContaBinding
    private val viewModel by viewModelsDoApp { ComandaViewModel(comandaId, it.comandas, it.impressao) }
    private var comandaId: Long = 0

    private var comandaAtual: Comanda? = null
    private var itensAtuais: List<ItemComanda> = emptyList()

    private data class PagamentoItem(val forma: FormaPagamento, val valorCentavos: Long)
    private val pagamentos = mutableListOf<PagamentoItem>()

    override val ancoraSnackbar: View? get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        comandaId = intent.getLongExtra(EXTRA_COMANDA_ID, 0L)
        if (comandaId == 0L) {
            finish()
            return
        }

        binding = ActivityFechamentoContaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        supportActionBar?.title = getString(R.string.titulo_fechamento_conta)

        binding.conteudo.aplicarInsets(base = true)

        configurarOuvintes()

        lifecycleScope.launch {
            viewModel.estado.collect { estado ->
                if (!estado.carregado) return@collect
                val comanda = estado.comanda
                if (comanda == null) {
                    finish()
                    return@collect
                }
                comandaAtual = comanda
                itensAtuais = estado.itens
                atualizarInterface()
            }
        }
    }

    private fun configurarOuvintes() {
        binding.switchTaxa.setOnCheckedChangeListener { _, _ ->
            atualizarCalculos()
        }

        binding.editDesconto.doAfterTextChanged {
            binding.layoutDesconto.error = null
            atualizarCalculos()
        }

        binding.editValorPagamento.doAfterTextChanged {
            binding.layoutValorPagamento.error = null
        }

        binding.btnAdicionarPagamento.setOnClickListener {
            adicionarPagamento()
        }

        binding.btnFinalizar.setOnClickListener {
            finalizarFechamento()
        }
    }

    private fun atualizarInterface() {
        val comanda = comandaAtual ?: return
        binding.textIdentificacao.text = comanda.rotuloImpressao
        binding.textPessoas.isVisible = comanda.pessoas > 0

        val subtotal = itensAtuais.sumOf { it.quantidade * it.precoUnitarioCentavos }
        val percentualTaxa = Restaurante.TAXA_SERVICO_PERCENTUAL
        val valorTaxa = Moeda.taxaServico(subtotal, percentualTaxa)

        binding.switchTaxa.isVisible = percentualTaxa > 0
        if (percentualTaxa > 0) {
            binding.switchTaxa.text = getString(R.string.taxa_configurada, percentualTaxa, Moeda.formatar(valorTaxa))
        }

        if (comanda.pessoas > 0) {
            binding.textPessoas.text = getString(
                R.string.valor_por_pessoa,
                comanda.pessoas,
                Moeda.formatar(Moeda.dividir(subtotal, comanda.pessoas))
            )
        }

        atualizarCalculos()
    }

    private fun calcularTotalFinal(): Long {
        val subtotal = itensAtuais.sumOf { it.quantidade * it.precoUnitarioCentavos }
        val percentualTaxa = if (binding.switchTaxa.isChecked) Restaurante.TAXA_SERVICO_PERCENTUAL else 0
        val taxa = Moeda.taxaServico(subtotal, percentualTaxa)
        val descontoTexto = binding.editDesconto.text?.toString().orEmpty()
        val desconto = Moeda.converter(descontoTexto) ?: 0L
        return maxOf(0L, subtotal + taxa - desconto)
    }

    private fun atualizarCalculos() {
        val subtotal = itensAtuais.sumOf { it.quantidade * it.precoUnitarioCentavos }
        val totalFinal = calcularTotalFinal()
        val totalPago = pagamentos.sumOf { it.valorCentavos }
        val restante = maxOf(0L, totalFinal - totalPago)

        binding.textSubtotal.text = Moeda.formatar(subtotal)
        binding.textTotalGeral.text = Moeda.formatar(totalFinal)

        if (totalPago >= totalFinal) {
            binding.textRestante.isVisible = false
            val troco = totalPago - totalFinal
            binding.textTroco.isVisible = troco > 0
            binding.textTroco.text = getString(R.string.troco, Moeda.formatar(troco))
        } else {
            binding.textTroco.isVisible = false
            binding.textRestante.isVisible = true
            binding.textRestante.text = getString(R.string.restante_a_pagar) + ": " + Moeda.formatar(restante)
        }

        // Sugere o valor restante no campo de pagamento caso esteja vazio
        if (binding.editValorPagamento.text.isNullOrBlank() && restante > 0) {
            binding.editValorPagamento.setText(Moeda.paraCampo(restante))
        }

        renderizarListaPagamentos()
    }

    private fun adicionarPagamento() {
        val textoValor = binding.editValorPagamento.text?.toString().orEmpty()
        val valor = Moeda.converter(textoValor)
        if (valor == null || valor <= 0) {
            binding.layoutValorPagamento.error = getString(R.string.valor_invalido)
            return
        }

        val forma = when (binding.grupoFormas.checkedChipId) {
            R.id.chipDinheiro -> FormaPagamento.DINHEIRO
            R.id.chipPix -> FormaPagamento.PIX
            R.id.chipDebito -> FormaPagamento.DEBITO
            R.id.chipCredito -> FormaPagamento.CREDITO
            else -> FormaPagamento.DINHEIRO
        }

        pagamentos.add(PagamentoItem(forma, valor))
        binding.editValorPagamento.text = null

        atualizarCalculos()
    }

    private fun renderizarListaPagamentos() {
        binding.containerPagamentos.removeAllViews()
        binding.textSemPagamentos.isVisible = pagamentos.isEmpty()

        val inflater = LayoutInflater.from(this)
        pagamentos.forEachIndexed { index, item ->
            val bItem = ItemPagamentoParcialBinding.inflate(inflater, binding.containerPagamentos, false)
            bItem.textForma.text = item.forma.descricao
            bItem.textValor.text = Moeda.formatar(item.valorCentavos)
            bItem.btnRemover.setOnClickListener {
                pagamentos.removeAt(index)
                atualizarCalculos()
            }
            binding.containerPagamentos.addView(bItem.root)
        }
    }

    private fun finalizarFechamento() {
        val totalFinal = calcularTotalFinal()
        val totalPago = pagamentos.sumOf { it.valorCentavos }

        if (totalFinal > 0 && totalPago < totalFinal) {
            avisar(R.string.pagamento_incompleto)
            return
        }

        // Se nenhuma forma foi inserida separadamente mas o valor é 0, assume dinheiro
        val formaPrincipal = pagamentos.firstOrNull()?.forma ?: FormaPagamento.DINHEIRO

        viewModel.fecharComanda(formaPrincipal, totalFinal)

        Toast.makeText(this, R.string.comanda_paga, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val EXTRA_COMANDA_ID = "comanda_id"

        fun intentPara(context: Context, comandaId: Long): Intent =
            Intent(context, FechamentoContaActivity::class.java).putExtra(EXTRA_COMANDA_ID, comandaId)
    }
}
