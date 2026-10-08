package br.com.murupi.comandas.data.seed

/**
 * Catálogo padronizado (chaves exatas do JSON em assets/catalogo_padronizado.json).
 *
 * Dois tipos de produto:
 * - simples: possui [preco]/[precoCentavos] diretos, sem variações.
 * - variacoes: sem preço próprio; possui [variacoes], cada uma com
 *   [VariacaoPadronizada.tamanho] livre (ex.: "250ml", "10") e preço próprio.
 * Nenhum preço é inventado: tudo vem das chaves do JSON.
 */
data class VariacaoPadronizada(
    val id: Long,
    val nome: String,
    val tamanho: String?,
    val preco: Double,
    val precoCentavos: Long,
    val categoria: String,
    val categoriaId: Int,
    val descricao: String,
    val estoque: Int?,
    val ativo: Boolean,
    val multiplo: Int
)

data class ProdutoPadronizado(
    /** Simples usam número (ex.: 22); grupos de variação usam texto (ex.: "Acerola"). */
    val id: String,
    val nome: String,
    val categoria: String,
    val categoriaId: Int,
    val preco: Double?,
    val precoCentavos: Long?,
    val descricao: String,
    val estoque: Int?,
    val ativo: Boolean,
    val multiplo: Int,
    val tipo: String,
    val variacoes: List<VariacaoPadronizada>
) {
    val comVariacoes: Boolean get() = tipo == "variacoes"
}

data class CategoriaPadronizada(val id: Int, val nome: String)

data class CatalogoPadronizado(
    val totalCategorias: Int,
    val totalProdutos: Int,
    val totalVariacoes: Int,
    val categorias: List<CategoriaPadronizada>,
    val produtos: List<ProdutoPadronizado>
)

/** Linha pronta para virar [Produto] no banco (grupo = id do produto com variações). */
data class ItemSeed(
    val categoria: String,
    val nome: String,
    val precoCentavos: Long,
    val grupo: String?,
    val descricao: String,
    val multiplo: Int,
    val estoque: Int?,
    val ativo: Boolean
)

/** Expande o catálogo: 1 simples = 1 item; N variações = N itens com o mesmo grupo. */
fun CatalogoPadronizado.expandir(): List<ItemSeed> = buildList {
    produtos.forEach { produto ->
        if (produto.comVariacoes) {
            produto.variacoes.forEach { v ->
                add(
                    ItemSeed(
                        categoria = v.categoria,
                        nome = v.nome,
                        precoCentavos = v.precoCentavos,
                        grupo = produto.id,
                        descricao = v.descricao,
                        multiplo = v.multiplo,
                        estoque = v.estoque,
                        ativo = v.ativo
                    )
                )
            }
        } else {
            add(
                ItemSeed(
                    categoria = produto.categoria,
                    nome = produto.nome,
                    precoCentavos = produto.precoCentavos
                        ?: throw IllegalArgumentException("Produto simples sem precoCentavos: ${produto.nome}"),
                    grupo = null,
                    descricao = produto.descricao,
                    multiplo = produto.multiplo,
                    estoque = produto.estoque,
                    ativo = produto.ativo
                )
            )
        }
    }
}

// ---------- Leitura do JSON (parser próprio, sem dependências) ----------

private sealed interface JVal {
    data class Obj(val v: Map<String, JVal>) : JVal
    data class Arr(val v: List<JVal>) : JVal
    data class Str(val v: String) : JVal
    data class Num(val v: Double) : JVal
    data class Bool(val v: Boolean) : JVal
    data object Nulo : JVal
}

private class ErroJson(mensagem: String, posicao: Int) : IllegalArgumentException("$mensagem (posição $posicao)")

private class LeitorJson(private val texto: String) {
    private var i = 0

    fun ler(): JVal {
        espacos()
        val valor = valor()
        espacos()
        if (i != texto.length) throw ErroJson("Conteúdo após o fim do JSON", i)
        return valor
    }

    private fun espacos() {
        while (i < texto.length && texto[i].isWhitespace()) i++
    }

    private fun valor(): JVal = when (val c = proximo()) {
        '{' -> objeto()
        '[' -> lista()
        '"' -> JVal.Str(textoLiteral())
        't' -> literal("true", JVal.Bool(true))
        'f' -> literal("false", JVal.Bool(false))
        'n' -> literal("null", JVal.Nulo)
        '-', in '0'..'9' -> numero()
        else -> throw ErroJson("Valor inesperado: '$c'", i - 1)
    }

