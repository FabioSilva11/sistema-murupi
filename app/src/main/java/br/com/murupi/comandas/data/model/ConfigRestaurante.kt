package br.com.murupi.comandas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Configurações do estabelecimento, editadas na tela de configurações. */
@Entity(tableName = "config_restaurante")
data class ConfigRestaurante(
    /** Linha única: sempre 1. */
    @PrimaryKey val id: Int = 1,
    /** Nome impresso no topo dos tickets. Vazio mantém o padrão do app. */
    val nome: String = "",
    /** Mensagem no fim do espelho da conta. Vazio mantém o padrão do app. */
    val agradecimento: String = "",
    /** Quantidade de mesas do salão (1 a 200). */
    val totalMesas: Int = 60,
    /** Percentual de taxa de serviço sugerido no pagamento (0 desativa). */
    val taxaServicoPercentual: Int = 0
)
