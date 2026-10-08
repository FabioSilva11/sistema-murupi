package br.com.murupi.comandas.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import br.com.murupi.comandas.R
import br.com.murupi.comandas.databinding.ActivityInicioBinding
import br.com.murupi.comandas.ui.catalogo.CatalogoActivity
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.ui.config.ConfigActivity
import br.com.murupi.comandas.ui.impressora.ImpressorasActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/** Tela inicial: abas Mesas, Balcão e Delivery com o mesmo fluxo de comanda. */
class InicioActivity : BaseActivity() {

    private lateinit var binding: ActivityInicioBinding
    private val viewModel by viewModelsDoApp { InicioViewModel(it.comandas) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInicioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = false)
        supportActionBar?.subtitle = getString(R.string.titulo_inicio)

        binding.pager.adapter = AbasAdapter(this)
        binding.pager.offscreenPageLimit = 2
        binding.pager.aplicarInsets(base = true)
        TabLayoutMediator(binding.abas, binding.pager) { aba, posicao ->
            when (posicao) {
                PAGINA_MESAS -> aba.setText(R.string.aba_mesas)
                PAGINA_BALCAO -> aba.setText(R.string.aba_balcao)
                PAGINA_DELIVERY -> aba.setText(R.string.aba_delivery)
            }
        }.attach()
    }

    override fun onStart() {
        super.onStart()
        viewModel.limparComandasVazias()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ABRIR_MESAS, false) && ::binding.isInitialized) {
            binding.pager.setCurrentItem(PAGINA_MESAS, false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_mesas, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_impressoras -> {
            startActivity(Intent(this, ImpressorasActivity::class.java))
            true
        }
        R.id.action_catalogo -> {
            startActivity(Intent(this, CatalogoActivity::class.java))
            true
        }
        R.id.action_historico -> {
            startActivity(Intent(this, br.com.murupi.comandas.ui.historico.HistoricoActivity::class.java))
            true
        }
        R.id.action_config -> {
            startActivity(Intent(this, ConfigActivity::class.java))
            true
        }
        R.id.action_sobre -> {
            abrirSobre()
            true
        }
        R.id.action_reset -> {
            confirmarReset()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmarReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resetar)
            .setMessage(R.string.resetar_pedidos_historico_pergunta)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.resetar) { _, _ ->
                lifecycleScope.launch {
                    viewModel.resetarPedidosEHistorico()
                    binding.pager.setCurrentItem(PAGINA_MESAS, false)
                    avisar(R.string.reset_concluido)
                }
            }
            .show()
    }

    private fun abrirSobre() {
        val versao = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
        val sobre = br.com.murupi.comandas.databinding.DialogSobreBinding.inflate(layoutInflater)
        sobre.textVersao.text = getString(R.string.sobre_versao, versao)
        sobre.botaoGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/FabioSilva11")))
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sobre)
            .setView(sobre.root)
            .setPositiveButton(R.string.fechar, null)
            .show()
    }

    companion object {
        const val PAGINA_MESAS = 0
        const val PAGINA_BALCAO = 1
        const val PAGINA_DELIVERY = 2

        private const val EXTRA_ABRIR_MESAS = "abrir_mesas_apos_impressao"

        fun intentParaMesas(context: android.content.Context): Intent =
            Intent(context, InicioActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ABRIR_MESAS, true)
    }
}

private class AbasAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        1 -> BalcaoFragment()
        2 -> DeliveryFragment()
        else -> MesasFragment()
    }
}