    private fun proximo(): Char {
        if (i >= texto.length) throw ErroJson("Fim inesperado do JSON", i)
        return texto[i++]
    }

    private fun exige(c: Char) {
        if (proximo() != c) throw ErroJson("Esperava '$c'", i - 1)
    }

    private fun literal(esperado: String, valor: JVal): JVal {
        val inicio = i - 1
        if (!texto.startsWith(esperado, inicio)) throw ErroJson("Literal inválido", inicio)
        i = inicio + esperado.length
        return valor
    }

    private fun objeto(): JVal.Obj {
        val mapa = LinkedHashMap<String, JVal>()
        espacos()
        if (i < texto.length && texto[i] == '}') {
            i++
            return JVal.Obj(mapa)
        }
        while (true) {
            espacos()
            if (i >= texto.length || texto[i] != '"') throw ErroJson("Esperava chave de texto", i)
            i++
            val chave = textoLiteral()
            espacos()
            exige(':')
            espacos()
            mapa[chave] = valor()
            espacos()
            when (proximo()) {
                ',' -> continue
                '}' -> break
                else -> throw ErroJson("Esperava ',' ou '}'", i - 1)
            }
        }
        return JVal.Obj(mapa)
    }

    private fun lista(): JVal.Arr {
        val itens = mutableListOf<JVal>()
        espacos()
        if (i < texto.length && texto[i] == ']') {
            i++
            return JVal.Arr(itens)
        }
        while (true) {
            espacos()
            itens.add(valor())
            espacos()
            when (proximo()) {
                ',' -> continue
                ']' -> break
                else -> throw ErroJson("Esperava ',' ou ']'", i - 1)
            }
        }
        return JVal.Arr(itens)
    }

    private fun textoLiteral(): String {
        val sb = StringBuilder()
        while (true) {
            if (i >= texto.length) throw ErroJson("Texto sem fechamento", i)
            val c = texto[i++]
            if (c == '"') break
            if (c != '\\') {
                sb.append(c)
                continue
            }
            if (i >= texto.length) throw ErroJson("Escape sem fechamento", i)
            when (val e = texto[i++]) {
                '"', '\\', '/' -> sb.append(e)
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (i + 4 > texto.length) throw ErroJson("Escape \\u incompleto", i)
                    val codigo = texto.substring(i, i + 4).toIntOrNull(16)
                        ?: throw ErroJson("Escape \\u inválido", i)
                    sb.append(codigo.toChar())
                    i += 4
                }
                else -> throw ErroJson("Escape inválido: '\\$e'", i - 1)
            }
        }
        return sb.toString()
    }

    private fun numero(): JVal.Num {
        val inicio = i - 1
        while (i < texto.length && (texto[i].isDigit() || texto[i] in "+-.eE")) i++
        val bruto = texto.substring(inicio, i)
        return JVal.Num(bruto.toDoubleOrNull() ?: throw ErroJson("Número inválido: $bruto", inicio))
    }
}

private fun JVal.Obj.campo(chave: String, contexto: String): JVal =
    v[chave] ?: throw IllegalArgumentException("Chave ausente '$chave' em $contexto")

private fun JVal.Obj.texto(chave: String, contexto: String): String =
    when (val valor = campo(chave, contexto)) {
        is JVal.Str -> valor.v
        is JVal.Num -> if (valor.v % 1.0 == 0.0) valor.v.toLong().toString() else valor.v.toString()
        else -> throw IllegalArgumentException("Chave '$chave' não é texto em $contexto")
    }

private fun JVal.Obj.textoOuNulo(chave: String, contexto: String): String? =
    when (val valor = v[chave]) {
        null, JVal.Nulo -> null
        is JVal.Str -> valor.v
        is JVal.Num -> if (valor.v % 1.0 == 0.0) valor.v.toLong().toString() else valor.v.toString()
        else -> throw IllegalArgumentException("Chave '$chave' não é texto em $contexto")
    }

private fun JVal.Obj.inteiro(chave: String, contexto: String): Int =
    when (val valor = campo(chave, contexto)) {
        is JVal.Num -> valor.v.toInt()
        else -> throw IllegalArgumentException("Chave '$chave' não é número em $contexto")
    }

private fun JVal.Obj.inteiroOuNulo(chave: String, contexto: String): Int? =
    when (val valor = v[chave]) {
        null, JVal.Nulo -> null
        is JVal.Num -> valor.v.toInt()
        else -> throw IllegalArgumentException("Chave '$chave' não é número em $contexto")
    }

