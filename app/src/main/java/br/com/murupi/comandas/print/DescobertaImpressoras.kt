package br.com.murupi.comandas.print

import android.content.Context
import android.net.ConnectivityManager
import br.com.murupi.comandas.data.model.Impressora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Procura impressoras de rede testando a porta 9100 em todos os IPs da sub-rede /24 do aparelho
 * (ex.: 192.168.0.1 a 192.168.0.254). Térmicas baratas não anunciam serviço na rede, então a
 * varredura de porta é o método que funciona com todas.
 */
class DescobertaImpressoras(private val context: Context) {

    sealed interface Evento {
        data class Iniciada(val subRede: String, val total: Int) : Evento
        data class Progresso(val verificados: Int, val total: Int) : Evento
        data class Encontrada(val ip: String) : Evento
        data object SemRede : Evento
    }

    fun varrer(porta: Int = Impressora.PORTA_PADRAO): Flow<Evento> = channelFlow {
        val ip = ipLocal()
        if (ip == null) {
            send(Evento.SemRede)
            return@channelFlow
        }
        val octetos = ip.address.map { it.toInt() and 0xFF }
        val prefixo = "${octetos[0]}.${octetos[1]}.${octetos[2]}."
        val hosts = (1..254).filter { it != octetos[3] }
        send(Evento.Iniciada("${prefixo}x", hosts.size))

        val limite = Semaphore(PARALELISMO)
        val verificados = AtomicInteger()
        hosts.forEach { host ->
            launch(Dispatchers.IO) {
                val alvo = prefixo + host
                val aberta = limite.withPermit { portaAberta(alvo, porta) }
                if (aberta) send(Evento.Encontrada(alvo))
                send(Evento.Progresso(verificados.incrementAndGet(), hosts.size))
            }
        }
    }

    /** IPv4 do aparelho na rede atual (Wi-Fi), ou null se estiver sem rede. */
    private fun ipLocal(): Inet4Address? {
        val conectividade = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val rede = conectividade.activeNetwork ?: return null
        return conectividade.getLinkProperties(rede)?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
    }

    private fun portaAberta(ip: String, porta: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, porta), TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        false
    }

    private companion object {
        const val PARALELISMO = 32
        const val TIMEOUT_MS = 500
    }
}
