package br.com.murupi.comandas.print

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Envia bytes crus para a impressora pela rede (modo RAW / JetDirect, porta 9100). */
object ClienteImpressora {

    private const val TIMEOUT_MS = 4000

    suspend fun enviar(ip: String, porta: Int, dados: ByteArray) = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, porta), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            socket.getOutputStream().run {
                write(dados)
                flush()
            }
        }
    }
}
