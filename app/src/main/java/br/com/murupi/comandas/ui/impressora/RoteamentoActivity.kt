package br.com.murupi.comandas.ui.impressora

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Setor
import br.com.murupi.comandas.databinding.ActivityRoteamentoBinding
import br.com.murupi.comandas.databinding.DialogRoteamentoBinding
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.app
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Define para qual impressora de produção vai cada categoria do cardápio. */
class RoteamentoActivity : BaseActivity() {

    private lateinit var binding: ActivityRoteamentoBinding
    private val adapter = RoteamentoAdapter(::editar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoteamentoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.recycler.aplicarInsets(base = true)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recycler.adapter = adapter

        coletar { app.cardapio.categorias().collect(adapter::submitList) }
    }

    private fun editar(categoria: Categoria) {
        val b = DialogRoteamentoBinding.inflate(layoutInflater)
        b.grupoSetor.check(
            when (categoria.setor) {
                Setor.COZINHA -> R.id.radioCozinha
                Setor.SUCOS -> R.id.radioSucos
                Setor.REFRIGERANTES -> R.id.radioRefrigerantes
            }
        )
        b.checkDemanda.isChecked = categoria.demanda
        b.checkOpcoesSuco.isChecked = categoria.perguntarOpcoesSuco

        MaterialAlertDialogBuilder(this)
            .setTitle(categoria.nome)
            .setView(b.root)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.salvar) { _, _ ->
                val setor = when (b.grupoSetor.checkedRadioButtonId) {
                    R.id.radioSucos -> Setor.SUCOS
                    R.id.radioRefrigerantes -> Setor.REFRIGERANTES
                    else -> Setor.COZINHA
                }
                val atualizada = categoria.copy(
                    setor = setor,
                    demanda = b.checkDemanda.isChecked,
                    perguntarOpcoesSuco = b.checkOpcoesSuco.isChecked
                )
                lifecycleScope.launch { app.cardapio.atualizarCategoria(atualizada) }
            }
            .show()
    }
}
