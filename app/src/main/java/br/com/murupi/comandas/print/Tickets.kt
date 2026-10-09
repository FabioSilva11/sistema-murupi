package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.model.formasPagamentoDescricao
import br.com.murupi.comandas.data.model.rotuloImpressao
import br.com.murupi.comandas.data.model.rotuloPapeis
import br.com.murupi.comandas.data.model.totalCentavos
import br.com.murupi.comandas.util.DataHora
import br.com.murupi.comandas.util.Moeda

/** Conteúdo de cada tipo de ticket, já em bytes ESC/POS. */
object Tickets {

    const val DESTAQUE_DEMANDA = ">>> "

    /**
     * Comanda de produção (cozinha, sucos ou refrigerantes): só quantidade e nome, sem preço.
     * Observações saem em branco no preto e itens sob demanda começam com ">>>".
     */
    fun producao(
        impressora: Impressora,
        papel: PapelImpressora,
        comanda: Comanda,
        itens: List<ItemComanda>,
        reimpressao: Boolean,
        agora: Long = System.currentTimeMillis()
    ): ByteArray {
        val t = EscPosBuilder(impressora.colunas, impressora.removerAcentos)
        t.alinhar(Alinhamento.CENTRO).negrito(true).linha(Restaurante.NOME)
        t.negrito(true)
            .tamanho(larguraDupla = true, alturaDupla = true).linha(papel.descricao.uppercase())
        if (reimpressao) t.tamanho(larguraDupla = false, alturaDupla = false).linha("*** REIMPRESSÃO ***")
        t.tamanho(larguraDupla = true, alturaDupla = true).linha(comanda.rotuloImpressao)
        t.normal().linha(DataHora.completa(agora) + if (comanda.pessoas > 0) " - ${comanda.pessoas} pessoa(s)" else "")
        t.alinhar(Alinhamento.ESQUERDA).separador('=')

        val linhas = consolidarProducao(itens)
        linhas.forEachIndexed { indice, item ->
            if (indice > 0) t.linha()
            val destaque = if (item.demanda) DESTAQUE_DEMANDA else ""
            t.negrito(true).tamanho(larguraDupla = false, alturaDupla = true)
                .paragrafo("$destaque${item.quantidade}x ${item.nome}", recuo = " ".repeat(destaque.length + 3))
            if (item.observacao.isNotEmpty()) {
                t.inverso(true).paragrafo("OBS: ${item.observacao.uppercase()}", recuo = "     ").inverso(false)
            }
            t.normal()
        }
        t.separador('=')
        t.linha("Total de itens: ${linhas.sumOf { it.quantidade }}")
        return t.cortar().bytes()
    }

    /** Atualização integral da estação: estado atual e cancelamentos em seção separada. */
    fun atualizacaoProducao(
        impressora: Impressora,
        papel: PapelImpressora,
        comanda: Comanda,
        itensAtuais: List<ItemComanda>,
        cancelamentos: List<ItemComanda>,
        agora: Long = System.currentTimeMillis()
    ): ByteArray {
        val t = EscPosBuilder(impressora.colunas, impressora.removerAcentos)
        t.alinhar(Alinhamento.CENTRO).negrito(true).linha(Restaurante.NOME)
        t.tamanho(larguraDupla = true, alturaDupla = true).linha(papel.descricao.uppercase())
        t.tamanho(larguraDupla = false, alturaDupla = false).linha("*** ATUALIZAÇÃO COMPLETA ***")
        t.tamanho(larguraDupla = true, alturaDupla = true).linha(comanda.rotuloImpressao)
        t.normal().linha(DataHora.completa(agora))
        t.alinhar(Alinhamento.ESQUERDA).separador('=')
        t.negrito(true).linha("PEDIDO ATUAL — CONFERIR ANTES DE PREPARAR").normal()
        val linhas = consolidarProducao(itensAtuais)
        if (linhas.isEmpty()) {
            t.linha("Nenhum item ativo nesta estação")
        } else {
            linhas.forEach { item ->
                val destaque = if (item.demanda) DESTAQUE_DEMANDA else ""
                t.negrito(true).tamanho(larguraDupla = false, alturaDupla = true)
                    .paragrafo("$destaque${item.quantidade}x ${item.nome}", recuo = " ".repeat(destaque.length + 3))
                if (item.observacao.isNotEmpty()) {
                    t.inverso(true).paragrafo("OBS: ${item.observacao.uppercase()}", recuo = "     ").inverso(false)
                }
                t.normal()
            }
        }
        if (cancelamentos.isNotEmpty()) {
            t.separador('!').negrito(true).linha("CANCELAMENTOS — NÃO PREPARAR").normal()
            cancelamentos.forEach { item ->
                t.inverso(true).paragrafo("CANCELADO: ${item.quantidade}x ${item.nome}").inverso(false)
            }
        }
        t.separador('=')
        t.linha("Itens ativos: ${linhas.sumOf { it.quantidade }}")
        t.negrito(true).linha("NÃO REPREPARAR ITENS JÁ PRODUZIDOS").normal()
        return t.cortar().bytes()
    }

