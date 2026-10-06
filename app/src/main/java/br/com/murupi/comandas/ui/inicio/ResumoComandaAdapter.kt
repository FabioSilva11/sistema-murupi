package br.com.murupi.comandas.ui.inicio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.titulo
import br.com.murupi.comandas.databinding.ItemResumoComandaBinding
import br.com.murupi.comandas.util.Moeda

/** Lista de comandas de balcão/delivery: título, detalhe (nome/endereço) e total. */
class ResumoComandaAdapter(
    private val detalhe: (ComandaResumo) -> String,
    private val aoTocar: (ComandaResumo) -> Unit
) : ListAdapter<ComandaResumo, ResumoComandaAdapter.ResumoViewHolder>(ResumoDiff) {

    class ResumoViewHolder(val b: ItemResumoComandaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ResumoViewHolder(ItemResumoComandaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ResumoViewHolder, position: Int) {
        val resumo = getItem(position)
        val textoDetalhe = detalhe(resumo)
        with(holder.b) {
            textTitulo.text = resumo.comanda.titulo
            textDetalhe.isVisible = textoDetalhe.isNotEmpty()
            textDetalhe.text = textoDetalhe
            textTotal.text = Moeda.formatar(resumo.totalCentavos)
            root.setOnClickListener { aoTocar(resumo) }
        }
    }
}

private object ResumoDiff : DiffUtil.ItemCallback<ComandaResumo>() {
    override fun areItemsTheSame(antigo: ComandaResumo, novo: ComandaResumo) = antigo.comanda.id == novo.comanda.id
    override fun areContentsTheSame(antigo: ComandaResumo, novo: ComandaResumo) = antigo == novo
}
