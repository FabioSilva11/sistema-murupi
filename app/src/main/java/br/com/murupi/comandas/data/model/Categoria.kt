package br.com.murupi.comandas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    /** Cor do botão no formato "#RRGGBB". */
    val cor: String,
    val ordem: Int,
    val setor: Setor,
    /** Itens desta categoria saem destacados com ">>>" na comanda de produção. */
    val demanda: Boolean = false,
    /** Ao lançar, pergunta o preparo do suco (com leite, sem açúcar, ...). */
    val perguntarOpcoesSuco: Boolean = false
)
