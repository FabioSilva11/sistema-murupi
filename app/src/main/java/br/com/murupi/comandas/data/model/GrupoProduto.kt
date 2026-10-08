package br.com.murupi.comandas.data.model

/** Uma opção com preço dentro de um grupo ou subcategoria do cardápio. */
data class OpcaoGrupoProduto(
    val id: Long = 0,
    val nome: String,
    val precoCentavos: Long
)

/**
 * Edição conjunta de produtos que aparecem sob o mesmo grupo no lançamento.
 * Cada opção é salva como um produto próprio para continuar funcionando em estoque e comandas.
 */
data class GrupoProduto(
    val categoriaId: Long,
    val nome: String,
    val descricao: String = "",
    val opcoes: List<OpcaoGrupoProduto>,
    /** Quando não nulo, aplica a todas as opções (novas e existentes). */
    val ativo: Boolean? = null
) {

    /** Mantém estoque, disponibilidade e múltiplo das variantes que já existiam. */
    fun paraProdutos(basePorId: Map<Long, Produto> = emptyMap()): List<Produto> =
        opcoes.map { opcao ->
            val base = basePorId[opcao.id]
            if (base != null) {
                base.copy(
                    categoriaId = categoriaId,
                    nome = opcao.nome,
                    precoCentavos = opcao.precoCentavos,
                    grupo = nome,
                    descricao = descricao,
                    ativo = ativo ?: base.ativo
                )
            } else {
                Produto(
                    id = opcao.id,
                    categoriaId = categoriaId,
                    nome = opcao.nome,
                    precoCentavos = opcao.precoCentavos,
                    grupo = nome,
                    descricao = descricao,
                    ativo = ativo ?: true
                )
            }
        }

    /** Variantes existentes que foram retiradas da tela devem sair do catálogo. */
    fun idsRemovidos(idsOriginais: Set<Long>): Set<Long> =
        idsOriginais - opcoes.map { it.id }.filter { it > 0 }.toSet()

}
