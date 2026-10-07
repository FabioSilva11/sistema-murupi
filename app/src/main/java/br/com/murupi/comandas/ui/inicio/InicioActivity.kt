package br.com.murupi.comandas.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.google.android.material.tabs.TabLayoutMediator

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
            aba.setText(
                when (posicao) {
                    PAGINA_BALCAO -> R.string.aba_balcao
                    PAGINA_DELIVERY -> R.string.aba_delivery
                    else -> R.string.aba_mesas
                }
            )
        }.attach()
    }

    override fun onStart() {
        super.onStart()
        viewModel.limparComandasVazias()
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
            val versao = try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            } catch (_: Exception) {
                ""
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sobre)
                .setMessage(getString(R.string.sobre_mensagem, versao))
                .setPositiveButton(R.string.ok, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private companion object {
        const val PAGINA_MESAS = 0
        const val PAGINA_BALCAO = 1
        const val PAGINA_DELIVERY = 2
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
