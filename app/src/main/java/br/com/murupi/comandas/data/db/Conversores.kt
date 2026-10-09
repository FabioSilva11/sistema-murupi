package br.com.murupi.comandas.data.db

import androidx.room.TypeConverter
import br.com.murupi.comandas.data.model.PapelImpressora

/** Papéis da impressora gravados como texto separado por vírgula (ex.: "COZINHA,SUCOS"). */
class Conversores {

    @TypeConverter
    fun papeisParaTexto(papeis: Set<PapelImpressora>): String =
        papeis.sorted().joinToString(",") { it.name }

    @TypeConverter
    fun textoParaPapeis(texto: String?): Set<PapelImpressora> {
        if (texto.isNullOrBlank()) return emptySet()
        return texto.split(",").mapNotNull { nome ->
            runCatching { PapelImpressora.valueOf(nome.trim()) }.getOrNull()
        }.toSet()
    }
}