private fun JVal.Obj.longo(chave: String, contexto: String): Long =
    when (val valor = campo(chave, contexto)) {
        is JVal.Num -> valor.v.toLong()
        else -> throw IllegalArgumentException("Chave '$chave' não é número em $contexto")
    }

private fun JVal.Obj.longoOuNulo(chave: String, contexto: String): Long? =
    when (val valor = v[chave]) {
        null, JVal.Nulo -> null
        is JVal.Num -> valor.v.toLong()
        else -> throw IllegalArgumentException("Chave '$chave' não é número em $contexto")
    }

private fun JVal.Obj.decimalOuNulo(chave: String): Double? =
    when (val valor = v[chave]) {
        null, JVal.Nulo -> null
        is JVal.Num -> valor.v
        else -> throw IllegalArgumentException("Chave '$chave' não é número")
    }

private fun JVal.Obj.booleano(chave: String, contexto: String): Boolean =
    when (val valor = campo(chave, contexto)) {
        is JVal.Bool -> valor.v
        else -> throw IllegalArgumentException("Chave '$chave' não é booleano em $contexto")
    }

private fun JVal.Obj.lista(chave: String, contexto: String): List<JVal> =
    when (val valor = campo(chave, contexto)) {
        is JVal.Arr -> valor.v
        else -> throw IllegalArgumentException("Chave '$chave' não é lista em $contexto")
    }

private fun JVal.Obj.objeto(chave: String, contexto: String): JVal.Obj =
    when (val valor = campo(chave, contexto)) {
        is JVal.Obj -> valor
        else -> throw IllegalArgumentException("Chave '$chave' não é objeto em $contexto")
    }

/** Lê o catálogo padronizado usando exatamente as chaves do JSON. */
fun parseCatalogoPadronizado(json: String): CatalogoPadronizado {
    val raiz = LeitorJson(json).ler()
    if (raiz !is JVal.Obj) throw IllegalArgumentException("Raiz do JSON não é objeto")
    val categorias = raiz.lista("categorias", "raiz").map { item ->
        if (item !is JVal.Obj) throw IllegalArgumentException("Categoria não é objeto")
        CategoriaPadronizada(id = item.inteiro("id", "categoria"), nome = item.texto("nome", "categoria"))
    }
    val produtos = raiz.lista("produtos", "raiz").map { item ->
        if (item !is JVal.Obj) throw IllegalArgumentException("Produto não é objeto")
        val nome = item.texto("nome", "produto")
        val tipo = item.texto("tipo", nome)
        if (tipo != "simples" && tipo != "variacoes") throw IllegalArgumentException("Tipo inválido '$tipo' em $nome")
        val variacoes = if (tipo == "variacoes") {
            item.lista("variacoes", nome).map { sub ->
                if (sub !is JVal.Obj) throw IllegalArgumentException("Variação não é objeto em $nome")
                val nomeVar = sub.texto("nome", "variação de $nome")
                VariacaoPadronizada(
                    id = sub.longo("id", nomeVar),
                    nome = nomeVar,
                    tamanho = sub.textoOuNulo("tamanho", nomeVar),
                    preco = sub.decimalOuNulo("preco")
                        ?: throw IllegalArgumentException("Variação sem preco: $nomeVar"),
                    precoCentavos = sub.longo("precoCentavos", nomeVar),
                    categoria = sub.texto("categoria", nomeVar),
                    categoriaId = sub.inteiro("categoriaId", nomeVar),
                    descricao = sub.texto("descricao", nomeVar),
                    estoque = sub.inteiroOuNulo("estoque", nomeVar),
                    ativo = sub.booleano("ativo", nomeVar),
                    multiplo = sub.inteiro("multiplo", nomeVar)
                )
            }
        } else {
            emptyList()
        }
        ProdutoPadronizado(
            id = item.texto("id", nome),
            nome = nome,
            categoria = item.texto("categoria", nome),
            categoriaId = item.inteiro("categoriaId", nome),
            preco = item.decimalOuNulo("preco"),
            precoCentavos = item.longoOuNulo("precoCentavos", nome),
            descricao = item.texto("descricao", nome),
            estoque = item.inteiroOuNulo("estoque", nome),
            ativo = item.booleano("ativo", nome),
            multiplo = item.inteiro("multiplo", nome),
            tipo = tipo,
            variacoes = variacoes
        )
    }
    return CatalogoPadronizado(
        totalCategorias = raiz.inteiro("totalCategorias", "raiz"),
        totalProdutos = raiz.inteiro("totalProdutos", "raiz"),
        totalVariacoes = raiz.inteiro("totalVariacoes", "raiz"),
        categorias = categorias,
        produtos = produtos
    )
}
