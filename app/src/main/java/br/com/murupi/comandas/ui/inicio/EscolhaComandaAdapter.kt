package br.com.murupi.comandas.ui.inicio

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.StatusComanda
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.databinding.ItemEscolhaComandaBinding
import br.com.murupi.comandas.util.Moeda

/** Opções do diálogo ao tocar numa mesa ocupada: as comandas abertas e abrir outra. */
sealed interface OpcaoComanda {
    data class Existente(val resumo: ComandaResumo) : OpcaoComanda
}

class EscolhaComandaAdapter(context: Context, opcoes: List<OpcaoComanda>) :
    ArrayAdapter<OpcaoComanda>(context, 0, opcoes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = convertView?.tag as ItemEscolhaComandaBinding?
            ?: ItemEscolhaComandaBinding.inflate(LayoutInflater.from(context), parent, false)
                .also { it.root.tag = it }
        when (val opcao = getItem(position)) {
            is OpcaoComanda.Existente -> {
                val resumo = opcao.resumo
                val fechada = resumo.comanda.status != StatusComanda.ABERTA
                b.root.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (fechada) R.color.fechada_fundo else R.color.comanda_aberta_fundo
                    )
                )
                b.textTitulo.text = resumo.comanda.sequencia.toString()
                b.textTitulo.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (fechada) R.color.observacao else R.color.texto_primario
                    )
                )
                b.textSubtitulo.text = if (fechada) context.getString(R.string.conta_fechada)
                else context.resources.getQuantityString(R.plurals.itens, resumo.qtdItens, resumo.qtdItens)
                b.textSubtitulo.append(" · ${Moeda.formatar(resumo.totalCentavos)}")
            }
            null -> Unit
        }
        return b.root
    }
}
