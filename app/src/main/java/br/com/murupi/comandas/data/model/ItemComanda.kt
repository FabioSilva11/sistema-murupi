package br.com.murupi.comandas.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item lançado numa comanda. Nome, preço e roteamento são copiados do cardápio no lançamento,
 * para que mudanças posteriores no cardápio não alterem pedidos já feitos.
 */
@Entity(
    tableName = "itens_comanda",
    foreignKeys = [
        ForeignKey(
            entity = Comanda::class,
            parentColumns = ["id"],
            childColumns = ["comandaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("comandaId")]
)
data class ItemComanda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comandaId: Long,
    val produtoId: Long?,
    val nome: String,
    val categoriaNome: String,
    val setor: Setor,
    val demanda: Boolean,
    /** O produto não tem preço fixo: o preço pode ser corrigido depois do lançamento. */
    val precoLivre: Boolean,
    val quantidade: Int,
    val precoUnitarioCentavos: Long,
    val observacao: String = "",
    /** Já saiu na impressora de produção (cozinha/sucos/refrigerantes). */
    val enviadoProducao: Boolean = false,
    val criadoEm: Long = System.currentTimeMillis(),
    /** Copiado do produto: a quantidade só muda de N em N. */
    @ColumnInfo(defaultValue = "1") val multiplo: Int = 1
)

val ItemComanda.totalCentavos: Long get() = quantidade * precoUnitarioCentavos
