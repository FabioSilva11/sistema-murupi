package br.com.murupi.comandas.ui.impressora

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.databinding.ItemImpressoraBinding
import br.com.murupi.comandas.util.DataHora

class ImpressoraAdapter(
    private val aoTocar: (Impressora) -> Unit,
    private val aoTestar: (Impressora) -> Unit
) : ListAdapter<Impressora, ImpressoraAdapter.ImpressoraViewHolder>(ImpressoraDiff) {

    class ImpressoraViewHolder(val b: ItemImpressoraBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ImpressoraViewHolder(ItemImpressoraBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ImpressoraViewHolder, position: Int) {
        val impressora = getItem(position)
        val contexto = holder.itemView.context
        with(holder.b) {
            textNome.text = impressora.nome
            textEndereco.text = contexto.getString(R.string.ip_porta, impressora.ip, impressora.porta)
            textPapel.text = contexto.getString(
                R.string.papel_detalhe, impressora.papel.descricao.uppercase(), impressora.papel.detalhe
            )
            textDetalhes.text = listOfNotNull(
                contexto.getString(
                    if (impressora.colunas == Impressora.COLUNAS_58MM) R.string.largura_58 else R.string.largura_80
                ),
                contexto.getString(if (impressora.removerAcentos) R.string.sem_acentos else R.string.com_acentos),
                contexto.getString(R.string.inativa).takeIf { !impressora.ativa },
                impressora.ultimoUsoEm?.let { contexto.getString(R.string.ultimo_uso, DataHora.curta(it)) }
            ).joinToString(" · ")
            root.alpha = if (impressora.ativa) 1f else 0.5f
            root.setOnClickListener { aoTocar(impressora) }
            btnTestar.setOnClickListener { aoTestar(impressora) }
        }
    }
}

private object ImpressoraDiff : DiffUtil.ItemCallback<Impressora>() {
    override fun areItemsTheSame(antiga: Impressora, nova: Impressora) = antiga.id == nova.id
    override fun areContentsTheSame(antiga: Impressora, nova: Impressora) = antiga == nova
}
