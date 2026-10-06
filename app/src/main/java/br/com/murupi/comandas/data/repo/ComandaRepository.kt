package br.com.murupi.comandas.data.repo

import androidx.room.withTransaction
import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.Categoria
import br.com.murupi.comandas.data.model.Comanda
import br.com.murupi.comandas.data.model.ComandaResumo
import br.com.murupi.comandas.data.model.FormaPagamento
import br.com.murupi.comandas.data.model.ItemComanda
import br.com.murupi.comandas.data.model.Produto
import br.com.murupi.comandas.data.model.StatusComanda
import br.com.murupi.comandas.data.model.TipoComanda
import br.com.murupi.comandas.data.model.precoLivre
import kotlinx.coroutines.flow.Flow

/** Lançamento maior que o estoque do produto. */
class EstoqueInsuficienteException(val produto: String, val disponivel: Int) : Exception()

class ComandaRepository(private val db: AppDatabase) {

    private val comandaDao = db.comandaDao()
    private val itemDao = db.itemComandaDao()
    private val produtoDao = db.produtoDao()

    fun comandasAbertas(): Flow<List<ComandaResumo>> = comandaDao.observarAbertas()

    fun observarComanda(id: Long): Flow<Comanda?> = comandaDao.observar(id)

    fun observarItens(comandaId: Long): Flow<List<ItemComanda>> = itemDao.observarDaComanda(comandaId)

    suspend fun comanda(id: Long): Comanda? = comandaDao.buscar(id)

    suspend fun itens(comandaId: Long): List<ItemComanda> = itemDao.listarDaComanda(comandaId)

    suspend fun resumosAbertosDaMesa(mesa: Int): List<ComandaResumo> = comandaDao.resumosAbertosDaMesa(mesa)

    /** Últimas contas fechadas da mesa (vermelho no diálogo, só consulta). */
    suspend fun resumosFechadasDaMesa(mesa: Int, limite: Int = 5): List<ComandaResumo> =
        comandaDao.resumosFechadasDaMesa(mesa, limite)

    /** Histórico de vendas para a tela de histórico. */
    fun historico(): Flow<List<ComandaResumo>> = comandaDao.observarFechadas()

    fun resumosAbertosDoTipo(tipo: TipoComanda): Flow<List<ComandaResumo>> =
        comandaDao.observarAbertasDoTipo(tipo)

    /** Abre a comanda seguinte da mesa: 9.1, depois 9.2, reaproveitando números já pagos. */
    suspend fun abrirNovaComanda(mesa: Int): Comanda = db.withTransaction {
        val nova = Comanda(mesa = mesa, sequencia = proximaSequencia(TipoComanda.MESA, mesa))
        nova.copy(id = comandaDao.inserir(nova))
    }

    /** Abre comanda de balcão ou delivery (mesa 0), com nome do cliente e endereço. */
    suspend fun abrirComandaAvulsa(tipo: TipoComanda, nomeCliente: String, endereco: String): Comanda =
        db.withTransaction {
            val nova = Comanda(
                mesa = 0,
                sequencia = proximaSequencia(tipo, 0),
                tipo = tipo,
                nomeCliente = nomeCliente.trim(),
                endereco = endereco.trim()
            )
            nova.copy(id = comandaDao.inserir(nova))
        }

    private suspend fun proximaSequencia(tipo: TipoComanda, mesa: Int): Int {
        val emUso = if (tipo == TipoComanda.MESA) {
            comandaDao.abertasDaMesa(mesa).map { it.sequencia }.toSet()
        } else {
            comandaDao.abertasDoTipo(tipo, mesa).map { it.sequencia }.toSet()
        }
        return generateSequence(1) { it + 1 }.first { it !in emUso }
    }

    /**
     * Lança o produto na comanda e dá baixa no estoque. Se já existe o mesmo produto com a mesma
     * observação ainda não impresso, só soma a quantidade nele.
     * @throws EstoqueInsuficienteException se não houver quantidade disponível.
     */
    suspend fun adicionarItem(
        comandaId: Long,
        produto: Produto,
        categoria: Categoria,
        quantidade: Int,
        precoUnitarioCentavos: Long,
        observacao: String
    ): Unit = db.withTransaction {
        movimentarEstoque(produto.id, quantidade)
        val obs = observacao.trim()
        val pendente = itemDao.pendenteIgual(comandaId, produto.id, precoUnitarioCentavos, obs)
        if (pendente != null) {
            itemDao.atualizar(pendente.copy(quantidade = pendente.quantidade + quantidade))
        } else {
            itemDao.inserir(
                ItemComanda(
                    comandaId = comandaId,
                    produtoId = produto.id,
                    nome = produto.nome,
                    categoriaNome = categoria.nome,
                    setor = categoria.setor,
                    demanda = categoria.demanda,
                    precoLivre = produto.precoLivre,
                    quantidade = quantidade,
                    precoUnitarioCentavos = precoUnitarioCentavos,
                    observacao = obs,
                    multiplo = produto.multiplo
                )
            )
        }
    }

