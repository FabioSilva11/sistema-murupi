package br.com.murupi.comandas.data

import br.com.murupi.comandas.data.model.ConfigRestaurante
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dados do restaurante usados nas telas e nos tickets. Carregados do banco na inicialização
 * (padrões legados enquanto não há configuração salva) e atualizados pela tela de configurações.
 */
object Restaurante {

    /** Enquanto não há configuração salva, valem os padrões de sempre. */
    private val padrao = ConfigRestaurante(nome = "MURUPI RESTAURANTE", agradecimento = "O Restaurante Murupi agradece a preferência.")

    private val configAtual = MutableStateFlow(padrao)

    /** Observável para a tela de configurações preencher os campos. */
    val config: Flow<ConfigRestaurante> get() = configAtual

    val NOME: String get() = configAtual.value.nome
    val AGRADECIMENTO: String get() = configAtual.value.agradecimento
    val TOTAL_MESAS: Int get() = configAtual.value.totalMesas
    val TAXA_SERVICO_PERCENTUAL: Int get() = configAtual.value.taxaServicoPercentual

    /** Aplica a configuração salva no banco; campos vazios/caidos mantêm os padrões. */
    fun aplicar(config: ConfigRestaurante) {
        configAtual.value = config.copy(
            nome = config.nome.trim().ifEmpty { padrao.nome },
            agradecimento = config.agradecimento.trim().ifEmpty { padrao.agradecimento },
            totalMesas = config.totalMesas.takeIf { it > 0 } ?: padrao.totalMesas
        )
    }
}
