# Changelog

Todas as mudanças notáveis do projeto são registradas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/)
(`MAJOR.MINOR.PATCH`) a partir da v1.2.0:

- **MAJOR** — mudança incompatível (ex.: exige reconfigurar apps/Spaces)
- **MINOR** — funcionalidade nova, compatível com o que já existia
- **PATCH** — correção de bug ou de performance, sem funcionalidade nova

## [1.6.1] - 2026-09-25

Continuação da auditoria: P2 (recomposição excessiva) e P8 (itens
menores de performance/limpeza).

### Corrigido
- **Recomposição excessiva (P2):** sem Kotlin 2.0 (que traz "strong
  skipping" por padrão), o compilador do Compose trata qualquer
  `List`/`Map`/`Set` puro do Kotlin como instável — qualquer composable
  que recebe esses tipos como parâmetro nunca pula recomposição, mesmo
  que o conteúdo não tenha mudado. Cada notificação postada recompunha
  Home, Gaveta e Widgets inteiras. Convertidos os StateFlow expostos
  pelo ViewModel (`filteredApps`, `spaces`, `currentSpaceApps`,
  `assignments`, `appsWithNotifications`, `widgetPlacements`) e as
  assinaturas dos composables que os recebem para
  `PersistentList`/`PersistentSet`/`PersistentMap`
  (`kotlinx.collections.immutable`) — reconhecidos como estáveis pelo
  compilador mesmo sem strong skipping, e mantendo os operadores
  `+`/`-`/`put` que o código já usava. Optamos por essa rota em vez de
  migrar o projeto inteiro para Kotlin 2.0, mais arriscado sem
  conseguir compilar localmente para validar.
- `collectAsState()` trocado por `collectAsStateWithLifecycle()` em
  todas as coletas de `StateFlow` no `MainActivity` — a coleta (e a
  recomposição que ela pode disparar) agora pausa sozinha quando a
  Activity não está em primeiro plano. A dependência já estava no
  projeto (`lifecycle-runtime-compose`), só não era usada.
- **Relógio (#9, ainda pendente da auditoria original + P8):** o loop
  de `delay()` foi trocado por um `BroadcastReceiver` ouvindo
  `ACTION_TIME_TICK`/`ACTION_TIME_CHANGED`/`ACTION_TIMEZONE_CHANGED`,
  registrado só enquanto a Activity está em primeiro plano
  (ON_START/ON_STOP). Corrige o atraso de até 1 minuto que podia
  acontecer ao acordar a tela (`delay()` usa tempo de execução, que não
  avança durante o sono profundo) e o loop rodando à toa em segundo
  plano. Também passou a respeitar a preferência de 12h/24h do sistema
  em vez de fixar "HH:mm".
- (P8) `getSpaces()` era chamado duas vezes no construtor do
  `LauncherViewModel`, lendo e fazendo parsing das prefs em dobro à toa.
- (P8) Removida a regra `-keep class com.feather.launcher.data.** { *; }`
  do ProGuard — não há reflexão usando essas classes em lugar nenhum do
  projeto, então ela só impedia o R8 de otimizar esse pacote sem
  nenhum ganho real.

### Adicionado
- Dependência `org.jetbrains.kotlinx:kotlinx-collections-immutable`.

## [1.6.0] - 2026-09-19

Continuação da rodada de correções da auditoria: segurança da
assinatura de release (#6) e lambdas travadas/acessibilidade (#10).

### Adicionado
- Assinatura de release configurável via variáveis de ambiente
  (`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/
  `RELEASE_KEY_PASSWORD`), lidas pelo CI a partir de GitHub Secrets. Ver
  `RELEASING.md` para o passo a passo de configuração. Sem os secrets
  configurados, o release continua caindo de volta pra chave de debug
  (build não quebra, só não fica assinado com a chave de verdade).

### Corrigido
- **Segurança (#6):** o release era assinado com a mesma chave de
  debug, versionada no repositório com senha pública. Num repositório
  público com CI publicando o release automaticamente, isso permitia
  que qualquer pessoa assinasse um APK malicioso com a mesma
  assinatura e o Android aceitasse como "atualização" — grave,
  considerando que o app pede Acesso a Notificações. Gerada uma chave
  de release de verdade (fora do repositório, entregue separadamente).
- **Lambdas "congeladas" (#10):** `pointerInput(key) { detectTapGestures(...) }`
  só reinicia sua coroutine quando a `key` muda. Em `SpaceTabsRow`, a
  key era `space.id` (não muda ao renomear) — renomear uma aba e tocar
  segurar nela de novo abria o diálogo com o nome ANTIGO pré-preenchido.
  O mesmo padrão existia (com impacto menor) em `AppTile`, `AppRow` e
  `AppGridTile`. Trocado por `Modifier.combinedClickable`, que lê
  `onClick`/`onLongClick` sempre atualizados a cada recomposição.
- **Acessibilidade (#10):** `detectTapGestures` não gera nenhuma
  semântica de clique — o TalkBack não conseguia abrir nenhum app por
  esta launcher. `combinedClickable` expõe a ação corretamente para
  leitores de tela, nos mesmos pontos acima e no seletor de widgets
  (`WidgetProviderRow`, agora com `clickable` simples).
- (P6, de brinde) `BitmapPainter(...)` recriado a cada recomposição
  trocado por `Image(bitmap = ...)` nos mesmos pontos; removido um
  `.background(Color.Transparent)` sem efeito em `AppTile`.

## [1.5.0] - 2026-09-19

Correções a partir de uma auditoria completa do código (gerada por um
agente auditor separado, verificada manualmente contra o código real
antes de aplicar qualquer coisa). Cobre os 4 problemas de maior
prioridade apontados: um crash provável, widgets trocando de lugar, a
lista de apps nunca atualizando, e o jank mais perceptível da lista.
Inclui também a correção do "voltar fecha o launcher", confirmada em
uso real.

### Adicionado
- A lista de apps agora se atualiza sozinha ao instalar, desinstalar ou
  atualizar um app, via `LauncherApps.Callback` (com um pequeno
  debounce, já que uma atualização de app costuma disparar mais de um
  evento em sequência). Até aqui, a lista só recarregava reiniciando o
  processo do launcher inteiro.
- Vínculos de apps por Space para pacotes desinstalados são limpos
  automaticamente a cada atualização da lista, em vez de acumular
  indefinidamente nas prefs.

### Corrigido
- **Crash por chave duplicada na Gaveta:** `AppListView` e `AppGridView`
  usavam `packageName` como key; um pacote com mais de uma activity
  LAUNCHER (comum em algumas ROMs e em apps do Google/Samsung) gerava
  duas entradas com a mesma key, e o Compose lança
  `IllegalArgumentException`. Trocado para `app.key`
  (`packageName + activityName`), que já era usado corretamente na Home.
- **Widgets trocando de lugar ao remover/redimensionar:** a grade
  manual de widgets (`WidgetsGrid`) não envolvia cada item em `key()`.
  Sem isso, o Compose identifica cada `WidgetCard` pela posição na
  árvore, não pelo widget que ele representa — ao remover o widget A,
  o slot dele passava a receber os dados do widget B, mas o
  `AndroidView` (cuja `factory` só roda uma vez por slot) continuava
  mostrando a view de A. Resultado: os botões de ação podiam agir no
  widget errado.
- **"Voltar" fechando o launcher na Home:** o `BackHandler` ficava
  desligado na Home, deixando o botão "voltar" cair no comportamento
  padrão do sistema — que, numa Activity HOME com `launchMode
  singleTask`, encerra a Activity, e o Android a recria na hora (por
  ser a launcher padrão). Na prática: a tela pisca e a grade de apps é
  reconstruída do zero. Confirmado em uso real. Agora o `BackHandler`
  fica sempre ativo; na Home, simplesmente não faz nada.
- **Painel de Widgets recriado a cada visita:** com
  `beyondViewportPageCount = 0` no `HorizontalPager`, a página de
  Widgets era descartada por inteiro sempre que não estava visível —
  cada volta reinflava todos os `AppWidgetHostView` do zero. Esse é o
  mesmo tipo de problema que uma correção anterior (grade não-lazy, ver
  v1.4.0) já tinha resolvido, só que dentro do painel; faltava resolver
  também na troca de página. Agora Widgets e Home ficam sempre
  compostas (a Gaveta, mais pesada, continua sendo descartada quando
  não está visível — é a página com a lista completa de apps, e menos
  visitada a partir de Widgets diretamente).

## [1.4.4] - 2026-09-18

**Reversão** das duas mudanças da v1.4.3, que pioraram o app em teste
real: o widget da agenda continuou estático (nenhum ganho) e o botão
de "Alterar tamanho"/"Remover" parou de funcionar por completo
(regressão clara). Como nenhuma das duas trouxe benefício confirmado,
optamos por voltar ao comportamento da v1.4.2, que era conhecido e
funcionava.

### Revertido
- `detectLongPress` voltou a usar `waitForUpOrCancellation()` puro
  (sem a checagem manual de deslocamento do dedo adicionada na
  v1.4.3), que é o que fazia o toque-e-segure funcionar. A checagem de
  deslocamento tinha um bug não identificado que impedia o long-press
  de disparar.
- Removida a `NestedScrollInteropConnection` do painel de Widgets — não
  fez a lista da agenda do Google Calendar rolar, então ficou só como
  risco sem benefício.

### Conhecido
- O diálogo de ações do widget ainda pode abrir, ocasionalmente, se um
  swipe entre as abas Widgets ↔ Home começar bem dentro do cantinho de
  40dp (v1.4.2) — não resolvido nesta versão; qualquer tentativa de
  correção precisa ser testada com cuidado antes de substituir o que já
  funciona.
- Listas nativas dentro de widgets (ex.: agenda do Google Calendar)
  continuam sem rolar dentro do painel de Widgets — sem solução
  identificada até o momento.

## [1.4.3] - 2026-09-18

Segunda camada de proteção para o mesmo problema do diálogo de ações
do widget abrindo durante o swipe entre páginas (v1.4.2), mais uma
correção de compatibilidade de rolagem dentro de widgets nativos.

### Corrigido
- O detector de toque e segure na faixa do widget (v1.4.1/v1.4.2) não
  cancelava a detecção quando o dedo se movia — só parava ao soltar ou
  ao estourar o tempo. Isso deixava uma brecha residual mesmo com a
  área já restrita a um cantinho (v1.4.2): se o arrasto entre páginas
  começasse bem ali, o diálogo ainda podia abrir. Agora o detector
  cancela assim que o deslocamento ultrapassa `touchSlop`, do mesmo
  jeito que o `detectTapGestures` do próprio Compose já faz.
- Widgets com listas roláveis nativas (ex.: a agenda do Google
  Calendar) não conseguiam rolar dentro do próprio widget — o
  `verticalScroll` do painel de Widgets "vencia" qualquer arrasto
  vertical antes da lista nativa ter chance de rolar sozinha.
  Adicionado `rememberNestedScrollInteropConnection()`
  (`androidx.compose.ui.platform`), a ponte oficial do Compose para
  este tipo de conflito. **Ressalva:** essa ponte foi desenhada
  principalmente para Views que implementam o protocolo moderno de
  nested scrolling (`RecyclerView`, `NestedScrollView`); não há
  confirmação de que a `ListView` clássica usada por widgets de coleção
  (como o da Agenda) participa desse protocolo — o efeito real precisa
  ser validado em aparelho. Se não resolver, o próximo passo é uma
  abordagem estrutural diferente para o scroll do painel de Widgets.

## [1.4.2] - 2026-09-18

Segunda correção no diálogo de ações do widget (v1.4.1), reportada em
uso real ao transitar entre as abas de Widgets e Home.

### Corrigido
- A faixa de toque e segure no topo do widget (v1.4.1) ocupava a
  largura inteira do card. Como o gesto de arrastar da aba de Widgets
  para a Home desliza horizontalmente bem em cima dela, às vezes o
  diálogo de "Alterar tamanho"/"Remover" abria no meio da transição
  entre páginas. A área sensível agora fica só num cantinho fixo (40dp)
  no canto esquerdo da faixa, longe do caminho natural do dedo ao
  deslizar entre as abas.

## [1.4.1] - 2026-09-18

Correção de comportamento no diálogo de ações do widget (v1.4.0),
reportada em uso real com um widget de controle de ponto.

### Corrigido
- Toque e segure para abrir "Alterar tamanho"/"Remover" disparava por
  engano ao interagir normalmente com o widget (ex.: segurar um botão
  de "bater ponto" por tempo suficiente para o app processar a ação).
  A área sensível ao toque longo agora é uma faixa dedicada e visível
  no topo do card, separada do conteúdo do widget — o resto do card
  (onde ficam os botões reais do widget) não tem mais nenhuma detecção
  de toque longo por cima.
- Tempo de toque e segure aumentado (~600ms, um pouco acima do padrão
  do sistema) como margem extra.

## [1.4.0] - 2026-09-18

Leva de ajustes de usabilidade na aba de Widgets e na Home, a partir de
feedback de uso real do app.

### Adicionado
- Diálogo de ações ao tocar e segurar um widget já adicionado ("Alterar
  tamanho" / "Remover"), substituindo a antiga barra fixa com texto
  "Tamanho" e botão de fechar — o widget ocupa toda a área do card agora.
- No seletor de widgets: resumo curto (2ª linha) para diferenciar
  widgets do mesmo app (usa a descrição declarada pelo provider no
  Android 12+, com o tamanho sugerido como alternativa em versões
  anteriores), e os widgets passaram a ser agrupados por app, com um
  cabeçalho por grupo.
- Ponto de notificação nos ícones dos apps na Home, para qualquer app
  com notificação ativa no momento (antes só existia o resumo da
  última notificação, sem indicação nos ícones).
- Segunda linha no resumo de notificação da Home: o corpo da
  notificação agora aparece por inteiro (sem cortar com "..."),
  separado da linha com app + título.

### Alterado
- Botão de aparência (engrenagem) movido do lado direito para o
  esquerdo da barra superior da Home — mais longe do apoio natural do
  polegar, reduzindo toques acidentais.
- Relógio da Home reaproximado do topo (padding reduzido), compensando
  o espaço extra da segunda linha de notificação.
- Botão "+" de nova aba, na Home, agora alinhado verticalmente ao
  centro com o texto das abas (`Dia a dia`, `Trabalho` etc.).

### Removido
- Linha "Usados recentemente" da Home, incluindo o rastreamento que só
  existia para alimentá-la (`AppPrefs`/`LauncherViewModel`).

## [1.3.0] - 2026-09-17

### Adicionado
- Alternância entre lista e grade na Gaveta de apps, com ícone dinâmico
  dentro do próprio campo de busca (mostra sempre o modo *para o qual*
  o toque leva). A escolha é lembrada entre sessões.

## [1.2.0] - 2026-09-17

Versão de referência que reúne a renomeação completa do projeto e a
primeira rodada de correções de fluidez, motivada pela comparação com
a launcher AOSP (Quickstep) original do aparelho de testes.

### Alterado
- Renomeação completa de "Aviate Lite" para **Feather Launcher**: pacote
  (`com.aviatelite.launcher` → `com.feather.launcher`), nome do app,
  classes de tema/notificação, nome do projeto raiz e nome do APK gerado.
- CI (`build.yml`) passou a gerar também o APK de **release**
  (minificado, sem `debuggable`), além do de debug — necessário para
  avaliar fluidez de forma justa.
- Painel de widgets: `LazyVerticalGrid` trocado por uma grade não-lazy,
  evitando que o `AppWidgetHost` recriasse os widgets nativos a cada
  rolagem.
- Home: removidas as camadas de fade (`AnimatedVisibility`) do resumo
  de notificação e do painel de aparência, reduzindo overdraw na GPU
  do aparelho de testes.

### Adicionado
- Infraestrutura para Baseline Profile (`androidx.profileinstaller`) —
  o arquivo `baseline-prof.txt` em si ainda precisa ser gerado via
  Macrobenchmark num aparelho físico.
- Gatilhos de `pull_request` e execução manual (`workflow_dispatch`) no CI.
- README reescrito do zero, com o real estado do código documentado.

### Removido
- Texto "toque e segure para remover" nos cards de widget (a ação já
  tinha um botão explícito).
- Dependência não utilizada do Coil (`io.coil-kt:coil-compose`).
- Dependência órfã do `androidx.compose.animation:animation`, sem mais
  uso após a remoção dos fades acima.
