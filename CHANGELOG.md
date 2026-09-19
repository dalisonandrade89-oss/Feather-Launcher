# Changelog

Todas as mudanças notáveis do projeto são registradas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/)
(`MAJOR.MINOR.PATCH`) a partir da v1.2.0:

- **MAJOR** — mudança incompatível (ex.: exige reconfigurar apps/Spaces)
- **MINOR** — funcionalidade nova, compatível com o que já existia
- **PATCH** — correção de bug ou de performance, sem funcionalidade nova

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