    /** Espelho / conferência de conta: todos os itens com preço, total e valor por pessoa. */
    fun espelho(
        impressora: Impressora,
        comanda: Comanda,
        itens: List<ItemComanda>,
        agora: Long = System.currentTimeMillis()
    ): ByteArray {
        val t = EscPosBuilder(impressora.colunas, impressora.removerAcentos)
        val total = itens.sumOf { it.totalCentavos }

        t.alinhar(Alinhamento.CENTRO).negrito(true)
            .tamanho(larguraDupla = false, alturaDupla = true).linha(Restaurante.NOME)
        t.normal().linha("CONFERÊNCIA DE CONTA")
        t.negrito(true).tamanho(larguraDupla = true, alturaDupla = true).linha(comanda.rotuloImpressao)
        t.normal().alinhar(Alinhamento.ESQUERDA)
        t.duasColunas("Abertura:", DataHora.completa(comanda.abertaEm))
        t.duasColunas("Emissão:", DataHora.completa(agora))
        if (comanda.pessoas > 0) t.duasColunas("Pessoas:", comanda.pessoas.toString())
        if (comanda.tipo == TipoComanda.DELIVERY && comanda.endereco.isNotEmpty()) {
            t.linha("Entrega: ${comanda.endereco}")
        }
        t.separador()
        t.negrito(true).duasColunas("ITEM", "VALOR").negrito(false)
        t.separador()

        consolidarConta(itens).forEach { item ->
            t.linhas(
                TextoTicket.comValor(
                    "${item.quantidade}x ${item.nome}",
                    Moeda.formatar(item.totalCentavos, comSimbolo = false),
                    t.largura
                )
            )
            if (item.quantidade > 1) {
                t.linha("   ${item.quantidade} x ${Moeda.formatar(item.precoUnitarioCentavos, comSimbolo = false)}")
            }
        }

        t.separador()
        t.negrito(true).tamanho(larguraDupla = false, alturaDupla = true)
            .duasColunas("TOTAL", Moeda.formatar(total)).normal()
        if (comanda.pessoas > 1) {
            t.duasColunas("Por pessoa (${comanda.pessoas}):", Moeda.formatar(Moeda.dividir(total, comanda.pessoas)))
        }
        t.separador()
        t.alinhar(Alinhamento.CENTRO).linha("NÃO É DOCUMENTO FISCAL").linha().paragrafo(Restaurante.AGRADECIMENTO)
        return t.cortar().bytes()
    }

    /** Página de teste: confirma conexão, largura do papel e se os acentos saem certos. */
    fun teste(impressora: Impressora, agora: Long = System.currentTimeMillis()): ByteArray {
        val t = EscPosBuilder(impressora.colunas, impressora.removerAcentos)
        t.alinhar(Alinhamento.CENTRO).negrito(true)
            .tamanho(larguraDupla = true, alturaDupla = true).linha("TESTE")
        t.normal().linha(Restaurante.NOME).alinhar(Alinhamento.ESQUERDA).separador()
        t.duasColunas("Impressora:", impressora.nome)
        t.duasColunas("Endereço:", "${impressora.ip}:${impressora.porta}")
        t.duasColunas("Papel:", impressora.rotuloPapeis)
        t.duasColunas("Largura:", "${impressora.colunas} colunas")
        t.duasColunas("Data:", DataHora.completa(agora))
        t.separador()
        t.linha("Acentos: ação café açaí pão jabá")
        t.linha((1..t.largura).joinToString("") { (it % 10).toString() })
        t.negrito(true).linha("Negrito").normal()
        t.tamanho(larguraDupla = false, alturaDupla = true).linha("Altura dupla").normal()
        t.inverso(true).linha(" OBS: DESTAQUE ").inverso(false)
        return t.cortar().bytes()
    }

