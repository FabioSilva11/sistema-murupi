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

/** Aba Delivery: pede nome e endereço e segue o mesmo fluxo das mesas. */
class DeliveryFragment : Fragment() {

    private var binding: FragmentAvulsasBinding? = null
    private val viewModel by viewModelsDaAtividade { InicioViewModel(it.comandas) }
    private val adapter = ResumoComandaAdapter(
        detalhe = { it.comanda.endereco },
        aoTocar = { abrirComanda(it.comanda.id) }
    )

    private var abrindo = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentAvulsasBinding.inflate(inflater, container, false)
        binding = b
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.textVazio.setText(R.string.vazio_delivery)
        b.fab.setOnClickListener { pedirDados() }
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.coletar {
            viewModel.delivery.collect { lista ->
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

    private fun pedirDados() {
        requireContext().pedirTexto(
            titulo = getString(R.string.aba_delivery),
            rotulo = getString(R.string.nome_cliente),
            tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            validar = { texto -> if (texto.isEmpty()) getString(R.string.informe_nome) else null }
        ) { nome -> pedirEndereco(nome) }
    }

    private fun pedirEndereco(nome: String) {
        requireContext().pedirTexto(
            titulo = nome,
            rotulo = getString(R.string.endereco_entrega),
            tipoEntrada = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            validar = { texto -> if (texto.isEmpty()) getString(R.string.informe_endereco) else null }
        ) { endereco -> novoPedido(nome, endereco) }
    }

    private fun novoPedido(nome: String, endereco: String) {
        if (abrindo) return
        abrindo = true
        lifecycleScope.launch {
            abrirComanda(viewModel.abrirAvulsa(TipoComanda.DELIVERY, nome, endereco).id, ignorarTrava = true)
        }
    }

    private fun abrirComanda(id: Long, ignorarTrava: Boolean = false) {
        if (abrindo && !ignorarTrava) return
        abrindo = true
        startActivity(ComandaActivity.intentPara(requireContext(), id))
    }
}
