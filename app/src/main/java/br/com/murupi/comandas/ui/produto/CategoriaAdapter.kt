package br.com.murupi.comandas.ui.produto

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.databinding.ItemCategoriaBinding
import br.com.murupi.comandas.ui.common.Cores

class CategoriaAdapter(
    private val aoTocar: (Categoria) -> Unit
) : ListAdapter<Categoria, CategoriaAdapter.CategoriaViewHolder>(CategoriaDiff) {

    class CategoriaViewHolder(val b: ItemCategoriaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CategoriaViewHolder(ItemCategoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        val categoria = getItem(position)
        val cor = Cores.converter(categoria.cor)
        with(holder.b) {
            card.setCardBackgroundColor(Color.WHITE)
            card.strokeColor = cor
            viewCor.backgroundTintList = ColorStateList.valueOf(cor)
            textNome.text = categoria.nome
            textNome.setTextColor(Color.rgb(35, 45, 60))
            card.setOnClickListener { aoTocar(categoria) }
        }
    }
}

private object CategoriaDiff : DiffUtil.ItemCallback<Categoria>() {
    override fun areItemsTheSame(antiga: Categoria, nova: Categoria) = antiga.id == nova.id
    override fun areContentsTheSame(antiga: Categoria, nova: Categoria) = antiga == nova
}
