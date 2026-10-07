package br.com.murupi.comandas.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.murupi.comandas.data.model.ConfigRestaurante
import br.com.murupi.comandas.data.repo.ConfigRepository
import kotlinx.coroutines.launch

class ConfigViewModel(private val repo: ConfigRepository) : ViewModel() {

    /** Última configuração carregada/validada pela tela, pronta para gravar. */
    var atual: ConfigRestaurante = ConfigRestaurante()

    /** Carrega a configuração salva (nulo = nunca salva: usar padrões). */
    suspend fun carregar(): ConfigRestaurante? = repo.carregar()

    fun salvar(config: ConfigRestaurante, aoConcluir: () -> Unit) {
        viewModelScope.launch {
            repo.salvar(config)
            aoConcluir()
        }
    }
}
