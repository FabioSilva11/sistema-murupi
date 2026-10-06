package br.com.murupi.comandas.print

/** Diagramação de texto em largura fixa (colunas da bobina). */
object TextoTicket {

    private val ESPACOS = Regex("\\s+")

    /**
     * Quebra [texto] em linhas de no máximo [largura] colunas sem cortar palavras (só corta
     * palavras maiores que a linha). As linhas de continuação começam com [recuo].
     */
    fun quebrar(texto: String, largura: Int, recuo: String = ""): List<String> {
        require(largura > recuo.length) { "Largura $largura menor que o recuo" }
        val linhas = mutableListOf<String>()
        var linha = StringBuilder()
        for (palavraInteira in texto.trim().split(ESPACOS)) {
            var palavra = palavraInteira
            while (palavra.isNotEmpty()) {
                val separador = when {
                    linha.isNotEmpty() -> " "
                    linhas.isEmpty() -> ""
                    else -> recuo
                }
                if (linha.length + separador.length + palavra.length <= largura) {
                    linha.append(separador).append(palavra)
                    palavra = ""
                } else if (linha.isNotEmpty()) {
                    linhas += linha.toString()
                    linha = StringBuilder()
                } else {
                    val cabe = largura - separador.length
                    linhas += separador + palavra.take(cabe)
                    palavra = palavra.drop(cabe)
                }
            }
        }
        if (linha.isNotEmpty() || linhas.isEmpty()) linhas += linha.toString()
        return linhas
    }

    /** Texto à esquerda e [direita] encostado na margem direita, numa linha só. */
    fun colunas(esquerda: String, direita: String, largura: Int): String {
        val espacoEsquerda = largura - direita.length - 1
        val esq = if (esquerda.length > espacoEsquerda) esquerda.take(maxOf(0, espacoEsquerda)) else esquerda
        return esq + " ".repeat(maxOf(1, largura - esq.length - direita.length)) + direita
    }

    /**
     * Descrição quebrada em quantas linhas precisar, com [valor] alinhado à direita na última
     * (ou numa linha própria, se não couber).
     */
    fun comValor(descricao: String, valor: String, largura: Int, recuo: String = "   "): List<String> {
        val linhas = quebrar(descricao, largura, recuo)
        val ultima = linhas.last()
        return if (ultima.length + 1 + valor.length <= largura) {
            linhas.dropLast(1) + colunas(ultima, valor, largura)
        } else {
            linhas + colunas("", valor, largura)
        }
    }
}
