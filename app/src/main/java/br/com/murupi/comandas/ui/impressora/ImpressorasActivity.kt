package br.com.murupi.comandas.ui.impressora

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.murupi.comandas.R
import br.com.murupi.comandas.data.model.Impressora
import br.com.murupi.comandas.data.model.PapelImpressora
import br.com.murupi.comandas.databinding.ActivityImpressorasBinding
import br.com.murupi.comandas.databinding.DialogDescobertaBinding
import br.com.murupi.comandas.databinding.DialogImpressoraBinding
import br.com.murupi.comandas.print.DescobertaImpressoras.Evento
import br.com.murupi.comandas.ui.common.BaseActivity
import br.com.murupi.comandas.ui.common.aoConfirmar
import br.com.murupi.comandas.ui.common.aplicarInsets
import br.com.murupi.comandas.ui.common.coletar
import br.com.murupi.comandas.ui.common.viewModelsDoApp
import br.com.murupi.comandas.util.Rede
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cadastro das impressoras térmicas Wi-Fi e do papel de cada uma. */
class ImpressorasActivity : BaseActivity() {

    private lateinit var binding: ActivityImpressorasBinding
    private val viewModel by viewModelsDoApp { ImpressorasViewModel(it.impressoras, it.impressao, it.descoberta) }
    private val adapter = ImpressoraAdapter(aoTocar = { editar(it) }, aoTestar = ::testar)