    /**
     * Versão em texto puro do ticket de produção, para conferir na tela antes de imprimir.
     * Espelha [producao]: mesmas linhas consolidadas, observações e total de itens.
     */
    fun producaoTexto(
        largura: Int,
        papel: PapelImpressora,
        comanda: Comanda,
        itens: List<ItemComanda>,
        reimpressao: Boolean,
        agora: Long = System.currentTimeMillis()
    ): String {
        val t = mutableListOf<String>()
        t += centralizar(Restaurante.NOME, largura)
        t += centralizar(papel.descricao.uppercase(), largura)
        if (reimpressao) t += centralizar("*** REIMPRESSÃO ***", largura)
        t += centralizar(comanda.rotuloImpressao, largura)
        t += DataHora.completa(agora) + if (comanda.pessoas > 0) " - ${comanda.pessoas} pessoa(s)" else ""
        t += "=".repeat(largura)
        val linhas = consolidarProducao(itens)
        linhas.forEachIndexed { indice, item ->
            if (indice > 0) t += ""
            val destaque = if (item.demanda) DESTAQUE_DEMANDA else ""
            t += TextoTicket.quebrar("$destaque${item.quantidade}x ${item.nome}", largura, " ".repeat(destaque.length + 3))
            if (item.observacao.isNotEmpty()) {
                t += TextoTicket.quebrar("OBS: ${item.observacao.uppercase()}", largura, "     ")
            }
        }
        t += "=".repeat(largura)
        t += "Total de itens: ${linhas.sumOf { it.quantidade }}"
        return t.joinToString("\n")
    }

    /** Prévia em texto do ticket de atualização integral. */
    fun atualizacaoProducaoTexto(
        largura: Int,
        papel: PapelImpressora,
        comanda: Comanda,
        itensAtuais: List<ItemComanda>,
        cancelamentos: List<ItemComanda>,
        agora: Long = System.currentTimeMillis()
    ): String {
        val linhas = consolidarProducao(itensAtuais)
        val t = mutableListOf<String>()
        t += centralizar(Restaurante.NOME, largura)
        t += centralizar(papel.descricao.uppercase(), largura)
        t += centralizar("*** ATUALIZAÇÃO COMPLETA ***", largura)
        t += centralizar(comanda.rotuloImpressao, largura)
        t += DataHora.completa(agora)
        t += "=".repeat(largura)
        t += "PEDIDO ATUAL — CONFERIR ANTES DE PREPARAR"
        if (linhas.isEmpty()) t += "Nenhum item ativo nesta estação"
        linhas.forEach { item ->
            val destaque = if (item.demanda) DESTAQUE_DEMANDA else ""
            t += TextoTicket.quebrar("$destaque${item.quantidade}x ${item.nome}", largura, " ".repeat(destaque.length + 3))
            if (item.observacao.isNotEmpty()) t += TextoTicket.quebrar("OBS: ${item.observacao.uppercase()}", largura, "     ")
        }
        if (cancelamentos.isNotEmpty()) {
            t += "!".repeat(largura)
            t += "CANCELAMENTOS — NÃO PREPARAR"
            cancelamentos.forEach { item ->
                t += TextoTicket.quebrar("CANCELADO: ${item.quantidade}x ${item.nome}", largura)
            }
        }
        t += "=".repeat(largura)
        t += "Itens ativos: ${linhas.sumOf { it.quantidade }}"
        t += "NÃO REPREPARAR ITENS JÁ PRODUZIDOS"
        return t.joinToString("\n")
    }

