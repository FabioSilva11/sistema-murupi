package br.com.murupi.comandas.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Comanda de uma mesa. A mesa 9 pode ter as comandas 9.1, 9.2, ... abertas ao mesmo tempo. */
@Entity(tableName = "comandas", indices = [Index("mesa"), Index("status")])
data class Comanda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Número da mesa no salão; 0 para balcão e delivery. */
    val mesa: Int,
    val sequencia: Int,
    val status: StatusComanda = StatusComanda.ABERTA,
    val pessoas: Int = 0,
    val abertaEm: Long = System.currentTimeMillis(),
    val fechadaEm: Long? = null,
    val formaPagamento: FormaPagamento? = null,
    val totalPagoCentavos: Long? = null,
    val tipo: TipoComanda = TipoComanda.MESA,
    /** Delivery (e balcão, se informado): nome do cliente. */
    val nomeCliente: String = "",
    /** Delivery: endereço de entrega. */
    val endereco: String = ""
)

/** Número exibido da comanda, ex.: "9.1". */
val Comanda.numero: String get() = "$mesa.$sequencia"

/** Título exibido nas telas: "9.1", "Balcão 1" ou o nome do cliente no delivery. */
val Comanda.titulo: String get() = when (tipo) {
    TipoComanda.MESA -> numero
    TipoComanda.BALCAO -> "Balcão $sequencia"
    TipoComanda.DELIVERY -> nomeCliente.ifEmpty { "Delivery $sequencia" }
}

/** Linha do ticket impresso identificando a origem: MESA 9.1, BALCÃO 1 ou DELIVERY. */
val Comanda.rotuloImpressao: String get() = when (tipo) {
    TipoComanda.MESA -> "MESA $numero"
    TipoComanda.BALCAO -> "BALCÃO $sequencia"
    TipoComanda.DELIVERY ->
        if (nomeCliente.isNotEmpty()) "DELIVERY $sequencia - $nomeCliente" else "DELIVERY $sequencia"
}

/** Comanda aberta com o total já somado, para a tela de mesas. */
data class ComandaResumo(
    @Embedded val comanda: Comanda,
    val totalCentavos: Long,
    val qtdItens: Int
)
