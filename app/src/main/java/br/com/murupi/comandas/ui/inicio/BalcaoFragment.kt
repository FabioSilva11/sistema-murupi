package br.com.murupi.comandas.ui.inicio

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.databinding.FragmentAvulsasBinding
import br.com.murupi.comandas.ui.comanda.ComandaActivity
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.pedirTexto
import br.com.murupi.comandas.ui.common.viewModelsDaAtividade
import kotlinx.coroutines.launch

/** Aba Balcão: pede o nome do cliente (opcional) e segue o fluxo das mesas. */
class BalcaoFragment : Fragment() {

    private var binding: FragmentAvulsasBinding? = null
    private val viewModel by viewModelsDaAtividade { InicioViewModel(it.comandas) }
    private val adapter = ResumoComandaAdapter(
        detalhe = { it.comanda.nomeCliente },
        aoTocar = { abrirComanda(it.comanda.id) }
    )

    private var abrindo = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentAvulsasBinding.inflate(inflater, container, false)
        binding = b
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.textVazio.setText(R.string.vazio_balcao)
        b.fab.setOnClickListener { pedirNome() }
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.coletar {
            viewModel.balcao.collect { lista ->
                adapter.submitList(lista)
                binding?.textVazio?.isVisible = lista.isEmpty()
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

    /** Nome do cliente é opcional no balcão: vazio abre o pedido sem nome. */
    private fun pedirNome() {
        requireContext().pedirTexto(
            titulo = getString(R.string.aba_balcao),
            rotulo = getString(R.string.nome_cliente),
            tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            validar = { null }
        ) { nome -> novoPedido(nome) }
    }

    private fun novoPedido(nome: String) {
        if (abrindo) return
        abrindo = true
        lifecycleScope.launch {
            abrirComanda(viewModel.abrirAvulsa(TipoComanda.BALCAO, nome, "").id, ignorarTrava = true)
        }
    }

    private fun abrirComanda(id: Long, ignorarTrava: Boolean = false) {
        if (abrindo && !ignorarTrava) return
        abrindo = true
        startActivity(ComandaActivity.intentPara(requireContext(), id))
    }
}
