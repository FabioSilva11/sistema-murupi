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
    private val adapter = MesaAdapter(aoTocar = ::aoTocarMesa)

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
        lifecycleScope.launch { escolherComanda(mesa) }
    }

    /** Seletor com abertas em azul e fechadas em vermelho; '+' cria uma nova comanda. */
    private suspend fun escolherComanda(mesa: MesaUi) {
        val abertas = mesa.comandas.map { OpcaoComanda.Existente(it) }
        val fechadas = viewModel.fechadasDaMesa(mesa.numero).map { OpcaoComanda.Existente(it) }
        val opcoes = abertas + fechadas
        val conteudo = layoutInflater.inflate(R.layout.dialog_escolha_comanda, null)
        conteudo.findViewById<android.widget.TextView>(R.id.tituloComandas)
            .text = getString(R.string.escolha_comanda_titulo, mesa.numero)
        val lista = conteudo.findViewById<android.widget.GridView>(R.id.gradeComandas)
        lista.numColumns = maxOf(1, opcoes.size.coerceAtMost(4))
        lista.adapter = EscolhaComandaAdapter(requireContext(), opcoes)
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setView(conteudo)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        lista.setOnItemClickListener { _, _, indice, _ ->
                val opcao = opcoes[indice] as OpcaoComanda.Existente
                abrirComanda(opcao.resumo.comanda.id)
                dialogo.dismiss()
        }
        conteudo.findViewById<View>(R.id.botaoNovaComanda).setOnClickListener {
            dialogo.dismiss()
            abrirNovaComanda(mesa.numero)
        }
        dialogo.show()
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
