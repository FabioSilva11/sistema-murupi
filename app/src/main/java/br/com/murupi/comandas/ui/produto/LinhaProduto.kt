package br.com.murupi.comandas.ui.produto

import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.ProdutoItem
import br.com.murupi.comandas.data.model.esgotado
import br.com.murupi.comandas.data.model.precoLivre
import br.com.murupi.comandas.ui.common.FormularioItem

/** Linhas da lista de produtos: grupos expansíveis, produtos e a linha de opções de preparo. */
sealed interface LinhaProduto {
    val chave: String

    /** Ex.: "Graviola", que expande para os tamanhos. */
    data class Grupo(
        val nome: String,
        val categoria: Categoria,
        val opcoes: List<ProdutoItem>,
        val variantes: Int,
        val menorPrecoCentavos: Long?,
        val esgotado: Boolean,
        val expandido: Boolean,
        /** Composição do sabor, exibida abaixo do título (ex.: sucos numerados). */
        val descricao: String = ""
    ) : LinhaProduto {
        override val chave: String get() = chaveGrupo(categoria.id, nome)
    }

    data class Item(
        val item: ProdutoItem,
        /** Dentro de um grupo: aparece recuado e só com o nome curto ("300ml"). */
        val recuado: Boolean,
        val preparoAberto: Boolean,
        val mostrarCategoria: Boolean
    ) : LinhaProduto {
        override val chave: String get() = "item:${item.produto.id}"

        /** Sucos de polpa: tocar expande com leite/sem leite em vez de abrir a janela. Laranja, limão e abacatada lançam direto. */
        val pedePreparo: Boolean
            get() = item.categoria.perguntarOpcoesSuco && !item.produto.precoLivre && !item.produto.esgotado &&
                FormularioItem.exigeOpcaoLeite(item.produto.nome, item.produto.grupo)
    }

    /** Opções (com leite, sem açúcar...) logo abaixo do produto; cada toque lança 1 unidade. */
    data class Preparo(val item: ProdutoItem) : LinhaProduto {
        override val chave: String get() = "preparo:${item.produto.id}"
    }

    companion object {
        fun chaveGrupo(categoriaId: Long, nome: String) = "grupo:$categoriaId:$nome"
    }
}

object MontadorLinhas {

    private class Bloco(val titulo: String, val grupo: String?, val itens: List<ProdutoItem>)

    /**
     * Monta a lista em ordem alfabética. Com [agrupar], produtos do mesmo grupo viram uma linha
     * só, que mostra as variantes quando a chave dela está em [gruposAbertos].
     */
    fun montar(
        itens: List<ProdutoItem>,
        gruposAbertos: Set<String>,
        preparoAberto: Long?,
        agrupar: Boolean,
        mostrarCategoria: Boolean,
        ordemAlfabetica: Comparator<String>
    ): List<LinhaProduto> {
        val linhas = mutableListOf<LinhaProduto>()
        val porNome = Comparator<ProdutoItem> { a, b -> ordemAlfabetica.compare(a.produto.nome, b.produto.nome) }

        fun adicionar(item: ProdutoItem, recuado: Boolean) {
            val linha = LinhaProduto.Item(item, recuado, preparoAberto == item.produto.id, mostrarCategoria)
            linhas += linha
            if (linha.preparoAberto && linha.pedePreparo) linhas += LinhaProduto.Preparo(item)
        }

        if (!agrupar) {
            itens.sortedWith(porNome).forEach { adicionar(it, recuado = false) }
            return linhas
        }

        val (comGrupo, avulsos) = itens.partition { !it.produto.grupo.isNullOrBlank() }
        val blocos = avulsos.map { Bloco(it.produto.nome, null, listOf(it)) } +
            comGrupo.groupBy { it.produto.grupo.orEmpty().trim() }.map { (nome, lista) -> Bloco(nome, nome, lista) }

        for (bloco in blocos.sortedWith { a, b -> ordemAlfabetica.compare(a.titulo, b.titulo) }) {
            if (bloco.grupo == null) {
                adicionar(bloco.itens.first(), recuado = false)
                continue
            }
            val categoria = bloco.itens.first().categoria
            val expandido = LinhaProduto.chaveGrupo(categoria.id, bloco.grupo) in gruposAbertos
            linhas += LinhaProduto.Grupo(
                nome = bloco.grupo,
                categoria = categoria,
                opcoes = bloco.itens.sortedWith(porNome),
                variantes = bloco.itens.size,
                menorPrecoCentavos = bloco.itens.map { it.produto }.filterNot { it.precoLivre }.minOfOrNull { it.precoCentavos },
                esgotado = bloco.itens.all { it.produto.esgotado },
                expandido = expandido,
                descricao = bloco.itens.firstOrNull()?.produto?.descricao.orEmpty()
            )
            if (expandido) bloco.itens.sortedWith(porNome).forEach { adicionar(it, recuado = true) }
        }
        return linhas
    }
}
