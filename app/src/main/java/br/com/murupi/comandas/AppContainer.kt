package br.com.murupi.comandas

import android.content.Context
import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.repo.CardapioRepository
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.ImpressoraRepository
import br.com.murupi.comandas.data.seed.CardapioSeed
import br.com.murupi.comandas.print.DescobertaImpressoras
import br.com.murupi.comandas.print.ServicoImpressao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Dependências compartilhadas do app (injeção manual, sem framework). */
class AppContainer(context: Context) {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase = AppDatabase.criar(context)
    val cardapio = CardapioRepository(database)
    val comandas = ComandaRepository(database)
    val impressoras = ImpressoraRepository(database.impressoraDao())
    val impressao = ServicoImpressao(database.impressoraDao(), database.itemComandaDao())
    val descoberta = DescobertaImpressoras(context.applicationContext)

    init {
        escopo.launch { CardapioSeed.popularSeVazio(database) }
    }
}
