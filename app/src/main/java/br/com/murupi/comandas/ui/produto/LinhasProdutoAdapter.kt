package br.com.murupi.comandas.ui.produto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.model.esgotado
import br.com.murupi.comandas.data.model.precoLivre
import br.com.murupi.comandas.data.model.rotuloNoGrupo
import br.com.murupi.comandas.databinding.ChipPreparoBinding
import br.com.murupi.comandas.databinding.ItemPreparoBinding
import br.com.murupi.comandas.databinding.ItemProdutoBinding
import br.com.murupi.comandas.databinding.ItemProdutoGrupoBinding
import br.com.murupi.comandas.ui.common.Cores
import br.com.murupi.comandas.ui.common.FormularioItem
import br.com.murupi.comandas.util.Moeda

class LinhasProdutoAdapter(
    private val aoTocarGrupo: (LinhaProduto.Grupo) -> Unit,
    private val aoTocarItem: (LinhaProduto.Item) -> Unit,
    private val aoEscolherPreparo: (ProdutoItem, String) -> Unit
) : ListAdapter<LinhaProduto, RecyclerView.ViewHolder>(LinhaDiff) {

    private class GrupoViewHolder(val b: ItemProdutoGrupoBinding) : RecyclerView.ViewHolder(b.root)
    private class ItemViewHolder(val b: ItemProdutoBinding) : RecyclerView.ViewHolder(b.root)
    private class PreparoViewHolder(val b: ItemPreparoBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LinhaProduto.Grupo -> TIPO_GRUPO
        is LinhaProduto.Item -> TIPO_ITEM
        is LinhaProduto.Preparo -> TIPO_PREPARO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_GRUPO -> GrupoViewHolder(ItemProdutoGrupoBinding.inflate(inflater, parent, false))
            TIPO_ITEM -> ItemViewHolder(ItemProdutoBinding.inflate(inflater, parent, false))
            else -> {
                val b = ItemPreparoBinding.inflate(inflater, parent, false)
                FormularioItem.OPCOES_SUCO.forEach { opcao ->
                    val chip = ChipPreparoBinding.inflate(inflater, b.grupoPreparo, false).root
                    chip.text = opcao
                    b.grupoPreparo.addView(chip)
                }
                PreparoViewHolder(b)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val linha = getItem(position)) {
            is LinhaProduto.Grupo -> vincularGrupo(holder as GrupoViewHolder, linha)
            is LinhaProduto.Item -> vincularItem(holder as ItemViewHolder, linha)
            is LinhaProduto.Preparo -> vincularPreparo(holder as PreparoViewHolder, linha)
        }
    }

    private fun vincularGrupo(holder: GrupoViewHolder, linha: LinhaProduto.Grupo) {
        val contexto = holder.itemView.context
        with(holder.b) {
            faixa.setBackgroundColor(Cores.converter(linha.categoria.cor))
            textNome.text = linha.nome
            textResumo.text = listOfNotNull(
                contexto.resources.getQuantityString(R.plurals.opcoes_grupo, linha.variantes, linha.variantes),
                linha.menorPrecoCentavos?.let { contexto.getString(R.string.a_partir_de, Moeda.formatar(it)) },
                contexto.getString(R.string.esgotado).takeIf { linha.esgotado }
            ).joinToString(" · ")
            textComposicao.isVisible = linha.descricao.isNotBlank()
            textComposicao.text = linha.descricao
            seta.rotation = if (linha.expandido) 180f else 0f
            root.setOnClickListener { aoTocarGrupo(linha) }
        }
    }

    private fun vincularItem(holder: ItemViewHolder, linha: LinhaProduto.Item) {
        val contexto = holder.itemView.context
        val produto = linha.item.produto
        with(holder.b) {
            root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = contexto.resources.getDimensionPixelSize(
                    if (linha.recuado) R.dimen.recuo_variante else R.dimen.margem_linha_produto
                )
            }
            faixa.setBackgroundColor(Cores.converter(linha.item.categoria.cor))
            textNome.text = if (linha.recuado) produto.rotuloNoGrupo() else produto.nome
            val detalhe = listOfNotNull(
                produto.descricao.takeIf { it.isNotBlank() },
                linha.item.categoria.nome.takeIf { linha.mostrarCategoria },
                contexto.getString(R.string.de_n_em_n, produto.multiplo).takeIf { produto.multiplo > 1 }
            ).joinToString(" · ")
            textDetalhe.isVisible = detalhe.isNotEmpty()
            textDetalhe.text = detalhe
            textPreco.text = Moeda.formatar(produto.precoCentavos)

            val estoque = produto.estoque
            textEstoque.isVisible = estoque != null
            if (estoque != null) {
                textEstoque.text = if (produto.esgotado) contexto.getString(R.string.esgotado)
                else contexto.getString(R.string.restam_n, estoque)
                textEstoque.setTextColor(
                    ContextCompat.getColor(
                        contexto,
                        when {
                            produto.esgotado -> R.color.observacao
                            estoque <= ESTOQUE_BAIXO -> R.color.pendente
                            else -> R.color.texto_secundario
                        }
                    )
                )
            }
            seta.isVisible = linha.pedePreparo
            seta.rotation = if (linha.preparoAberto) 180f else 0f
            root.alpha = if (produto.esgotado) 0.5f else 1f
            root.setOnClickListener { aoTocarItem(linha) }
        }
    }

    private fun vincularPreparo(holder: PreparoViewHolder, linha: LinhaProduto.Preparo) {
        val grupo = holder.b.grupoPreparo
        FormularioItem.OPCOES_SUCO.forEachIndexed { indice, opcao ->
            grupo.getChildAt(indice).setOnClickListener { aoEscolherPreparo(linha.item, opcao) }
        }
    }

    private companion object {
        const val TIPO_GRUPO = 0
        const val TIPO_ITEM = 1
        const val TIPO_PREPARO = 2
        const val ESTOQUE_BAIXO = 3
    }
}

private object LinhaDiff : DiffUtil.ItemCallback<LinhaProduto>() {
    override fun areItemsTheSame(antiga: LinhaProduto, nova: LinhaProduto) = antiga.chave == nova.chave
    override fun areContentsTheSame(antiga: LinhaProduto, nova: LinhaProduto) = antiga == nova
}
