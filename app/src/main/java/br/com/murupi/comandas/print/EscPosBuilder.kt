package br.com.murupi.comandas.print

import br.com.murupi.comandas.util.Texto
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA }

/**
 * Monta os bytes de um ticket ESC/POS (padrão Epson, aceito pela maioria das térmicas).
 * [colunasFonte] é a largura em caracteres da fonte normal: 48 para 80 mm, 32 para 58 mm.
 * Com [removerAcentos] = false o texto sai na tabela PC850, que tem os acentos do português.
 */
class EscPosBuilder(private val colunasFonte: Int, removerAcentos: Boolean) {

    private val saida = ByteArrayOutputStream()
    private val charset: Charset? = if (removerAcentos) null else CP850
    private var larguraDupla = false

    /** Caracteres por linha no tamanho de fonte atual. */
    val largura: Int get() = if (larguraDupla) colunasFonte / 2 else colunasFonte

    init {
        comando(ESC, '@'.code)
        if (charset != null) comando(ESC, 't'.code, TABELA_PC850)
    }

    fun alinhar(alinhamento: Alinhamento) = apply { comando(ESC, 'a'.code, alinhamento.ordinal) }

    fun negrito(ativo: Boolean) = apply { comando(ESC, 'E'.code, if (ativo) 1 else 0) }

    /** Branco no preto: usado para destacar observações na produção. */
    fun inverso(ativo: Boolean) = apply { comando(GS, 'B'.code, if (ativo) 1 else 0) }

    fun tamanho(larguraDupla: Boolean, alturaDupla: Boolean) = apply {
        this.larguraDupla = larguraDupla
        comando(GS, '!'.code, (if (larguraDupla) 0x10 else 0) or (if (alturaDupla) 0x01 else 0))
    }

    fun normal() = tamanho(larguraDupla = false, alturaDupla = false).negrito(false).inverso(false)

    fun linha(texto: String = "") = apply {
        escrever(texto)
        saida.write(LF)
    }

    fun linhas(textos: List<String>) = apply { textos.forEach { linha(it) } }

    fun paragrafo(texto: String, recuo: String = "") = linhas(TextoTicket.quebrar(texto, largura, recuo))

    /** Rótulo à esquerda e valor à direita; o rótulo quebra se for comprido. */
    fun duasColunas(esquerda: String, direita: String) = linhas(TextoTicket.comValor(esquerda, direita, largura))

    fun separador(caractere: Char = '-') = linha(caractere.toString().repeat(largura))

    fun avancar(linhas: Int) = apply { comando(ESC, 'd'.code, linhas) }

    /** Avança o papel até passar da lâmina e faz corte parcial. */
    fun cortar() = avancar(4).apply { comando(GS, 'V'.code, 1) }

    fun bytes(): ByteArray = saida.toByteArray()

    private fun escrever(texto: String) {
        val bytes = charset?.let { texto.toByteArray(it) }
            ?: Texto.semAcentos(texto).toByteArray(Charsets.US_ASCII)
        saida.write(bytes)
    }

    private fun comando(vararg bytes: Int) = bytes.forEach { saida.write(it) }

    companion object {
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val LF = 0x0A
        private const val TABELA_PC850 = 2
        private val CP850: Charset? = runCatching { Charset.forName("IBM850") }.getOrNull()
    }
}
