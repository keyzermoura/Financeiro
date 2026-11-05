# Carteira Invest

Aplicativo Android nativo para acompanhar uma carteira de investimentos, com foco em ações, FIIs, ETFs, criptomoedas e renda fixa. Todo o controle financeiro é local: não existe backend próprio, conta em nuvem ou sincronização entre dispositivos.

O app reúne cotações da B3 (Brapi), criptomoedas (CoinGecko) e câmbio (AwesomeAPI), mantendo os dados em cache no Room para funcionar offline. Os valores monetários usam `BigDecimal` do início ao fim e só são formatados no momento de exibição.

---

## Recursos

- **Conta local** com cadastro, login, logout, bloqueio por biometria e sessão criptografada.
- **Múltiplas carteiras** com seleção da carteira ativa em todo o aplicativo.
- **Ativos** dos tipos ação, FII, ETF, criptomoeda e renda fixa, com criação, edição e exclusão.
- **Lançamentos** de compra, venda e provento, com edição e exclusão.
- **Preço médio ponderado**, resultado realizado, resultado não realizado, proventos recebidos e variação diária.
- **Dashboard** com patrimônio, investido, ganhos, alocação por categoria (gráfico de pizza) e maiores posições.
- **Evolução patrimonial** por snapshots, com gráfico de linha e comparação com a BOVA11 como proxy do Ibovespa.
- **Proventos** com total do período, fluxo mensal, filtro por ano e histórico de lançamentos.
- **Alertas locais** de variação de preço e de vencimento de renda fixa, com notificações.
- **Simulação de investimento** com juros compostos mensais equivalentes à taxa anual informada.
- **Cotações de câmbio** USD/BRL e EUR/BRL.
- **Relatório em PDF** compartilhável com resumo, alocação, posições, proventos e aviso de responsabilidade.
- **Offline-first**: cotações, taxas e dados do usuário são lidos do Room; a rede apenas atualiza o cache.
- **Atualização em segundo plano** a cada 30 minutos com WorkManager, além de pull-to-refresh.

---

## Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Arquitetura | MVVM + Clean Architecture |
| Injeção de dependência | Hilt 2.52 |
| Persistência | Room 2.6.1 (KSP) |
| Rede | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson |
| Concorrência | Coroutines + Flow |
| Background | WorkManager 2.10.0 |
| Gráficos | MPAndroidChart 3.1.0 via `AndroidView` |
| Segurança | EncryptedSharedPreferences + BiometricPrompt + PBKDF2 |
| PDF | `android.graphics.pdf.PdfDocument` + FileProvider |
| Build | AGP 8.7.3, Java 17, `minSdk 24`, `targetSdk 35` |

O projeto usa **core library desugaring** para disponibilizar `java.time` em dispositivos a partir da API 24.

---

## Arquitetura

```text
app/src/main/java/br/com/carteirainvest/
├── core/                  # rede, segurança, formatos e matemática financeira
├── data/
│   ├── local/             # Room: entidades, DAOs, banco e mappers
│   ├── remote/            # APIs Retrofit, DTOs e interceptor HTTP
│   ├── repository/        # implementações dos contratos de domínio
│   ├── worker/            # sincronização periódica e agendador
│   ├── notification/      # canais e envio de notificações
│   └── report/            # geração do PDF
├── domain/
│   ├── model/             # modelos de negócio
│   ├── repository/        # contratos
│   └── usecase/           # regras de negócio e cálculos
├── di/                    # módulos Hilt
└── presentation/          # Compose, ViewModels, navegação e tema
```

Decisões principais:

- O **Room é a fonte de verdade** da interface. As telas observam `Flow`s do banco; uma atualização de rede grava no cache e o Compose reage automaticamente.
- Cada tela possui um `ViewModel` com `StateFlow`, e as regras ficam em `UseCase`s independentes de Android quando possível.
- Valores monetários e percentuais permanecem em `BigDecimal`; `Double` é usado apenas dentro de exponenciais (`Math.pow`) para juros compostos.
- A navegação usa rotas em string e uma barra inferior apenas nas abas principais: **Início**, **Ativos**, **Evolução**, **Proventos** e **Mais**.
- O WorkManager é inicializado manualmente em `InvestApplication` para permitir a injeção de dependências nos workers com Hilt.

---

## APIs e cache

### Endpoints

| Fonte | Uso | Endpoint |
| --- | --- | --- |
| Brapi | Cotações da B3, histórico e proventos anunciados | `GET https://brapi.dev/api/quote/{tickers}` |
| CoinGecko | Preços de criptomoedas em BRL | `GET https://api.coingecko.com/api/v3/simple/price` |
| AwesomeAPI | Câmbio USD/BRL e EUR/BRL | `GET https://economia.awesomeapi.com.br/json/last/USD-BRL,EUR-BRL` |

