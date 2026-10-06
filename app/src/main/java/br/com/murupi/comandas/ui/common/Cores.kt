package br.com.murupi.comandas.ui.common

import android.graphics.Color
import androidx.core.graphics.ColorUtils

object Cores {

    fun converter(hex: String, padrao: Int = Color.GRAY): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        padrao
    }

    /** Branco sobre cores escuras, preto sobre cores claras (amarelo, verde-claro...). */
    fun textoSobre(fundo: Int): Int = if (ColorUtils.calculateLuminance(fundo) > 0.45) Color.BLACK else Color.WHITE
}
