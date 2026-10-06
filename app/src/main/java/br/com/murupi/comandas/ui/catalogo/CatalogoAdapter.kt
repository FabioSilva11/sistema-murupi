package br.com.murupi.comandas.ui.catalogo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.model.esgotado
import br.com.murupi.comandas.data.model.precoLivre
import br.com.murupi.comandas.databinding.ItemCatalogoBinding
import br.com.murupi.comandas.ui.common.Cores
import br.com.murupi.comandas.util.Moeda

class CatalogoAdapter(
    private val aoTocar: (ProdutoItem) -> Unit
) : ListAdapter<ProdutoItem, CatalogoAdapter.CatalogoViewHolder>(CatalogoDiff) {

    class CatalogoViewHolder(val b: ItemCatalogoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CatalogoViewHolder(ItemCatalogoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CatalogoViewHolder, position: Int) {
        val item = getItem(position)
        val produto = item.produto
        val contexto = holder.itemView.context
        with(holder.b) {
            faixa.setBackgroundColor(Cores.converter(item.categoria.cor))
            textNome.text = produto.nome
            textDetalhe.text = listOfNotNull(
                produto.descricao.takeIf { it.isNotBlank() },
                item.categoria.nome,
                produto.grupo?.let { contexto.getString(R.string.grupo_valor, it) },
                if (produto.precoLivre) contexto.getString(R.string.preco_livre) else Moeda.formatar(produto.precoCentavos),
                contexto.getString(R.string.de_n_em_n, produto.multiplo).takeIf { produto.multiplo > 1 }
            ).joinToString(" · ")

            val estoque = produto.estoque
            textEstoque.text = when {
                estoque == null -> contexto.getString(R.string.sem_controle_estoque)
                produto.esgotado -> contexto.getString(R.string.esgotado)
                else -> contexto.getString(R.string.estoque_n, estoque)
            }
            textEstoque.setTextColor(
                ContextCompat.getColor(contexto, if (produto.esgotado) R.color.observacao else R.color.texto_secundario)
            )
            textOculto.isVisible = !produto.ativo
            root.alpha = if (produto.ativo) 1f else 0.6f
            root.setOnClickListener { aoTocar(item) }
        }
    }
}

private object CatalogoDiff : DiffUtil.ItemCallback<ProdutoItem>() {
    override fun areItemsTheSame(antigo: ProdutoItem, novo: ProdutoItem) = antigo.produto.id == novo.produto.id
    override fun areContentsTheSame(antigo: ProdutoItem, novo: ProdutoItem) = antigo == novo
}
