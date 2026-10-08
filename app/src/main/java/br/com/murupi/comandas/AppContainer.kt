package br.com.murupi.comandas

import android.content.Context
import br.com.murupi.comandas.data.Restaurante
import br.com.murupi.comandas.data.db.AppDatabase
import br.com.murupi.comandas.data.model.ConfigRestaurante
import br.com.murupi.comandas.data.repo.CardapioRepository
import br.com.murupi.comandas.data.repo.ComandaRepository
import br.com.murupi.comandas.data.repo.ConfigRepository
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
    val config = ConfigRepository(database)

    init {
        escopo.launch {
            CardapioSeed.popularSeVazio(database, lerCatalogoPadronizado(context))
            // Configuração salva (ou os padrões) disponível para telas e tickets.
            Restaurante.aplicar(config.carregar() ?: ConfigRestaurante())
        }
    }

    private fun lerCatalogoPadronizado(context: Context): String = try {
        context.assets.open("catalogo_padronizado.json").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        // Asset ausente: o seed ignora e mantém o banco como está.
        "{}"
    }
}
