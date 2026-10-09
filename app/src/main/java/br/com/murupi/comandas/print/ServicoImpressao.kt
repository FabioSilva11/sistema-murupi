package br.com.murupi.comandas.print

import br.com.murupi.comandas.data.db.ImpressoraDao
import br.com.murupi.comandas.data.db.ItemComandaDao
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.PapelImpressora
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ServicoImpressao(
    private val impressoraDao: ImpressoraDao,
    private val itemDao: ItemComandaDao
) {

    /** [erro] nulo significa que a impressora recebeu o ticket. */
    data class Envio(val impressora: Impressora, val erro: String?)

    data class Resultado(
        val envios: List<Envio> = emptyList(),
        val papeisSemImpressora: List<PapelImpressora> = emptyList()
    ) {
        val vazio: Boolean get() = envios.isEmpty() && papeisSemImpressora.isEmpty()
        val sucesso: Boolean get() = envios.isNotEmpty() && envios.all { it.erro == null } && papeisSemImpressora.isEmpty()
    }

    /**
     * Separa os itens por setor e manda cada grupo para as impressoras daquele papel. Os itens
     * só são marcados como enviados quando ao menos uma impressora do setor recebeu o ticket;
     * os demais continuam pendentes para a próxima tentativa.
     */
    suspend fun imprimirProducao(
        comanda: Comanda,
        itens: List<ItemComanda>,
        reimpressao: Boolean,
        cancelamentosPendentes: List<ItemComanda> = emptyList()
    ): Resultado {
        if (itens.isEmpty() && cancelamentosPendentes.isEmpty()) return Resultado()
        val impressoras = impressoraDao.listarAtivas()
        val envios = mutableListOf<Envio>()
        val semImpressora = mutableListOf<PapelImpressora>()

        for (grupo in agruparProducao(itens, cancelamentosPendentes, reimpressao)) {
            val papel = grupo.papel
            val destinos = impressoras.filter { papel in it.papeis }
            if (destinos.isEmpty()) {
                semImpressora += papel
                continue
            }
            // Na cozinha, as bebidas saem abaixo dos pratos.
            val ordenados = grupo.itensAtuais.sortedBy { RoteadorImpressao.ordemNoTicket(it.setor) }
            val enviosDoPapel = destinos.map { impressora ->
                val ticket = if (grupo.cancelamentos.isNotEmpty()) {
                    Tickets.atualizacaoProducao(impressora, papel, comanda, ordenados, grupo.cancelamentos)
                } else {
                    Tickets.producao(impressora, papel, comanda, ordenados, reimpressao)
                }
                Envio(impressora, enviar(impressora, ticket))
            }
            envios += enviosDoPapel
            if (enviosDoPapel.any { it.erro == null } && grupo.itensAtuais.isNotEmpty()) {
                itemDao.marcarEnviados(grupo.itensAtuais.map { it.id })
            }
            // Só baixa o aviso da fila quando todos os destinos desta estação confirmaram o envio.
            if (enviosDoPapel.all { it.erro == null } && grupo.cancelamentos.isNotEmpty()) {
                itemDao.marcarCancelamentosImpressos(grupo.cancelamentos.map { it.id })
            }
        }
        return Resultado(envios, semImpressora)
    }

    suspend fun imprimirEspelho(comanda: Comanda, itens: List<ItemComanda>): Resultado {
        val destinos = impressoraDao.listarAtivas().filter { PapelImpressora.ESPELHO in it.papeis }
        if (destinos.isEmpty()) return Resultado(papeisSemImpressora = listOf(PapelImpressora.ESPELHO))
        return Resultado(destinos.map { Envio(it, enviar(it, Tickets.espelho(it, comanda, itens))) })
    }

    /** Prévia do que será impresso, sem enviar nada para as impressoras. */
    data class Previa(val papel: String, val impressoras: String, val texto: String)

    /** Texto de cada papel da produção, para conferir na tela antes de aprovar. */
    suspend fun preverProducao(
        comanda: Comanda,
        itens: List<ItemComanda>,
        reimpressao: Boolean,
        cancelamentosPendentes: List<ItemComanda> = emptyList()
    ): List<Previa> {
        if (itens.isEmpty() && cancelamentosPendentes.isEmpty()) return emptyList()
        val ativas = impressoraDao.listarAtivas()
        return agruparProducao(itens, cancelamentosPendentes, reimpressao).map { grupo ->
            val papel = grupo.papel
            val destinos = ativas.filter { papel in it.papeis }
            val largura = destinos.firstOrNull()?.colunas ?: Impressora.COLUNAS_58MM
            val ordenados = grupo.itensAtuais.sortedBy { RoteadorImpressao.ordemNoTicket(it.setor) }
            Previa(
                papel = papel.descricao,
                impressoras = destinos.joinToString { it.nome }.ifEmpty { "sem impressora ativa" },
                texto = if (grupo.cancelamentos.isNotEmpty()) {
                    Tickets.atualizacaoProducaoTexto(largura, papel, comanda, ordenados, grupo.cancelamentos)
                } else {
                    Tickets.producaoTexto(largura, papel, comanda, ordenados, reimpressao)
                }
            )
        }
    }

    private data class GrupoProducao(
        val papel: PapelImpressora,
        val itensAtuais: List<ItemComanda>,
        val cancelamentos: List<ItemComanda>
    )

    /**
     * Para uma estação com cancelamento pendente, envia o pedido ativo inteiro daquela estação;
     * as outras estações continuam recebendo só itens novos, salvo uma reimpressão explícita.
     */
    private fun agruparProducao(
        itens: List<ItemComanda>,
        cancelamentos: List<ItemComanda>,
        reimpressao: Boolean
    ): List<GrupoProducao> {
        val ativosPorPapel = itens.groupBy { RoteadorImpressao.papelDoSetor(it.setor, emptySet()) }
        val canceladosPorPapel = cancelamentos.groupBy { RoteadorImpressao.papelDoSetor(it.setor, emptySet()) }
        val papeis = (ativosPorPapel.keys + canceladosPorPapel.keys).distinct().sorted()
        return papeis.mapNotNull { papel ->
            val ativos = ativosPorPapel[papel].orEmpty()
            val cancelados = canceladosPorPapel[papel].orEmpty()
            val selecionados = when {
                reimpressao || cancelados.isNotEmpty() -> ativos
                else -> ativos.filterNot { it.enviadoProducao }
            }
            if (selecionados.isEmpty() && cancelados.isEmpty()) null
            else GrupoProducao(papel, selecionados, cancelados)
        }
    }

    /** Texto do espelho, para conferir na tela antes de aprovar. */
    suspend fun preverEspelho(comanda: Comanda, itens: List<ItemComanda>): Previa {
        val destinos = impressoraDao.listarAtivas().filter { PapelImpressora.ESPELHO in it.papeis }
        val largura = destinos.firstOrNull()?.colunas ?: Impressora.COLUNAS_58MM
        return Previa(
            papel = PapelImpressora.ESPELHO.descricao,
            impressoras = destinos.joinToString { it.nome }.ifEmpty { "sem impressora ativa" },
            texto = Tickets.espelhoTexto(largura, comanda, itens)
        )
    }

    /** Retorna null se imprimiu, ou a descrição do erro. */
    suspend fun imprimirTeste(impressora: Impressora): String? = enviar(impressora, Tickets.teste(impressora))

    /**
     * Imprime o histórico completo (contas fechadas + saldo total) na impressora
     * escolhida. Retorna null se imprimiu, ou a descrição do erro.
     */
    suspend fun imprimirHistorico(
        impressora: Impressora,
        contas: List<Pair<Comanda, List<ItemComanda>>>
    ): String? = enviar(impressora, Tickets.historico(impressora, contas))

    private suspend fun enviar(impressora: Impressora, dados: ByteArray): String? = try {
        ClienteImpressora.enviar(impressora.ip, impressora.porta, dados)
        impressoraDao.registrarUso(impressora.id, System.currentTimeMillis())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        descreverErro(e)
    }

    private fun descreverErro(e: Exception): String = when (e) {
        is SocketTimeoutException -> "sem resposta (impressora desligada ou fora da rede?)"
        is NoRouteToHostException -> "IP inalcançável nesta rede"
        is ConnectException -> "conexão recusada (confira o IP e a porta)"
        is UnknownHostException -> "endereço inválido"
        else -> e.message ?: e.javaClass.simpleName
    }
}
