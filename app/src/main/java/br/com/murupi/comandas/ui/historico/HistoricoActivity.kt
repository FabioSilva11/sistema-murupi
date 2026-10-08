package br.com.murupi.comandas.ui.historico

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.databinding.ActivityHistoricoBinding
import br.com.murupi.comandas.ui.comanda.ComandaActivity
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.data.model.titulo
import br.com.murupi.comandas.util.Moeda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Histórico de vendas: contas fechadas, toque abre para consulta. */
class HistoricoActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoricoBinding
    private val viewModel by viewModelsDoApp { HistoricoViewModel(it.comandas, it.impressao, it.impressoras) }
    private val adapter = HistoricoVendasAdapter(
        aoTocar = {
            startActivity(ComandaActivity.intentPara(this, it.resumo.comanda.id, consultaHistorico = true))
        },
        aoPressionar = { venda ->
            confirmarExclusao(venda.resumo.comanda.id, venda.resumo.comanda.titulo)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoricoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.recycler.aplicarInsets(base = true)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        coletar {
            launch {
                viewModel.contas.collect { lista ->
                    adapter.submitList(lista) { invalidateOptionsMenu() }
                    binding.textVazio.isVisible = lista.isEmpty()
                    binding.cardResumo.isVisible = lista.isNotEmpty()
                }
            }
            launch {
                viewModel.resumo.collect { resumo ->
                    binding.textTotalVendido.text = Moeda.formatar(resumo.totalCentavos)
                    binding.textQtdVendas.text = resources.getQuantityString(
                        R.plurals.vendas, resumo.qtd, resumo.qtd
                    )
                    binding.textTicketMedio.text = Moeda.formatar(resumo.ticketMedioCentavos)
                }
            }
            launch { viewModel.eventos.collect { avisar(it) } }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_historico, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_imprimir_historico)?.isVisible = adapter.itemCount > 0
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_imprimir_historico -> {
            escolherImpressora()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Usa uma das impressoras cadastradas para sair o histórico completo + saldo total. */
    private fun escolherImpressora() {
        lifecycleScope.launch {
            val ativas = viewModel.impressorasAtivas()
            if (ativas.isEmpty()) {
                avisar(R.string.sem_impressora_historico)
                return@launch
            }
            val nomes = ativas.map { "${it.nome} (${it.papel.descricao})" }.toTypedArray()
            MaterialAlertDialogBuilder(this@HistoricoActivity)
                .setTitle(R.string.escolher_impressora)
                .setItems(nomes) { _, indice -> viewModel.imprimirHistorico(ativas[indice]) }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
    }

    private fun confirmarExclusao(comandaId: Long, titulo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.excluir_venda_historico)
            .setMessage(getString(R.string.excluir_venda_historico_pergunta, titulo))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir(comandaId) }
            .show()
    }
}
