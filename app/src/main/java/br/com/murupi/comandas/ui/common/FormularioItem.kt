package br.com.murupi.comandas.ui.common

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import br.com.murupi.comandas.R
import br.com.murupi.comandas.databinding.ChipOpcaoBinding
import br.com.murupi.comandas.databinding.DialogItemBinding
import br.com.murupi.comandas.util.Moeda
import br.com.murupi.comandas.util.Texto
import com.google.android.material.chip.Chip

data class DadosItem(val quantidade: Int, val precoCentavos: Long, val observacao: String)

/**
 * Controla o layout dialog_item (quantidade, preço livre, opções de suco e observação), usado
 * tanto para lançar um produto quanto para editar um item da comanda.
 */
class FormularioItem(
    private val b: DialogItemBinding,
    quantidadeInicial: Int,
    private val precoCentavos: Long,
    private val precoEditavel: Boolean,
    observacaoInicial: String,
    private val perguntarOpcoesSuco: Boolean,
    /** Limite do botão "+", ex.: o estoque disponível do produto. */
    private val quantidadeMaxima: Int = QUANTIDADE_MAXIMA,
    /** Produtos vendidos de N em N (ex.: entradas de 5 em 5): +/- andam de [passo] em [passo]. */
    private val passo: Int = 1
) {
    private val contexto = b.root.context
    private var quantidade = maxOf(quantidadeInicial, passo)

    init {
        b.layoutPreco.isVisible = precoEditavel
        if (precoEditavel) b.editPreco.setText(Moeda.paraCampo(precoCentavos))
        b.editObservacao.setText(observacaoInicial)

        b.textOpcoes.isVisible = perguntarOpcoesSuco
        b.grupoOpcoes.isVisible = perguntarOpcoesSuco
        if (perguntarOpcoesSuco) {
            val inflater = LayoutInflater.from(contexto)
            OPCOES_SUCO.forEach { opcao ->
                val chip = ChipOpcaoBinding.inflate(inflater, b.grupoOpcoes, false).root
                chip.text = opcao
                chip.id = View.generateViewId()
                b.grupoOpcoes.addView(chip)
            }
        }
        b.grupoOpcoes.setOnCheckedStateChangeListener { _, _ ->
            b.textErroOpcoes.isVisible = false
            val outro = opcaoSelecionada() == OPCAO_OUTRO
            b.layoutObservacao.hint = contexto.getString(if (outro) R.string.descreva_opcao else R.string.observacao)
            if (outro) b.editObservacao.requestFocus()
        }

        b.textPasso.isVisible = passo > 1
        b.textPasso.text = contexto.getString(R.string.vendido_de_n_em_n, passo)
        b.btnMenos.setOnClickListener {
            if (quantidade - passo >= passo) quantidade -= passo
            atualizar()
        }
        b.btnMais.setOnClickListener {
            if (quantidade + passo <= quantidadeMaxima) quantidade += passo
            atualizar()
        }
        b.editPreco.doAfterTextChanged {
            b.layoutPreco.error = null
            atualizar()
        }
        b.editObservacao.doAfterTextChanged { b.layoutObservacao.error = null }
        atualizar()
    }

    private fun precoAtual(): Long? =
        if (precoEditavel) Moeda.converter(b.editPreco.text?.toString().orEmpty()) else precoCentavos

    private fun opcaoSelecionada(): String? =
        b.grupoOpcoes.findViewById<Chip>(b.grupoOpcoes.checkedChipId)?.text?.toString()

    private fun atualizar() {
        b.textQuantidade.text = quantidade.toString()
        val preco = precoAtual()
        b.textTotal.text = if (preco != null && preco > 0) {
            contexto.getString(R.string.total_valor, Moeda.formatar(preco * quantidade))
        } else {
            ""
        }
    }

    /** Valida os campos e mostra os erros na tela; retorna null se algo estiver faltando. */
    fun validar(): DadosItem? {
        val preco = precoAtual()
        if (preco == null) {
            b.layoutPreco.error = contexto.getString(R.string.informe_preco)
            return null
        }
        val opcao = opcaoSelecionada()
        if (perguntarOpcoesSuco && opcao == null) {
            b.textErroOpcoes.isVisible = true
            return null
        }
        val observacao = b.editObservacao.text?.toString().orEmpty().trim()
        if (opcao == OPCAO_OUTRO && observacao.isEmpty()) {
            b.layoutObservacao.error = contexto.getString(R.string.descreva_opcao)
            return null
        }
        val textoFinal = listOfNotNull(opcao?.takeIf { it != OPCAO_OUTRO }, observacao.ifEmpty { null })
            .joinToString(", ")
        return DadosItem(quantidade, preco, textoFinal)
    }

    companion object {
        const val QUANTIDADE_MAXIMA = 999
        const val OPCAO_OUTRO = "Outro…"

        /** Pergunta obrigatória dos sucos de polpa: com leite ou sem leite. */
        val OPCOES_SUCO = listOf(
            "com leite",
            "sem leite",
            OPCAO_OUTRO
        )

        /** Laranja, limão e abacatada lançam direto, sem perguntar leite. */
        fun exigeOpcaoLeite(nomeProduto: String, grupo: String?): Boolean {
            val texto = Texto.normalizarBusca(nomeProduto) + " " + Texto.normalizarBusca(grupo.orEmpty())
            return !(texto.contains("laranja") || texto.contains("limao") || texto.contains("abacat"))
        }
    }
}
