package br.com.murupi.comandas.ui.common

import android.content.Context
import androidx.annotation.StringRes

/** Texto gerado no ViewModel sem depender de Context; a tela resolve na hora de mostrar. */
class Mensagem(@StringRes val res: Int, vararg val args: Any) {
    fun texto(context: Context): String = context.getString(res, *args)
}
