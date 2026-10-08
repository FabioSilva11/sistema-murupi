package br.com.murupi.comandas.ui.historico

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.formasPagamentoDescricao
import br.com.murupi.comandas.databinding.ItemHistoricoVendaBinding
import br.com.murupi.comandas.ui.common.tituloBarra
import br.com.murupi.comandas.util.DataHora
import br.com.murupi.comandas.util.Moeda

/**
 * Venda do histórico: origem, forma de pagamento, data/hora, itens e total.
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
        val resumo = venda.resumo
        val comanda = resumo.comanda
        val contexto = holder.itemView.context
        with(holder.b) {
            textTitulo.text = comanda.tituloBarra(contexto)
            chipForma.text = comanda.formasPagamentoDescricao
            chipForma.visibility = if (chipForma.text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            textDetalhe.text = listOfNotNull(
                comanda.fechadaEm?.let(DataHora::curta),
                contexto.resources.getQuantityString(R.plurals.itens, resumo.qtdItens, resumo.qtdItens),
                comanda.pessoas.takeIf { it > 0 }?.let {
                    contexto.resources.getQuantityString(R.plurals.pessoas, it, it)
                },
                comanda.endereco.takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            val produtos = venda.itens.joinToString(" · ") { item ->
                buildString {
                    append(item.quantidade).append("× ").append(item.nome)
                    item.observacao.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
            }
            textProdutos.visibility = if (produtos.isEmpty()) View.GONE else View.VISIBLE
            textProdutos.text = if (produtos.isEmpty()) {
                contexto.getString(R.string.sem_itens_na_venda)
            } else {
                contexto.getString(R.string.itens_da_venda, produtos)
            }
            textTotal.text = Moeda.formatar(resumo.totalCentavos)
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
