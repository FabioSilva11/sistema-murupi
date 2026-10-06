package br.com.murupi.comandas.ui.inicio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.databinding.ItemMesaBinding
import br.com.murupi.comandas.util.Moeda

class MesaAdapter(
    private val aoTocar: (MesaUi) -> Unit,
    private val aoSegurar: (MesaUi) -> Unit
) : ListAdapter<MesaUi, MesaAdapter.MesaViewHolder>(MesaDiff) {

    class MesaViewHolder(val b: ItemMesaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MesaViewHolder(ItemMesaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MesaViewHolder, position: Int) {
        val mesa = getItem(position)
        val contexto = holder.itemView.context
        with(holder.b) {
            textNumero.text = mesa.numero.toString()
            card.setCardBackgroundColor(
                ContextCompat.getColor(contexto, if (mesa.ocupada) R.color.mesa_ocupada else R.color.mesa_livre)
            )
            textInfo.text = when {
                !mesa.ocupada -> contexto.getString(R.string.mesa_livre)
                mesa.comandas.size > 1 ->
                    contexto.getString(R.string.mesa_info_varias, Moeda.formatar(mesa.totalCentavos), mesa.comandas.size)
                else -> Moeda.formatar(mesa.totalCentavos)
            }
            card.setOnClickListener { aoTocar(mesa) }
            card.setOnLongClickListener {
                aoSegurar(mesa)
                true
            }
        }
    }
}

private object MesaDiff : DiffUtil.ItemCallback<MesaUi>() {
    override fun areItemsTheSame(antigo: MesaUi, novo: MesaUi) = antigo.numero == novo.numero
    override fun areContentsTheSame(antigo: MesaUi, novo: MesaUi) = antigo == novo
}
