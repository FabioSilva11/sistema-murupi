package br.com.murupi.comandas.ui.impressora

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Setor
import br.com.murupi.comandas.databinding.ItemRoteamentoBinding
import br.com.murupi.comandas.ui.common.Cores

class RoteamentoAdapter(
    private val aoTocar: (Categoria) -> Unit
) : ListAdapter<Categoria, RoteamentoAdapter.RoteamentoViewHolder>(RoteamentoDiff) {

    class RoteamentoViewHolder(val b: ItemRoteamentoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RoteamentoViewHolder(ItemRoteamentoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RoteamentoViewHolder, position: Int) {
        val categoria = getItem(position)
        val contexto = holder.itemView.context
        with(holder.b) {
            viewCor.backgroundTintList = ColorStateList.valueOf(Cores.converter(categoria.cor))
            textNome.text = categoria.nome
            textSetor.text = listOfNotNull(
                if (categoria.setor == Setor.REFRIGERANTES) contexto.getString(R.string.setor_refrigerantes_curto)
                else categoria.setor.descricao,
                contexto.getString(R.string.flag_demanda).takeIf { categoria.demanda },
                contexto.getString(R.string.flag_opcoes_suco).takeIf { categoria.perguntarOpcoesSuco }
            ).joinToString(" · ")
            root.setOnClickListener { aoTocar(categoria) }
        }
    }
}

private object RoteamentoDiff : DiffUtil.ItemCallback<Categoria>() {
    override fun areItemsTheSame(antiga: Categoria, nova: Categoria) = antiga.id == nova.id
    override fun areContentsTheSame(antiga: Categoria, nova: Categoria) = antiga == nova
}
