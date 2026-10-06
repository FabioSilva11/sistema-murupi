package br.com.murupi.comandas.data.model

/** Setor de produção de um produto: define para qual impressora o pedido vai. */
enum class Setor(val descricao: String) {
    COZINHA("Cozinha"),
    SUCOS("Sucos"),

    /** Bebidas: saem na cozinha, abaixo dos pratos. */
    REFRIGERANTES("Refrigerantes")
}

/** Papel (role) de uma impressora. A ordem define a ordem de impressão e de exibição. */
enum class PapelImpressora(val descricao: String, val detalhe: String) {
    ESPELHO("Espelho", "conta com preço e total"),
    COZINHA("Cozinha", "produção sem preço (pratos e bebidas)"),
    SUCOS("Sucos", "só sucos, sem preço"),
}

enum class StatusComanda { ABERTA, FECHADA }

/** Origem da comanda: mesa do salão, balcão ou delivery. */
enum class TipoComanda(val descricao: String) {
    MESA("Mesa"),
    BALCAO("Balcão"),
    DELIVERY("Delivery")
}

enum class FormaPagamento(val descricao: String) {
    DINHEIRO("Dinheiro"),
    DEBITO("Cartão de débito"),
    CREDITO("Cartão de crédito"),
    PIX("PIX")
}
