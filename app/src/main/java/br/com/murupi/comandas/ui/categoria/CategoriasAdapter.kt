package br.com.murupi.comandas.ui.categoria

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Setor
import br.com.murupi.comandas.databinding.ItemGerenciaCategoriaBinding
import br.com.murupi.comandas.ui.common.Cores

class CategoriasAdapter(
    private val aoTocar: (Categoria) -> Unit
) : ListAdapter<Categoria, CategoriasAdapter.CategoriaViewHolder>(CategoriasDiff) {

    private var contagens: Map<Long, Int> = emptyMap()

    fun setContagens(novas: Map<Long, Int>) {
        contagens = novas
        notifyItemRangeChanged(0, itemCount)
    }

    class CategoriaViewHolder(val b: ItemGerenciaCategoriaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CategoriaViewHolder(ItemGerenciaCategoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        val categoria = getItem(position)
        val contexto = holder.itemView.context
        with(holder.b) {
            viewCor.backgroundTintList = ColorStateList.valueOf(Cores.converter(categoria.cor))
            textNome.text = categoria.nome
            val qtd = contagens[categoria.id] ?: 0
            textDetalhe.text = (listOfNotNull(
                if (categoria.setor == Setor.REFRIGERANTES) contexto.getString(R.string.setor_refrigerantes_curto)
                else categoria.setor.descricao,
                contexto.getString(R.string.flag_demanda).takeIf { categoria.demanda },
                contexto.getString(R.string.flag_opcoes_suco).takeIf { categoria.perguntarOpcoesSuco }
            ) + contexto.resources.getQuantityString(
                R.plurals.produtos_categoria, qtd, qtd
            )).joinToString(" · ")
            textDetalhe.isVisible = textDetalhe.text.isNotEmpty()
            root.setOnClickListener { aoTocar(categoria) }
        }
    }
}

private object CategoriasDiff : DiffUtil.ItemCallback<Categoria>() {
    override fun areItemsTheSame(antiga: Categoria, nova: Categoria) = antiga.id == nova.id
    override fun areContentsTheSame(antiga: Categoria, nova: Categoria) = antiga == nova
}
