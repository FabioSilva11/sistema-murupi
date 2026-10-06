package br.com.murupi.comandas.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.databinding.FragmentMesasBinding
import br.com.murupi.comandas.ui.comanda.ComandaActivity
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.viewModelsDaAtividade
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Aba Mesas: grade com as 60 mesas (verde = livre, azul = ocupada). */
class MesasFragment : Fragment() {

    private var binding: FragmentMesasBinding? = null
    private val viewModel by viewModelsDaAtividade { InicioViewModel(it.comandas) }
    private val adapter = MesaAdapter(aoTocar = ::aoTocarMesa, aoSegurar = ::escolherComanda)

    /** Evita abrir duas comandas com toques repetidos enquanto a próxima tela carrega. */
    private var abrindo = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentMesasBinding.inflate(inflater, container, false)
        binding = b
        b.recycler.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.colunas_mesas))
        b.recycler.adapter = adapter
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.coletar {
            viewModel.mesas.collect { mesas ->
                adapter.submitList(mesas)
                binding?.textLivres?.text = getString(R.string.legenda_livres, mesas.count { !it.ocupada })
                binding?.textOcupadas?.text = getString(R.string.legenda_ocupadas, mesas.count { it.ocupada })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        abrindo = false
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun aoTocarMesa(mesa: MesaUi) {
        when (mesa.comandas.size) {
            1 -> abrirComanda(mesa.comandas.first().comanda.id)
            // Sem aberta: mostra as últimas fechadas em vermelho, ou abre uma nova direto.
            0 -> lifecycleScope.launch {
                if (viewModel.fechadasDaMesa(mesa.numero).isEmpty()) abrirNovaComanda(mesa.numero)
                else escolherComanda(mesa)
            }
            else -> escolherComanda(mesa)
        }
    }

    /** Lista as comandas da mesa (abertas + últimas fechadas em vermelho) e permite abrir outra. */
    private fun escolherComanda(mesa: MesaUi) {
        lifecycleScope.launch {
            val opcoes = mesa.comandas.map { OpcaoComanda.Existente(it) } +
                viewModel.fechadasDaMesa(mesa.numero).map { OpcaoComanda.Existente(it) } +
                OpcaoComanda.Nova("${mesa.numero}.${mesa.proximaSequencia}")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mesa_n, mesa.numero))
                .setAdapter(EscolhaComandaAdapter(requireContext(), opcoes)) { _, indice ->
                    when (val opcao = opcoes[indice]) {
                        is OpcaoComanda.Existente -> abrirComanda(opcao.resumo.comanda.id)
                        is OpcaoComanda.Nova -> abrirNovaComanda(mesa.numero)
                    }
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }
    }

    private fun abrirNovaComanda(mesa: Int) {
        if (abrindo) return
        abrindo = true
        lifecycleScope.launch { abrirComanda(viewModel.abrirNovaComanda(mesa).id, ignorarTrava = true) }
    }

    private fun abrirComanda(id: Long, ignorarTrava: Boolean = false) {
        if (abrindo && !ignorarTrava) return
        abrindo = true
        startActivity(ComandaActivity.intentPara(requireContext(), id))
    }
}
