# Murupi Comandas

Aplicativo Android (PDV) para comandas do restaurante — mesas, balcão e delivery — com
cardápio padronizado em JSON, impressão térmica de produção e conta, pagamento em
Bottom Sheet e histórico de vendas. Feito por Fabio Silva.

## Funcionalidades

### Salão, balcão e delivery
- **Mesas**: grade do salão, várias comandas por mesa (`9.1`, `9.2`…), transferência entre
  mesas, junção de comandas, número de pessoas e valor por pessoa.
- **Balcão**: pedido avulso com nome do cliente opcional.
- **Delivery**: pede nome do cliente e endereço de entrega (obrigatórios).

### Catálogo e cardápio
- Cardápio inicial vem do arquivo padronizado `app/src/main/assets/catalogo_padronizado.json`
  (127 produtos, 390 variações): cada item é `simples` (preço único) ou `variacoes`
  (ex.: `Suco de Acerola` → `250ml/300ml/400ml/500ml/750ml/1L`; `Jabá` → `10/12/15`).
- Cadastro único e dinâmico: **Nome → Categoria → Tipo de preço** (`Preço único` ou
  `Preços por variação`) → campos correspondentes. Variação tem nome/tamanho livre e
  preço em R$ (o app calcula os centavos; nunca mostra centavos para o usuário).
- Cada variação é salva como um produto com o mesmo grupo, mantendo estoque individual.
- Controle de estoque por produto, produto oculto (`Ativo`), vendido de N em N
  (ex.: entradas de 5 em 5) e preço livre (digitado no lançamento).
- Categorias com cor, setor de produção e flags (sob demanda, perguntar opções do suco).

### Comanda
- Lançamento por categoria, com busca, grupos expansíveis ordenados do menor para o
  maior preço e rótulo curto da variação (`300ml`, `10`).
- Sucos de polpa perguntam **com leite / sem leite** (laranja e limão saem direto) e
  aceitam observação livre; a observação sai impressa em destaque (`OBS: …`).
- Dividir item em unidades, remover (com confirmação se já foi para a produção),
  espelho para conferência sem fechar a conta.
- Conta paga não reabre; conta fechada sem pagamento pode ser conferida e reaberta fora
  do histórico. O histórico é só consulta + impressão.

### Pagamento
- Bottom Sheet ao tocar em **Efetuar pagamento**: título da comanda, total em destaque,
  escolha da forma (Dinheiro começa selecionado, ou Débito, Crédito, PIX) e botões
  **Cancelar** / **Confirmar pagamento**.
- Comanda sem itens: confirma liberação da mesa (mesa) ou cancelamento (balcão/delivery).

### Impressão térmica (ESC/POS via rede, porta 9100)
- Uma impressora pode ter **vários papéis** (ex.: Cozinha + Sucos numa casa com
  1 impressora). Papéis: Espelho, Cozinha e Sucos.
- Roteamento por setor da categoria: Cozinha → cozinha; Sucos → sucos; Refrigerantes
  (bebidas) → cozinha, abaixo dos pratos. O setor é configurável por categoria.
- Produção sem preço (só o que ainda não foi enviado; reimpressão manda tudo com
  `*** REIMPRESSÃO ***`); espelho com preço, total e valor por pessoa.
- Prévia do ticket na tela antes de aprovar; sem impressora ativa para o papel, o item
  continua pendente e o app avisa. Larguras 80mm/58mm e remoção de acentos por impressora.
- Histórico de vendas sai com contas fechadas + saldo total na impressora escolhida.

### Histórico de vendas
- Cartão-resumo (total vendido, nº de vendas, ticket médio) e lista com origem, forma de
  pagamento, data/hora, itens e total. Toque abre para consulta; segurar exclui.

### Configurações
- Nome do restaurante, mensagem de agradecimento, total de mesas e taxa de serviço.

## Catálogo JSON (resumo da estrutura)

```jsonc
{ "id": 22, "nome": "Carne de Sol", "tipo": "simples",
  "categoria": "PRATOS MURUPI", "preco": 23.0, "precoCentavos": 2300, ... }
{ "id": "Acerola", "nome": "Suco de Acerola", "tipo": "variacoes", ...,
  "variacoes": [
    { "id": 57, "nome": "Suco de Acerola 250ml", "tamanho": "250ml",
      "preco": 5.0, "precoCentavos": 500, ... }
  ] }
```

Na primeira execução o banco é populado do JSON; depois, o sync completa produtos novos,
remove sopas obsoletas e corrige preços divergentes (estoque, disponibilidade e múltiplo
são preservados por serem estado operacional).

## Como compilar e instalar

Pré-requisitos: JDK 17, Android SDK com platform-tools.

```powershell
# APK debug (assinado com chave debug, instala direto no aparelho)
.\gradlew.bat :app:assembleDebug
# app\build\outputs\apk\debug\app-debug.apk

# Testes unitários
.\gradlew.bat :app:testDebugUnitTest

# Instalar via USB ou Wi-Fi
adb install -r app\build\outputs\apk\debug\app-debug.apk

# ADB via Wi-Fi (celular e PC na mesma rede)
adb tcpip 5555
adb connect <IP-DO-CELULAR>:5555
```

Para compartilhar com amigos, basta enviar o `app-debug.apk` (eles precisam permitir
"instalar apps desconhecidos"). Requer Android 8.0+.

## Estrutura do projeto

```
app/src/main/
  assets/catalogo_padronizado.json   # cardápio oficial (fonte do seed)
  java/br/com/murupi/comandas/
    data/
      model/   # Comanda, Produto, ItemComanda, Categoria, Impressora, enums
      db/      # Room (v9): DAOs, AppDatabase, Conversores, migrações
      repo/    # CardapioRepository, ComandaRepository, ImpressoraRepository, ConfigRepository
      seed/    # CardapioSeed + parser do JSON (CatalogoJson, sem dependências)
    ui/
      inicio/    # Mesas, Balcão, Delivery
      produto/   # IncluirProduto (venda), linhas/agrupamento por preço
      catalogo/  # Cadastro unificado (preço único x variações)
      comanda/   # Comanda + Bottom Sheet de pagamento
      historico/ # Resumo + vendas
      impressora/# Cadastro multi-papel, roteamento, busca na rede
      categoria/ # Gestão de categorias
      config/    # Configurações do restaurante
      common/    # BaseActivity, FormularioItem, insets, diálogos
    print/     # ServicoImpressao, Tickets ESC/POS, RoteadorImpressao
```

Tecnologias: Kotlin, Room, Coroutines/Flow, View Binding, Material 3, sem frameworks de DI
(injeção manual em `AppContainer`).