    /**
     * Versão em texto puro do espelho, para conferir na tela antes de imprimir.
     * Espelha [espelho]: itens com preço, total e valor por pessoa.
     */
    fun espelhoTexto(
        largura: Int,
        comanda: Comanda,
        itens: List<ItemComanda>,
        agora: Long = System.currentTimeMillis()
    ): String {
        val t = mutableListOf<String>()
        val total = itens.sumOf { it.totalCentavos }
        t += centralizar(Restaurante.NOME, largura)
        t += centralizar("CONFERÊNCIA DE CONTA", largura)
        t += centralizar(comanda.rotuloImpressao, largura)
        t += TextoTicket.colunas("Abertura:", DataHora.completa(comanda.abertaEm), largura)
        t += TextoTicket.colunas("Emissão:", DataHora.completa(agora), largura)
        if (comanda.pessoas > 0) t += TextoTicket.colunas("Pessoas:", comanda.pessoas.toString(), largura)
        if (comanda.tipo == TipoComanda.DELIVERY && comanda.endereco.isNotEmpty()) {
            t += TextoTicket.quebrar("Entrega: ${comanda.endereco}", largura)
        }
        t += "-".repeat(largura)
        consolidarConta(itens).forEach { item ->
            t += TextoTicket.comValor(
                "${item.quantidade}x ${item.nome}",
                Moeda.formatar(item.totalCentavos, comSimbolo = false),
                largura
            )
            if (item.quantidade > 1) {
                t += "   ${item.quantidade} x ${Moeda.formatar(item.precoUnitarioCentavos, comSimbolo = false)}"
            }
        }
        t += "-".repeat(largura)
        t += TextoTicket.colunas("TOTAL", Moeda.formatar(total), largura)
        if (comanda.pessoas > 1) {
            t += TextoTicket.colunas(
                "Por pessoa (${comanda.pessoas}):",
                Moeda.formatar(Moeda.dividir(total, comanda.pessoas)),
                largura
            )
        }
        t += "-".repeat(largura)
        t += centralizar("NÃO É DOCUMENTO FISCAL", largura)
        return t.joinToString("\n")
    }

    private fun centralizar(texto: String, largura: Int): String {
        if (texto.length >= largura) return texto
        val esquerda = (largura - texto.length) / 2
        return " ".repeat(esquerda) + texto
    }

    /**
     * Histórico de vendas completo numa das impressoras: cada conta fechada com
     * seus itens e total, e o saldo total no final.
     */
    fun historico(
        impressora: Impressora,
        contas: List<Pair<Comanda, List<ItemComanda>>>,
        agora: Long = System.currentTimeMillis()
    ): ByteArray {
        val t = EscPosBuilder(impressora.colunas, impressora.removerAcentos)
        val largura = impressora.colunas
        var saldo = 0L

        t.alinhar(Alinhamento.CENTRO).negrito(true).linha(Restaurante.NOME)
        t.normal().linha("HISTÓRICO DE VENDAS")
        t.normal().linha(DataHora.completa(agora))
        t.alinhar(Alinhamento.ESQUERDA).separador('=')

        contas.forEachIndexed { indice, (comanda, itens) ->
            if (indice > 0) t.linha()
            t.negrito(true).linha(comanda.rotuloImpressao).normal()
            if (comanda.tipo == TipoComanda.DELIVERY && comanda.endereco.isNotEmpty()) {
                t.linha("Entrega: ${comanda.endereco}")
            }
            t.duasColunas("Fechada:", DataHora.completa(comanda.fechadaEm ?: comanda.abertaEm))
            comanda.formasPagamentoDescricao.takeIf { it.isNotEmpty() }?.let {
                t.linhas(TextoTicket.quebrar("Pagamento: $it", largura))
            }
            consolidarConta(itens).forEach { item ->
                t.linhas(
                    TextoTicket.comValor(
                        "${item.quantidade}x ${item.nome}",
                        Moeda.formatar(item.totalCentavos, comSimbolo = false),
                        largura
                    )
                )
            }
            val total = comanda.totalPagoCentavos ?: itens.sumOf { it.totalCentavos }
            saldo += total
            t.negrito(true).duasColunas("TOTAL", Moeda.formatar(total)).normal()
            t.separador()
        }
        t.negrito(true).tamanho(larguraDupla = false, alturaDupla = true)
            .duasColunas("SALDO TOTAL", Moeda.formatar(saldo)).normal()
        return t.cortar().bytes()
    }

    private data class LinhaProducao(val nome: String, val observacao: String, val demanda: Boolean, val quantidade: Int)

    /** Junta itens iguais (mesmo nome e observação) lançados em momentos diferentes. */
    private fun consolidarProducao(itens: List<ItemComanda>): List<LinhaProducao> =
        itens.groupBy { Triple(it.nome, it.observacao.trim(), it.demanda) }
            .map { (chave, grupo) -> LinhaProducao(chave.first, chave.second, chave.third, grupo.sumOf { it.quantidade }) }

    private data class LinhaConta(val nome: String, val precoUnitarioCentavos: Long, val quantidade: Int) {
        val totalCentavos: Long get() = precoUnitarioCentavos * quantidade
    }

    private fun consolidarConta(itens: List<ItemComanda>): List<LinhaConta> =
        itens.groupBy { it.nome to it.precoUnitarioCentavos }
            .map { (chave, grupo) -> LinhaConta(chave.first, chave.second, grupo.sumOf { it.quantidade }) }
}
