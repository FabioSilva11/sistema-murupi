package br.com.murupi.comandas.ui.comanda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.totalCentavos
import br.com.murupi.comandas.databinding.ItemComandaBinding
import br.com.murupi.comandas.util.Moeda

class ItemComandaAdapter(
    private val aoTocar: (ItemComanda) -> Unit
) : ListAdapter<ItemComanda, ItemComandaAdapter.ItemViewHolder>(ItemComandaDiff) {

    class ItemViewHolder(val b: ItemComandaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ItemViewHolder(ItemComandaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        val contexto = holder.itemView.context
        with(holder.b) {
            textQuantidade.text = contexto.getString(R.string.quantidade_x, item.quantidade)
            textNome.text = item.nome
            val precoDescricao = if (item.precoUnitarioCentavos <= 0) {
                contexto.getString(R.string.preco_livre)
            } else {
                Moeda.formatar(item.precoUnitarioCentavos)
            }
            textDetalhe.text = contexto.getString(
                R.string.detalhe_item, precoDescricao, item.categoriaNome
            )
            textObservacao.isVisible = item.observacao.isNotEmpty()
            textObservacao.text = contexto.getString(R.string.obs_valor, item.observacao)
            textTotal.text = Moeda.formatar(item.totalCentavos)
            textStatus.setText(if (item.enviadoProducao) R.string.status_enviado else R.string.status_pendente)
            textStatus.setTextColor(
                ContextCompat.getColor(contexto, if (item.enviadoProducao) R.color.enviado else R.color.pendente)
            )
            root.setOnClickListener { aoTocar(item) }
        }
    }
}

private object ItemComandaDiff : DiffUtil.ItemCallback<ItemComanda>() {
    override fun areItemsTheSame(antigo: ItemComanda, novo: ItemComanda) = antigo.id == novo.id
    override fun areContentsTheSame(antigo: ItemComanda, novo: ItemComanda) = antigo == novo
}