A Brapi aceita vários tickers separados por vírgula. A CoinGecko recebe os IDs das moedas (por exemplo, `bitcoin,ethereum`), não os símbolos. O app possui um catálogo inicial dos pares mais comuns e permite informar o ID manualmente ao cadastrar uma criptomoeda.

### Tempos de cache

| Dado | TTL |
| --- | --- |
| Ações, FIIs e ETFs | 15 minutos |
| Criptomoedas | 5 minutos |
| Câmbio | 10 minutos |
| Proventos anunciados | 6 horas |

### Tempos de cache

| Dado | TTL |
| --- | --- |
| Ações, FIIs e ETFs | 5 minutos |
| Criptomoedas | 60 segundos |
| Câmbio | 1 hora |
| Proventos anunciados | 6 horas |

Fora do prazo, a próxima leitura tenta atualizar pela rede. Se a chamada falhar e existir cache anterior, o app continua exibindo os últimos valores com o indicador **stale = true**, para que a UI possa sinalizar ao usuário que os dados podem estar desatualizados. Renda fixa não possui cotação de mercado: seu valor é estimado pela taxa anual cadastrada.

### Camada de dados (offline-first)

O app segue o padrão **Repository** com **Room como fonte de verdade**. A UI nunca consome a rede diretamente; ela apenas observa `Flow`s vindos do Room.

#### Fluxo network-first com cache

1. A UI solicita dados via `Flow` (ex.: `observeQuotes()`).
2. Room emprime o valor cacheado imediatamente (se existir).
3. Se o TTL expirou, o repositório dispara uma atualização remota via Retrofit.
4. A resposta remota é persistida no Room.
5. O novo valor é emitido via `Flow`, atualizando a UI automaticamente.

#### Staleness

Quando a rede falha e existe cache anterior, o repositório retorna o valor cacheado com `isStale = true`. A ViewModel pode então exibir uma mensagem como "Sem conexão. Exibindo cotações salvas." para o usuário nunca confundir dados antigos com cotações ao vivo.

#### Mutex / single-flight

- **Brapi:** plano gratuito permite 1 requisição simultânea. Todas as chamadas Brapi (cotações, proventos, benchmark) passam por um `Mutex` dedicado (`brapiGate`), serializando o acesso.
- **CoinGecko:** rate limit compartilhado por IP. Chamadas também são serializadas (`coinGeckoGate`) para evitar estouro de 429.
- **Por símbolo:** cada ticker tem seu próprio `Mutex` (`symbolLocks`). Chamadas concorrentes para o mesmo ativo são serializadas e a segunda revalida o cache após a primeira concluir, evitando requisições duplicadas.

#### Retry e backoff

Falhas de rede, timeouts, HTTP 5xx e HTTP 429 são retryables. A política é:
- **HTTP 429 com `Retry-After`:** aguarda o tempo indicado pelo cabeçalho antes de tentar novamente.
- **HTTP 429 sem `Retry-After`:** backoff exponencial (100ms, 200ms, 400ms… limitado a 8s) com 20% de jitter.
- **HTTP 5xx / timeout / sem conexão:** backoff exponencial com jitter.
- **HTTP 4xx (exceto 429) / parsing:** falha imediatamente, sem retry.
- Máximo de 3 tentativas por chamada.

#### WorkManager

Três Workers independentes, um por tipo de dado:

| Worker | Dados | Intervalo padrão | Constraint de rede |
| --- | --- | --- | --- |
| `B3SyncWorker` | Ações, FIIs, ETFs | 15 minutos | `CONNECTED` |
| `CryptoSyncWorker` | Criptomoedas | 15 minutos | `CONNECTED` |
| `FxSyncWorker` | Câmbio | 1 hora | `CONNECTED` |

Cada Worker usa `ExistingPeriodicWorkPolicy.REPLACE` (atualiza o agendamento se mudar o intervalo), backoff exponencial e chama os mesmos métodos de refresh dos repositórios. Notificações de falha são mostradas apenas após 2 tentativas consecutivas com falha; sucesso rotineiro não gera notificação.

#### Notificações de falha de sync

Apenas falhas persistentes geram notificação (canal `quote_sync_failures`, importância baixa). Respeita `POST_NOTIFICATIONS` no Android 13+. Sucesso rotineiro é silencioso.

---

### Chaves de API (opcionais)

O Brapi e a CoinGecko possuem camadas gratuitas, mas limites maiores exigem credenciais. Crie/edite o arquivo `local.properties` na raiz do projeto:

```properties
sdk.dir=C\:\\Users\\SeuUsuario\\AppData\\Local\\Android\\Sdk
BRAPI_TOKEN=seu_token_brapi
COINGECKO_API_KEY=sua_chave_demo_coingecko
```

Também é possível usar as variáveis de ambiente `BRAPI_TOKEN` e `COINGECKO_API_KEY`. O `app/build.gradle.kts` lê esses valores para `BuildConfig`. O arquivo `local.properties` é ignorado pelo Git e não deve ser versionado. Um modelo está em `local.properties.example`.

> **Atenção:** qualquer chave embutida em um APK pode ser extraída. Para produção, use chaves restritas por aplicativo/pacote e, idealmente, faça proxy das chamadas em um backend próprio. Nunca coloque segredos realmente sensíveis no app.

---

## Segurança

- **Senha local:** PBKDF2 com 120.000 iterações, salt aleatório de 128 bits e chave de 256 bits. O app prefere `PBKDF2WithHmacSHA256` e usa `PBKDF2WithHmacSHA1` apenas em APIs antigas que não oferecem SHA-256. A verificação usa comparação em tempo constante.
- **Sessão:** usuário, nome, e-mail e preferências ficam em `EncryptedSharedPreferences`, com chaves AES-256-SIV e valores AES-256-GCM protegidos pelo Android Keystore. Se a entrada do Keystore for invalidada, o arquivo é descartado e recriado, sem fallback para texto puro.
- **Biometria:** `BiometricPrompt` com autenticadores forte ou fraco. Ao ativar a biometria, o app fica bloqueado na próxima abertura do processo. Em caso de falha ou indisponibilidade, o usuário pode sair e entrar novamente com a senha.
- **Rede:** somente HTTPS; `usesCleartextTraffic="false"` e network security config bloqueiam HTTP. Logs HTTP detalhados só existem em debug e o token da Brapi é mascarado.
- **Backups:** `allowBackup="false"` e regras de extração excluem todos os domínios de backup em nuvem e transferência entre dispositivos.
- **Compartilhamento:** o PDF é gravado no cache privado e exposto somente via `FileProvider`, com permissão temporária.

### Limitações de segurança

- O banco Room não é criptografado separadamente com SQLCipher. Ele fica no sandbox privado do aplicativo e depende da criptografia do dispositivo.
- A biometria funciona como bloqueio de acesso ao app; ela não deriva uma chave criptográfica própria para o banco.
- A conta é local. Não há recuperação de senha, sincronização, backup em nuvem ou uso em vários aparelhos.
- O relatório em PDF é um retrato dos dados e deve ser guardado pelo usuário se ele quiser uma cópia externa.

---

## Regras de negócio

### Posição e resultado

- **Compra:** soma quantidade e custo (`quantidade × preço + taxas`) ao preço médio ponderado.
- **Venda:** remove o custo pela média atual, calcula o resultado realizado com o valor líquido de taxas e mantém o preço médio das unidades restantes. A quantidade vendida é limitada à posição existente.
- **Provento:** não altera quantidade nem preço médio; é acumulado separadamente.
- **Resultado total:** resultado não realizado + resultado realizado + proventos.
- **Variação diária:** `(preço atual − fechamento anterior) × quantidade`; usa zero quando não há cotação válida.

### Renda fixa

Sem fonte pública de preços, o valor é estimado a partir da primeira compra:

```text
valor estimado = preço médio × (1 + taxa anual)^(dias corridos / 365)
```

A taxa anual e a data de vencimento são informadas pelo usuário. O valor é uma estimativa e não substitui o extrato da corretora nem considera impostos, taxas ou eventos do título.

### Proventos

- Os proventos anunciados pela Brapi aparecem nos detalhes de ações e FIIs.
- Eles **não são somados automaticamente** ao patrimônio, pois o investidor pode não ter recebido ou o valor pode variar.
- Para entrar nos totais, o provento deve ser lançado manualmente em **Proventos**/lançamentos do ativo.

### Alertas

- **Variação de preço:** percentual diário entre 0,1% e 50%, sem duplicidade por ativo. Cooldown de 12 horas após disparar.
- **Vencimento:** renda fixa com data cadastrada, aviso de 1 a 365 dias antes. Cooldown de 7 dias.
- As notificações são locais. No Android 13+ o app solicita `POST_NOTIFICATIONS` na tela de alertas.
- O worker avalia alertas mesmo sem rede, usando o último cache disponível; alertas de vencimento funcionam offline.

### Simulação

