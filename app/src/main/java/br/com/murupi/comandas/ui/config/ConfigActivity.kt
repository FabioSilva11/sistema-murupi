package br.com.murupi.comandas.ui.config

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.ConfigRestaurante
import br.com.murupi.comandas.databinding.ActivityConfigBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import kotlinx.coroutines.launch

/** Configurações do estabelecimento: tickets, mesas do salão e taxa de serviço. */
class ConfigActivity : BaseActivity() {

    private lateinit var binding: ActivityConfigBinding
    private val viewModel by viewModelsDoApp { ConfigViewModel(it.config) }

    override val ancoraSnackbar: View? get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.conteudo.aplicarInsets(base = true)

        lifecycleScope.launch {
            val config = viewModel.carregar()
                ?: ConfigRestaurante(nome = Restaurante.NOME, agradecimento = Restaurante.AGRADECIMENTO)
            preencher(config)
        }

        binding.btnSalvar.setOnClickListener { salvar() }
    }

    private fun preencher(config: ConfigRestaurante) {
        binding.editNome.setText(config.nome)
        binding.editAgradecimento.setText(config.agradecimento)
        binding.editMesas.setText(config.totalMesas.toString())
        binding.editTaxa.setText(config.taxaServicoPercentual.toString())
        viewModel.atual = config
    }

    private fun salvar() {
        val nome = binding.editNome.text?.toString().orEmpty().trim()
        if (nome.isEmpty()) {
            binding.layoutNome.error = getString(R.string.informe_nome_restaurante)
            return
        }
        val mesas = binding.editMesas.text?.toString()?.toIntOrNull()?.takeIf { it in 1..MESAS_MAXIMO }
        if (mesas == null) {
            binding.layoutMesas.error = getString(R.string.mesa_invalida, MESAS_MAXIMO)
            return
        }
        val taxa = binding.editTaxa.text?.toString()?.toIntOrNull()?.takeIf { it in 0..TAXA_MAXIMA }
        if (taxa == null) {
            binding.layoutTaxa.error = getString(R.string.taxa_invalida)
            return
        }
        viewModel.atual = viewModel.atual.copy(
            nome = nome,
            agradecimento = binding.editAgradecimento.text?.toString().orEmpty().trim(),
            totalMesas = mesas,
            taxaServicoPercentual = taxa
        )
        viewModel.salvar(viewModel.atual) {
            Restaurante.aplicar(viewModel.atual)
            avisar(R.string.config_salva)
        }
    }

    private companion object {
        const val MESAS_MAXIMO = 200
        const val TAXA_MAXIMA = 50
    }
}
