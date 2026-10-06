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
    suspend fun imprimirProducao(comanda: Comanda, itens: List<ItemComanda>, reimpressao: Boolean): Resultado {
        if (itens.isEmpty()) return Resultado()
        val impressoras = impressoraDao.listarAtivas()
        val porPapel = RoteadorImpressao.separarProducao(itens, impressoras.map { it.papel }.toSet())
        val envios = mutableListOf<Envio>()
        val semImpressora = mutableListOf<PapelImpressora>()

        for ((papel, itensDoPapel) in porPapel) {
            val destinos = impressoras.filter { it.papel == papel }
            if (destinos.isEmpty()) {
                semImpressora += papel
                continue
            }
            // Na cozinha, as bebidas saem abaixo dos pratos.
            val ordenados = itensDoPapel.sortedBy { RoteadorImpressao.ordemNoTicket(it.setor) }
            val enviosDoPapel = destinos.map { impressora ->
                val ticket = Tickets.producao(impressora, papel, comanda, ordenados, reimpressao)
                Envio(impressora, enviar(impressora, ticket))
            }
            envios += enviosDoPapel
            if (enviosDoPapel.any { it.erro == null }) itemDao.marcarEnviados(itensDoPapel.map { it.id })
        }
        return Resultado(envios, semImpressora)
    }

    suspend fun imprimirEspelho(comanda: Comanda, itens: List<ItemComanda>): Resultado {
        val destinos = impressoraDao.listarAtivas().filter { it.papel == PapelImpressora.ESPELHO }
        if (destinos.isEmpty()) return Resultado(papeisSemImpressora = listOf(PapelImpressora.ESPELHO))
        return Resultado(destinos.map { Envio(it, enviar(it, Tickets.espelho(it, comanda, itens))) })
    }

    /** Prévia do que será impresso, sem enviar nada para as impressoras. */
    data class Previa(val papel: String, val impressoras: String, val texto: String)

    /** Texto de cada papel da produção, para conferir na tela antes de aprovar. */
    suspend fun preverProducao(
        comanda: Comanda,
        itens: List<ItemComanda>,
        reimpressao: Boolean
    ): List<Previa> {
        if (itens.isEmpty()) return emptyList()
        val ativas = impressoraDao.listarAtivas()
        val porPapel = RoteadorImpressao.separarProducao(itens, ativas.map { it.papel }.toSet())
        return porPapel.map { (papel, itensDoPapel) ->
            val destinos = ativas.filter { it.papel == papel }
            val largura = destinos.firstOrNull()?.colunas ?: Impressora.COLUNAS_58MM
            val ordenados = itensDoPapel.sortedBy { RoteadorImpressao.ordemNoTicket(it.setor) }
            Previa(
                papel = papel.descricao,
                impressoras = destinos.joinToString { it.nome }.ifEmpty { "sem impressora ativa" },
                texto = Tickets.producaoTexto(largura, papel, comanda, ordenados, reimpressao)
            )
        }
    }

    /** Texto do espelho, para conferir na tela antes de aprovar. */
    suspend fun preverEspelho(comanda: Comanda, itens: List<ItemComanda>): Previa {
        val destinos = impressoraDao.listarAtivas().filter { it.papel == PapelImpressora.ESPELHO }
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
