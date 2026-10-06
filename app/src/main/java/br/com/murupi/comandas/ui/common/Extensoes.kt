package br.com.murupi.comandas.ui.common

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.murupi.comandas.AppContainer
import br.com.murupi.comandas.MurupiApp
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.model.numero
import br.com.murupi.comandas.databinding.DialogCampoTextoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val Activity.app: AppContainer get() = (application as MurupiApp).container

/** Título da comanda na barra: "Mesa 9.1", "Balcão 1" ou o nome do delivery. */
fun Comanda.tituloBarra(context: Context): String = when (tipo) {
    TipoComanda.MESA -> context.getString(R.string.titulo_comanda, numero)
    TipoComanda.BALCAO -> context.getString(R.string.titulo_balcao, sequencia)
    TipoComanda.DELIVERY -> nomeCliente.ifEmpty { context.getString(R.string.titulo_delivery, sequencia) }
}

/** ViewModel criado com as dependências do [AppContainer]. */
inline fun <reified VM : ViewModel> ComponentActivity.viewModelsDoApp(
    noinline criar: (AppContainer) -> VM
): Lazy<VM> = viewModels { viewModelFactory { initializer { criar(app) } } }

/** Mesmo ViewModel para todos os fragmentos da tela inicial (escopo da Activity). */
inline fun <reified VM : ViewModel> androidx.fragment.app.Fragment.viewModelsDaAtividade(
    noinline criar: (AppContainer) -> VM
): Lazy<VM> = lazy(LazyThreadSafetyMode.NONE) {
    val fabrica = viewModelFactory { initializer { criar((requireActivity().application as MurupiApp).container) } }
    androidx.lifecycle.ViewModelProvider(requireActivity(), fabrica)[VM::class.java]
}

/** Executa [bloco] enquanto a tela estiver visível (reinicia ao voltar para ela). */
fun LifecycleOwner.coletar(bloco: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED, bloco) }
}

/** Soma ao padding da view o espaço das barras do sistema (o app desenha atrás delas). */
fun View.aplicarInsets(topo: Boolean = false, base: Boolean = false) {
    val esquerda = paddingLeft
    val cima = paddingTop
    val direita = paddingRight
    val baixo = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(
            esquerda + barras.left,
            if (topo) cima + barras.top else cima,
            direita + barras.right,
            if (base) baixo + barras.bottom else baixo
        )
        insets
    }
}

/** O botão positivo só fecha o diálogo quando [acao] retorna true (permite validar campos). */
fun AlertDialog.aoConfirmar(acao: () -> Boolean) {
    setOnShowListener {
        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (acao()) dismiss() }
    }
}

/** Diálogo com um campo de texto. [validar] devolve a mensagem de erro, ou null se estiver ok. */
fun Context.pedirTexto(
    titulo: String,
    rotulo: String,
    valorInicial: String = "",
    tipoEntrada: Int = InputType.TYPE_CLASS_TEXT,
    validar: (String) -> String? = { null },
    aoConfirmar: (String) -> Unit
) {
    val b = DialogCampoTextoBinding.inflate(LayoutInflater.from(this))
    b.layoutCampo.hint = rotulo
    b.editCampo.inputType = tipoEntrada
    b.editCampo.setText(valorInicial)
    b.editCampo.setSelection(valorInicial.length)
    val dialogo = MaterialAlertDialogBuilder(this)
        .setTitle(titulo)
        .setView(b.root)
        .setNegativeButton(R.string.cancelar, null)
        .setPositiveButton(R.string.ok, null)
        .create()
    dialogo.aoConfirmar {
        val texto = b.editCampo.text?.toString().orEmpty().trim()
        val erro = validar(texto)
        b.layoutCampo.error = erro
        if (erro == null) aoConfirmar(texto)
        erro == null
    }
    dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    b.editCampo.requestFocus()
    dialogo.show()
}