- Taxa mensal equivalente: `(1 + taxa anual)^(1/12) − 1`.
- O aporte mensal é somado ao final de cada mês, depois da rentabilidade do período.
- Prazo de 1 a 600 meses e taxa anual de 0% a 100%.
- A simulação não inclui inflação, impostos, taxas de corretagem ou oscilações reais do mercado.

### Evolução e benchmark

- Um snapshot do patrimônio é gravado quando as cotações são atualizadas e também uma vez por dia pelo worker.
- O gráfico de evolução exige pelo menos dois snapshots em datas diferentes.
- A comparação usa **BOVA11** como proxy do Ibovespa, não o índice oficial.
- Períodos disponíveis: 1 mês, 3 meses, 6 meses, 1 ano e tudo.

---

## Como compilar

### Requisitos

- JDK 17
- Android SDK 35 (compile/target) e Build Tools compatíveis
- Android Studio recente ou Gradle Wrapper (já incluído)

### Configuração

1. Crie `local.properties` na raiz com o caminho do SDK:

   ```properties
   sdk.dir=C\:\\Users\\SeuUsuario\\AppData\\Local\\Android\\Sdk
   ```

2. Opcionalmente, adicione as chaves de API mostradas acima.
3. Confirme que `JAVA_HOME` aponta para um JDK 17.

### Comandos

Windows (PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Linux/macOS:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

O APK de debug é gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

O variant de debug usa o sufixo de aplicação `.debug` (`br.com.carteirainvest.debug`), portanto pode conviver com uma versão de produção no mesmo aparelho.

Instalação via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build de release

```bash
./gradlew :app:assembleRelease
```

O release usa R8 (`isMinifyEnabled = true`) e remoção de recursos. Antes de distribuir, configure um `signingConfig` próprio no `app/build.gradle.kts`; o projeto não inclui keystore nem credenciais de assinatura. Sem assinatura configurada, o artefato é gerado como:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## Testes

Os testes JVM ficam em `app/src/test/java/br/com/carteirainvest` e cobrem as regras financeiras mais sensíveis:

```bash
./gradlew :app:testDebugUnitTest
```

- `PortfolioCalculatorTest`: preço médio com taxas, venda parcial/total, limite de venda, proventos, cotação e variação diária, renda fixa, filtragem/ordenação e totais/alocação do resumo.
- `SimulateInvestmentUseCaseTest`: juros compostos, aportes, prazos, taxas e validações.
- `FinancialMathTest`: percentuais, composição anual, conversão de taxa, arredondamento e divisão por zero.

O projeto também passa por `:app:lintDebug` sem erros. Os avisos restantes são sugestões de versões mais novas de dependências.

---

### Adicionar um novo provedor de cotações

A camada de dados foi projetada para ser extensível sem alterar o domínio. Para adicionar um novo provedor:

1. **Crie o pacote** `data/remote/<novo-provedor>/` com:
   - `<Api>Api.kt` — interface Retrofit com os endpoints.
   - `<Api>Dtos.kt` — DTOs da resposta raw.
   - `<Api>Mappers.kt` — conversão DTO → entidade Room.

2. **Adicione o módulo no `NetworkModule.kt`** com `provide<Api>Api()` usando a `baseUrl` do provedor.

3. **Implemente ou estenda o repositório** seguindo o padrão network-first: ler do Room, verificar TTL, chamar a rede, persistir, emitir via `Flow`. Use `apiCallExecutor` para retry/backoff e `Mutex` para serialização quando o provedor tiver limite de concorrência.

4. **Crie um Worker** em `data/worker/` se a atualização periódica for necessária, registrando-o no `SyncScheduler`.

5. **Adicione testes** com MockWebServer simulando respostas de sucesso, erro e HTTP 429.

O domínio (`domain/model/`, `domain/repository/`) não precisa ser alterado, pois trabalha com modelos limpos e `Resource<T>`.

---

## Limitações conhecidas

- As APIs públicas têm limites de requisição, instabilidade e sem garantia de SLA.
- Renda fixa é estimada; não há integração com a B3, corretoras ou notas de corretagem.
- Não há importação automática de negócios nem conciliação de proventos.
- A conta e os dados são locais; não há nuvem, multiusuário ou recuperação de senha.
- O Room não é criptografado com SQLCipher; a proteção depende do sandbox e da criptografia do aparelho.
- O benchmark é uma aproximação via BOVA11.
- Os valores exibidos são informativos e não constituem recomendação de investimento.

---

## Aviso

Este aplicativo é uma ferramenta de organização e acompanhamento. Cotações podem atrasar ou falhar, e os cálculos de renda fixa, simulação e evolução são estimativas. Confira sempre os dados na corretora e consulte um profissional antes de tomar decisões de investimento.
