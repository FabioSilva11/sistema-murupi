package br.com.murupi.comandas.util

object Rede {

    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")

    fun ipValido(ip: String): Boolean = IPV4.matches(ip)
}
