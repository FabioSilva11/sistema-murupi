package br.com.murupi.comandas.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "produtos",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoriaId")]
)
data class Produto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoriaId: Long,
    val nome: String,
    /** Preço em centavos. Zero significa preço livre, digitado no lançamento. */
    val precoCentavos: Long,
    /** Produtos do mesmo grupo (ex.: "Graviola") aparecem juntos e expansíveis no lançamento. */
    val grupo: String? = null,
    /** Quantidade disponível; null quando o produto não tem controle de estoque. */
    val estoque: Int? = null,
    /** Produto oculto (false) não aparece para lançar, mas continua no catálogo. */
    @ColumnInfo(defaultValue = "1") val ativo: Boolean = true,
    /** Vendido de N em N (ex.: entradas de 5 em 5 a R$ 1,00 cada). */
    @ColumnInfo(defaultValue = "1") val multiplo: Int = 1,
    /** Composição exibida abaixo do nome (ex.: sucos numerados mostram do que são feitos). */
    @ColumnInfo(defaultValue = "''") val descricao: String = ""
)

val Produto.precoLivre: Boolean get() = precoCentavos <= 0

val Produto.esgotado: Boolean get() = estoque != null && estoque <= 0

/** Nome curto dentro do grupo: "Suco de Graviola 300ml" no grupo "Graviola" vira "300ml". */
fun Produto.rotuloNoGrupo(): String {
    val nomeGrupo = grupo?.trim().orEmpty()
    if (nomeGrupo.isEmpty()) return nome
    val inicio = nome.indexOf(nomeGrupo, ignoreCase = true)
    if (inicio < 0) return nome
    return nome.substring(inicio + nomeGrupo.length).trim().ifEmpty { nome }
}

/** Produto junto com a categoria dele (cor, setor e regras de lançamento). */
data class ProdutoItem(val produto: Produto, val categoria: Categoria)
