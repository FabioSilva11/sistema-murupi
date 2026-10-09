package br.com.murupi.comandas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Impressora térmica de rede (ESC/POS, normalmente na porta 9100). */
@Entity(tableName = "impressoras")
data class Impressora(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val ip: String,
    val porta: Int = PORTA_PADRAO,
    /** Papéis que saem nesta impressora (ex.: cozinha + sucos numa casa com 1 impressora). */
    val papeis: Set<PapelImpressora> = emptySet(),
    /** Caracteres por linha: 48 para bobina de 80 mm, 32 para 58 mm. */
    val colunas: Int = COLUNAS_80MM,
    /** Muitas térmicas imprimem lixo em letras acentuadas; sem acento é o mais seguro. */
    val removerAcentos: Boolean = true,
    val ativa: Boolean = true,
    val criadaEm: Long = System.currentTimeMillis(),
    val ultimoUsoEm: Long? = null
) {
    companion object {
        const val PORTA_PADRAO = 9100
        const val COLUNAS_80MM = 48
        const val COLUNAS_58MM = 32
    }
}

/** Rótulo dos papéis para telas e tickets (ex.: "Cozinha + Sucos"). */
val Impressora.rotuloPapeis: String
    get() = if (papeis.isEmpty()) "—"
    else papeis.sorted().joinToString(" + ") { it.descricao }
