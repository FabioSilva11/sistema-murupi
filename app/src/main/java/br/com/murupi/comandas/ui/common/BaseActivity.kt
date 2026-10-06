package br.com.murupi.comandas.ui.common

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import br.com.murupi.comandas.databinding.AppBarBinding
import com.google.android.material.snackbar.Snackbar

/** Base das telas: barra azul por trás da status bar, toolbar como action bar e Snackbar. */
abstract class BaseActivity : AppCompatActivity() {

    /** View acima da qual o Snackbar aparece (ex.: o FAB), para não cobrir botões. */
    protected open val ancoraSnackbar: View? get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
    }

    protected fun configurarBarra(barra: AppBarBinding, voltar: Boolean) {
        setSupportActionBar(barra.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(voltar)
        barra.root.aplicarInsets(topo = true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    protected fun avisar(texto: CharSequence): Snackbar =
        Snackbar.make(findViewById(android.R.id.content), texto, Snackbar.LENGTH_LONG).apply {
            ancoraSnackbar?.let { anchorView = it }
            show()
        }

    protected fun avisar(@StringRes texto: Int, vararg args: Any): Snackbar = avisar(getString(texto, *args))

    protected fun avisar(mensagem: Mensagem): Snackbar = avisar(mensagem.texto(this))
}
