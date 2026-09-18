# Changelog

Todas as mudanças notáveis do projeto são registradas aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/)
(`MAJOR.MINOR.PATCH`) a partir da v1.2.0:

- **MAJOR** — mudança incompatível (ex.: exige reconfigurar apps/Spaces)
- **MINOR** — funcionalidade nova, compatível com o que já existia
- **PATCH** — correção de bug ou de performance, sem funcionalidade nova

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
