package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.data.model.Setor

object ItensDeTeste {

    val comanda = Comanda(id = 1, mesa = 9, sequencia = 1, pessoas = 2, abertaEm = 0L)

    val isca = item(1, "Isca de Frango", "ISCAS", Setor.COZINHA, quantidade = 2, preco = 1600)

    val sucoSobDemanda = item(
        2, "Suco de Maracujá 300ml", "SUCOS", Setor.SUCOS,
        quantidade = 1, preco = 600, observacao = "com leite", demanda = true
    )

    val refrigerante = item(3, "Refrigerante Lata", "BEBIDAS RESTAURANTE", Setor.REFRIGERANTES, quantidade = 2, preco = 500)

    fun impressora(papel: PapelImpressora, colunas: Int = Impressora.COLUNAS_80MM) =
        Impressora(id = 1, nome = papel.descricao, ip = "192.168.0.50", papel = papel, colunas = colunas)

    private fun item(
        id: Long,
        nome: String,
        categoria: String,
        setor: Setor,
        quantidade: Int,
        preco: Long,
        observacao: String = "",
        demanda: Boolean = false
    ) = ItemComanda(
        id = id,
        comandaId = comanda.id,
        produtoId = id,
        nome = nome,
        categoriaNome = categoria,
        setor = setor,
        demanda = demanda,
        precoLivre = false,
        quantidade = quantidade,
        precoUnitarioCentavos = preco,
        observacao = observacao,
        criadoEm = 0L
    )
}