    override val ancoraSnackbar: View get() = binding.barraAcoes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImpressorasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarra(binding.barra, voltar = true)
        binding.barraAcoes.aplicarInsets(base = true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnAdicionar.setOnClickListener { editar(null) }
        binding.btnBuscar.setOnClickListener { buscarNaRede() }

        coletar {
            launch {
                viewModel.impressoras.collect { lista ->
                    adapter.submitList(lista)
                    binding.textVazio.isVisible = lista.isEmpty()
                }
            }
            launch {
                viewModel.papeisFaltando.collect { faltando ->
                    binding.textAviso.isVisible = faltando.isNotEmpty()
                    binding.textAviso.text =
                        getString(R.string.papeis_faltando, faltando.joinToString { it.descricao })
                }
            }
            launch { viewModel.eventos.collect { avisar(it) } }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_impressoras, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_roteamento -> {
            startActivity(Intent(this, RoteamentoActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun testar(impressora: Impressora) {
        avisar(R.string.enviando_teste, impressora.nome)
        viewModel.testar(impressora)
    }

    /** Cadastro manual ou edição. [ipSugerido] vem da busca na rede. */
    private fun editar(impressora: Impressora?, ipSugerido: String? = null) {
        val b = DialogImpressoraBinding.inflate(layoutInflater)
        val papelInicial = impressora?.papel ?: sugerirPapel()
        b.editNome.setText(impressora?.nome)
        b.editIp.setText(impressora?.ip ?: ipSugerido)
        b.editPorta.setText((impressora?.porta ?: Impressora.PORTA_PADRAO).toString())
        b.grupoPapel.check(radioDoPapel(papelInicial))
        b.grupoLargura.check(if (impressora?.colunas == Impressora.COLUNAS_58MM) R.id.radio58 else R.id.radio80)
        b.checkSemAcentos.isChecked = impressora?.removerAcentos ?: true
        b.switchAtiva.isChecked = impressora?.ativa ?: true
        b.switchAtiva.isVisible = impressora != null

        val construtor = MaterialAlertDialogBuilder(this)
            .setTitle(if (impressora == null) R.string.nova_impressora else R.string.editar_impressora)
            .setView(b.root)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
        if (impressora != null) construtor.setNeutralButton(R.string.excluir) { _, _ -> confirmarExclusao(impressora) }
        val dialogo = construtor.create()

        dialogo.aoConfirmar {
            val ip = b.editIp.text?.toString().orEmpty().trim()
            if (!Rede.ipValido(ip)) {
                b.layoutIp.error = getString(R.string.ip_invalido)
                return@aoConfirmar false
            }
            val porta = b.editPorta.text?.toString()?.toIntOrNull()?.takeIf { it in 1..65535 }
            if (porta == null) {
                b.layoutPorta.error = getString(R.string.porta_invalida)
                return@aoConfirmar false
            }
            val papel = papelDoRadio(b.grupoPapel.checkedRadioButtonId)
            val nome = b.editNome.text?.toString().orEmpty().trim().ifEmpty { papel.descricao }
            val colunas = if (b.grupoLargura.checkedRadioButtonId == R.id.radio58) {
                Impressora.COLUNAS_58MM
            } else {
                Impressora.COLUNAS_80MM
            }
            val base = impressora ?: Impressora(nome = nome, ip = ip, papel = papel)
            viewModel.salvar(
                base.copy(
                    nome = nome,
                    ip = ip,
                    porta = porta,
                    papel = papel,
                    colunas = colunas,
                    removerAcentos = b.checkSemAcentos.isChecked,
                    ativa = b.switchAtiva.isChecked
                )
            )
            true
        }
        dialogo.show()
    }

    /** Para uma impressora nova, sugere o primeiro papel que ainda não tem impressora. */
    private fun sugerirPapel(): PapelImpressora {
        val usados = viewModel.impressoras.value.map { it.papel }.toSet()
        return PapelImpressora.entries.firstOrNull { it !in usados } ?: PapelImpressora.COZINHA
    }

    private fun radioDoPapel(papel: PapelImpressora): Int = when (papel) {
        PapelImpressora.ESPELHO -> R.id.radioEspelho
        PapelImpressora.COZINHA -> R.id.radioCozinha
        PapelImpressora.SUCOS -> R.id.radioSucos
    }

    private fun papelDoRadio(id: Int): PapelImpressora = when (id) {
        R.id.radioEspelho -> PapelImpressora.ESPELHO
        R.id.radioSucos -> PapelImpressora.SUCOS
        else -> PapelImpressora.COZINHA
    }

    private fun confirmarExclusao(impressora: Impressora) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.excluir_impressora)
            .setMessage(getString(R.string.excluir_impressora_pergunta, impressora.nome))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir(impressora) }
            .show()
    }

    /** Varre a rede Wi-Fi atrás da porta 9100 e mostra os IPs encontrados para cadastrar. */
    private fun buscarNaRede() {
        val b = DialogDescobertaBinding.inflate(layoutInflater)
        val encontrados = mutableListOf<String>()
        val lista = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        b.lista.adapter = lista
        var varredura: Job? = null

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.buscar_impressoras)
            .setView(b.root)
            .setNegativeButton(R.string.fechar, null)
            .setOnDismissListener { varredura?.cancel() }
            .create()
        b.lista.setOnItemClickListener { _, _, posicao, _ ->
            dialogo.dismiss()
            editar(null, encontrados[posicao])
        }

        val cadastradas = viewModel.impressoras.value
        varredura = lifecycleScope.launch {
            viewModel.varrerRede().collect { evento ->
                when (evento) {
                    is Evento.SemRede -> {
                        b.textStatus.setText(R.string.sem_rede)
                        b.progresso.isVisible = false
                    }
                    is Evento.Iniciada -> b.textStatus.text = getString(R.string.procurando_em, evento.subRede)
                    is Evento.Progresso -> b.progresso.setProgressCompat(evento.verificados * 100 / evento.total, true)
                    is Evento.Encontrada -> {
                        encontrados += evento.ip
                        val existente = cadastradas.firstOrNull { it.ip == evento.ip }
                        lista.add(
                            if (existente == null) evento.ip
                            else getString(R.string.ip_ja_cadastrado, evento.ip, existente.nome)
                        )
                        b.textDica.isVisible = true
                    }
                }
            }
            if (b.progresso.isVisible) {
                b.progresso.isVisible = false
                b.textStatus.text = if (encontrados.isEmpty()) {
                    getString(R.string.nenhuma_encontrada)
                } else {
                    resources.getQuantityString(R.plurals.impressoras_encontradas, encontrados.size, encontrados.size)
                }
            }
        }
        dialogo.show()
    }
}
