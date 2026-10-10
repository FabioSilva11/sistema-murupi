package br.com.murupi.comandas.ui.historico

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.databinding.ItemHistoricoVendaBinding
import br.com.murupi.comandas.ui.common.tituloBarra
import br.com.murupi.comandas.util.Moeda

/**
 * Venda do histórico em grade compacta: origem e total.
 * Toque abre para consulta; segurar exclui.
 */
class HistoricoVendasAdapter(
    private val aoTocar: (VendaHistorico) -> Unit,
    private val aoPressionar: (VendaHistorico) -> Unit
) : ListAdapter<VendaHistorico, HistoricoVendasAdapter.VendaViewHolder>(VendaDiff) {

    class VendaViewHolder(val b: ItemHistoricoVendaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VendaViewHolder(ItemHistoricoVendaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VendaViewHolder, position: Int) {
        val venda = getItem(position)
        val comanda = venda.resumo.comanda
        val contexto = holder.itemView.context
        with(holder.b) {
            textTitulo.text = comanda.tituloBarra(contexto)
            textTotal.text = Moeda.formatar(venda.resumo.totalCentavos)
            root.setOnClickListener { aoTocar(venda) }
            root.setOnLongClickListener {
                aoPressionar(venda)
                true
            }
        }
    }
}

private object VendaDiff : DiffUtil.ItemCallback<VendaHistorico>() {
    override fun areItemsTheSame(antigo: VendaHistorico, novo: VendaHistorico) =
        antigo.resumo.comanda.id == novo.resumo.comanda.id
    override fun areContentsTheSame(antigo: VendaHistorico, novo: VendaHistorico) = antigo == novo
}