    /** Ajusta o estoque pela diferença de quantidade. */
    suspend fun atualizarItem(item: ItemComanda) = db.withTransaction {
        val anterior = itemDao.buscar(item.id) ?: return@withTransaction
        movimentarEstoque(item.produtoId, item.quantidade - anterior.quantidade)
        itemDao.atualizar(item)
    }

    /** Remove o item e devolve a quantidade ao estoque. */
    suspend fun excluirItem(item: ItemComanda) = db.withTransaction {
        movimentarEstoque(item.produtoId, -item.quantidade)
        itemDao.excluir(item)
    }

    /**
     * Divide um item de quantidade N em N itens de 1 unidade, para dar observações
     * diferentes a cada um (ex.: um para viagem, outro sem açúcar). O total e o
     * estoque não mudam; vale para qualquer produto.
     */
    suspend fun dividirItem(item: ItemComanda) = db.withTransaction {
        val atual = itemDao.buscar(item.id) ?: return@withTransaction
        if (atual.quantidade <= 1) return@withTransaction
        itemDao.atualizar(atual.copy(quantidade = 1))
        repeat(atual.quantidade - 1) {
            itemDao.inserir(atual.copy(id = 0, quantidade = 1, criadoEm = System.currentTimeMillis()))
        }
    }

    /** Positivo dá baixa, negativo devolve. Produtos sem controle de estoque são ignorados. */
    private suspend fun movimentarEstoque(produtoId: Long?, quantidade: Int) {
        if (produtoId == null || quantidade == 0) return
        val produto = produtoDao.buscar(produtoId) ?: return
        val estoque = produto.estoque ?: return
        if (quantidade > estoque) throw EstoqueInsuficienteException(produto.nome, estoque)
        produtoDao.atualizar(produto.copy(estoque = estoque - quantidade))
    }

    suspend fun definirPessoas(comandaId: Long, pessoas: Int) {
        val comanda = comandaDao.buscar(comandaId) ?: return
        comandaDao.atualizar(comanda.copy(pessoas = pessoas))
    }

    /**
     * Leva a comanda para [mesaDestino]. Com [juntarComId] os itens vão para aquela comanda e a
     * de origem deixa de existir; sem ele, a comanda ganha o próximo número livre da mesa.
     * Retorna o id da comanda que ficou com os itens.
     */
    suspend fun transferir(comandaId: Long, mesaDestino: Int, juntarComId: Long?): Long = db.withTransaction {
        val origem = checkNotNull(comandaDao.buscar(comandaId)) { "Comanda $comandaId não existe" }
        if (juntarComId != null) {
            val destino = checkNotNull(comandaDao.buscar(juntarComId)) { "Comanda $juntarComId não existe" }
            itemDao.moverItens(origem.id, destino.id)
            comandaDao.atualizar(destino.copy(pessoas = destino.pessoas + origem.pessoas))
            comandaDao.excluir(origem)
            destino.id
        } else {
            comandaDao.atualizar(origem.copy(mesa = mesaDestino, sequencia = proximaSequencia(TipoComanda.MESA, mesaDestino)))
            origem.id
        }
    }

    /** Registra o pagamento e fecha a comanda; a mesa fica livre quando não houver outra aberta. */
    suspend fun fecharComanda(comandaId: Long, forma: FormaPagamento, totalCentavos: Long) {
        val comanda = comandaDao.buscar(comandaId) ?: return
        comandaDao.atualizar(
            comanda.copy(
                status = StatusComanda.FECHADA,
                fechadaEm = System.currentTimeMillis(),
                formaPagamento = forma,
                totalPagoCentavos = totalCentavos
            )
        )
    }

    suspend fun excluirComanda(comandaId: Long) {
        comandaDao.buscar(comandaId)?.let { comandaDao.excluir(it) }
    }

    /**
     * Reabre uma conta fechada para lançar mais itens. A produção imprime só os
     * itens novos (pendentes) e o próximo espelho sai completo e atualizado.
     */
    suspend fun reabrirComanda(comandaId: Long) = db.withTransaction {
        val comanda = comandaDao.buscar(comandaId) ?: return@withTransaction
        if (comanda.status != StatusComanda.FECHADA) return@withTransaction
        comandaDao.atualizar(
            comanda.copy(
                status = StatusComanda.ABERTA,
                fechadaEm = null,
                formaPagamento = null,
                totalPagoCentavos = null
            )
        )
    }

    /**
     * Fecha a conta após imprimir o espelho com sucesso e libera a mesa
     * (outras comandas da mesa continuam ativas). Sem forma de pagamento:
     * a conta impressa vale como fechamento.
     */
    suspend fun fecharContaImpressa(comandaId: Long) = db.withTransaction {
        val comanda = comandaDao.buscar(comandaId) ?: return@withTransaction
        if (comanda.status != StatusComanda.ABERTA) return@withTransaction
        val total = itemDao.listarDaComanda(comandaId).sumOf { it.quantidade * it.precoUnitarioCentavos }
        comandaDao.atualizar(
            comanda.copy(
                status = StatusComanda.FECHADA,
                fechadaEm = System.currentTimeMillis(),
                totalPagoCentavos = total
            )
        )
    }

    suspend fun limparComandasVazias(): Int = comandaDao.excluirAbertasVazias()
}
