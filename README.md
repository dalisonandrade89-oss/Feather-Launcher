# Feather Launcher

Launcher Android minimalista inspirado no antigo **Aviate Launcher**, escrito em **Kotlin + Jetpack Compose**.

Otimizado para aparelhos de entrada com pouca RAM — desenvolvido e testado no **Umidigi A11 Pro** (MediaTek Helio G25, 4 GB RAM, tela 1080p @ 60Hz).

---

## Índice

- [Objetivo do projeto](#objetivo-do-projeto)
- [Funcionalidades](#funcionalidades)
- [Como abrir e rodar](#como-abrir-e-rodar)
- [CI/CD — build automático do APK](#cicd--build-automático-do-apk)
- [Versionamento](#versionamento)
- [Arquitetura](#arquitetura)
- [Decisões de baixo consumo de RAM](#decisões-de-baixo-consumo-de-ram)
- [Decisões de fluidez a 60 Hz](#decisões-de-fluidez-a-60-hz)
- [Correções de fluidez aplicadas](#correções-de-fluidez-aplicadas)
- [Débito técnico conhecido](#débito-técnico-conhecido)
- [Próximos passos sugeridos](#próximos-passos-sugeridos)

---

## Objetivo do projeto

Ser um launcher **extremamente leve, limpo e eficiente**, priorizando:

- o menor uso possível de memória RAM;
- inicialização rápida;
- máxima fluidez de navegação em hardware limitado.

Para isso, o projeto evita bibliotecas pesadas, animações desnecessárias e qualquer elemento visual que não se pague em usabilidade.

## Funcionalidades

O launcher é dividido em **3 telas navegáveis por swipe horizontal** (`HorizontalPager`), nesta ordem:

| Tela | Conteúdo |
|---|---|
| **Widgets** (esquerda) | Grade de widgets nativos do Android, adicionados via seletor visual, com resize por número de células |
| **Home** (centro, inicial) | Relógio, resumo da última notificação e a grade de apps do *Space* atual |
| **Gaveta de apps** (direita) | Lista completa e alfabética dos apps instalados, com busca em tempo real |

Outras funcionalidades:

- **Spaces (abas contextuais):** 3 abas padrão — *Dia a Dia*, *Trabalho*, *Noite* — mais quantas abas personalizadas o usuário quiser criar, renomear ou excluir. Um app só aparece em um Space se for explicitamente vinculado a ele pela gaveta (toque longo → marcar/desmarcar Spaces).
- **Resumo de notificações:** a última notificação recebida aparece resumida na Home, em duas linhas (origem + corpo completo, sem cortar), e some assim que é lida/descartada. Requer permissão manual de "Acesso a notificações", concedida pelo próprio usuário.
- **Ponto de notificação:** ícones de apps com notificação ativa no momento exibem um ponto no canto, na grade da Home.
- **Aparência:** alternância entre tema Escuro, Claro e Preto AMOLED, além de cor de destaque (*accent color*) personalizada via seletor de cores. O botão de aparência fica no canto superior esquerdo da Home (longe do apoio natural do polegar, para reduzir toques acidentais).
- **Widgets nativos:** suporte a `AppWidgetHost` para adicionar, redimensionar e remover widgets de outros apps, com fluxo completo de bind/configuração. Toque e segure o cantinho esquerdo da faixa no topo de um widget já adicionado para abrir "Alterar tamanho" ou "Remover" — só essa pequena área responde ao toque longo, deixando o resto do card livre tanto para o próprio widget quanto para o gesto de arrastar entre as abas. No seletor de widgets, as opções vêm agrupadas por app, cada uma com um resumo curto para diferenciar widgets parecidos do mesmo app.
- **Busca:** filtro "contains" simples e sem acentuação na gaveta de apps.
- **Lista ou grade na gaveta:** um ícone dentro do próprio campo de busca alterna entre ver os apps em lista (nome ao lado) ou em grade (como a Home). O ícone é dinâmico — sempre mostra o modo *para o qual* o toque leva (ex.: em lista, aparece o ícone de grade), e a escolha é lembrada entre sessões.

## Como abrir e rodar

1. Abra a pasta do projeto no Android Studio (Koala ou mais recente).
2. Deixe o Gradle sincronizar — usa **AGP 8.5.2**, **Kotlin 1.9.24** e **Compose BOM 2024.09.02**.
3. Rode em um dispositivo com **Android 8.0 (API 26)** ou superior — testado em Android 11, versão de fábrica do A11 Pro.
4. Ao abrir pela primeira vez, o Android perguntará se você quer usar o Feather Launcher como launcher padrão (por causa do `category.HOME` no manifesto). Escolha **"Sempre"** para testar como app principal.

> **Sobre o `gradlew` ausente:** este repositório não versiona o `gradle-wrapper.jar` (é um binário, e evitei gerá-lo fora de uma máquina com acesso normal a `services.gradle.org`). O Android Studio detecta isso automaticamente e oferece para gerar o wrapper ao abrir o projeto — ou, se preferir, rode `gradle wrapper` uma vez com o Gradle já instalado localmente para passar a usar `./gradlew` no dia a dia. O CI (abaixo) não depende do wrapper.

### Assinatura (debug)

O projeto versiona um `debug.keystore` fixo em `app/keystore/debug.keystore`. Isso evita o problema comum de `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, que acontece quando cada máquina gera seu próprio keystore de debug automaticamente — com uma chave compartilhada no repositório, qualquer build (local ou do CI) pode ser instalada por cima da anterior sem precisar desinstalar.

### Assinatura (release)

O release usa uma chave própria, configurada via variáveis de ambiente/secrets do CI — **nunca** versionada no repositório. Veja [`RELEASING.md`](RELEASING.md) para gerar a chave e configurar os 4 secrets necessários no GitHub. Sem essa configuração, o `assembleRelease` continua funcionando, só cai de volta para a chave de debug (build não fica com a assinatura de produção).

## CI/CD — build automático do APK

O workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) compila o projeto a cada `push` nas branches `main`, `master` ou `principal`, a cada `pull request`, ou manualmente pela aba **Actions** do GitHub (botão "Run workflow"). Ele gera **dois APKs**:

- `app-debug` — build de debug, sem otimizações, para desenvolvimento;
- `app-release` — build otimizada (minify + shrinkResources), a que deve ser usada para avaliar fluidez de verdade.

Para baixar: **Actions → [execução desejada] → Artifacts → `app-debug` ou `app-release`**.

O workflow instala o Gradle diretamente via `gradle/actions/setup-gradle`, em vez de depender do `./gradlew` (coerente com a ausência do wrapper, ver acima).

## Versionamento

O projeto segue [Versionamento Semântico](https://semver.org/lang/pt-BR/) (`MAJOR.MINOR.PATCH`) a partir da v1.2.0 — MAJOR para mudança incompatível, MINOR para funcionalidade nova, PATCH para correção de bug/performance sem funcionalidade nova. Cada versão é registrada no [`CHANGELOG.md`](CHANGELOG.md).

O número de versão fica centralizado no topo do `app/build.gradle.kts` (`appVersionCode`/`appVersionName`) — ele já alimenta sozinho o nome do APK gerado pelo CI e o rodapé exibido na Gaveta de apps, então nunca ficam dessincronizados.

## Arquitetura

```
LauncherApplication (Application)
 └─ instâncias globais únicas (repositórios, prefs)

MainActivity (Activity única)
 ├─ LauncherViewModel            -> estado da UI em StateFlow, por área
 │   ├─ AppRepository            -> PackageManager.queryIntentActivities (thread de background)
 │   ├─ AppPrefs                 -> SharedPreferences (tema, Spaces, vínculos)
 │   ├─ WidgetPrefs               -> SharedPreferences (widgets adicionados e seus spans)
 │   └─ NotificationRepository    -> alimentado pelo NotificationListenerService
 │
 └─ LauncherApp (Compose raiz)
     ├─ WidgetsPanelScreen        -> grade de widgets nativos (AppWidgetHost)
     ├─ HomeScreen                -> relógio, notificação, apps do Space atual
     └─ AppDrawerScreen           -> lista completa + busca + vínculo por Space
```

Principais pacotes:

| Pacote | Responsabilidade |
|---|---|
| `data/` | Modelos (`AppInfo`, `SpaceDef`, `ThemeMode`), persistência (`AppPrefs`), leitura de apps instalados (`AppRepository`), utilitários de texto e cor |
| `notification/` | `NotificationListenerService` e o repositório que expõe a última notificação para a UI |
| `widget/` | Gerência do `AppWidgetHost` e persistência dos widgets adicionados |
| `viewmodel/` | `LauncherViewModel`, único ponto de estado da UI, exposto via `StateFlow` |
| `ui/` | Telas Compose, tema (claro/escuro/AMOLED) e componentes reutilizáveis |

## Decisões de baixo consumo de RAM

| Decisão | Por quê |
|---|---|
| Sem Room/SQLite | Os dados persistidos (tema, Spaces, vínculos, widgets) são poucos e simples — `SharedPreferences` evita o footprint do SQLite residente em memória. |
| Ícones pré-decodificados e reduzidos | Cada ícone é desenhado direto em um bitmap de ~48dp (`AppRepository.drawableToSmallBitmap`) uma única vez, em vez de manter o `Drawable` original em resolução nativa (que pode passar de 190px por ícone). |
| Carregamento fora da main thread | `AppRepository.loadInstalledApps()` roda em `Dispatchers.Default`, então listar e decodificar ícones de centenas de apps não trava a UI. |
| Home com grade enxuta | A tela mais vista (Home) só renderiza os apps vinculados ao Space atual — não a lista completa do aparelho. |
| Sem Navigation Compose | As 3 telas são páginas de um único `HorizontalPager`, evitando o peso da lib de navegação para um caso simples de paginação. |
| `largeHeap = false` | De propósito: não pedimos heap grande, para não incentivar acúmulo de memória em vez de economia. |
| Minify + shrinkResources no release | Reduz classes e recursos carregados em runtime. |

## Decisões de fluidez a 60 Hz

O Helio G25 usa uma GPU PowerVR GE8320, modesta mesmo para 60fps com UI complexa. Por isso:

- Sem sombras/elevação (o `Card` do Material3 foi evitado) — cada sombra é uma camada extra de composição/blur na GPU.
- `LazyColumn`/`LazyVerticalGrid` sempre com `key` estável (`app.key`), evitando recomposição/relayout desnecessários ao rolar ou filtrar.
- Filtro de busca "contains" simples, não fuzzy — barato o suficiente para rodar a cada tecla digitada sem derrubar o frame.
- `configChanges` tratado no manifesto para a Activity não ser recriada (com toda a árvore de Compose remontada) em mudanças triviais de configuração.
- Botão "voltar" do sistema sempre retorna para a Home em vez de arriscar fechar a Activity, em qualquer página que não seja a Home.

## Correções de fluidez aplicadas

Depois de comparar a fluidez do Feather Launcher com a launcher AOSP (Quickstep) original do A11 Pro, identificamos que boa parte da diferença não vinha do Compose em si, e sim de decisões pontuais de implementação e de comparação (build de debug vs. sistema já otimizado). Correções já aplicadas:

| Correção | O que mudou |
|---|---|
| **Testar build de release, não debug** | O CI agora gera `assembleRelease` além de `assembleDebug` (minify + shrinkResources ligados). Compare a fluidez usando o `app-release`, não o `app-debug` — a build de debug é sempre mais lenta, em qualquer stack. |
| **Widgets nativos sem `LazyVerticalGrid`** | `WidgetsPanelScreen` trocou o grid preguiçoso por uma `Column` rolável com empacotamento manual em linhas. Um widget nativo hospedado via `AndroidView` é caro de recriar; num grid lazy, cada vez que ele saía e voltava da viewport o `AppWidgetHost` reinflava a `RemoteViews` do zero. Como o número de widgets é sempre pequeno, manter todos compostos é mais barato que recriá-los a cada rolagem. |
| **Sem `AnimatedVisibility` na Home** | O resumo de notificação e o painel de aparência agora aparecem/somem direto (`if` simples), sem fade. Cada `AnimatedVisibility` cria uma camada de composição extra para o blending de alpha — na GPU PowerVR GE8320 (fill-rate baixo), isso é overdraw evitável. |
| **Infra para Baseline Profile** | Adicionada a dependência `androidx.profileinstaller`, que permite ao app carregar um `baseline-prof.txt` e pedir ao ART para compilar AOT os caminhos "quentes" de composição/scroll já na instalação — evitando a lentidão do JIT "frio" nos primeiros usos. **Importante:** o arquivo `baseline-prof.txt` em si precisa ser gerado rodando um teste de Macrobenchmark num aparelho real (não é algo que dá pra gerar sem hardware); a dependência já está pronta, falta esse passo, que é o único desta lista que exige um dispositivo físico. |

## Débito técnico conhecido

- **CI ainda não roda testes automatizados** — só compila os dois APKs (debug e release). Não há testes unitários nem instrumentados configurados no workflow.
- **`baseline-prof.txt` ainda não existe** — ver a linha "Infra para Baseline Profile" acima; a dependência está pronta, falta gerar o profile com Macrobenchmark num aparelho real.

## Próximos passos sugeridos

Não incluídos ainda de propósito, para manter o footprint mínimo do MVP:

- Reordenar favoritos/apps por drag-and-drop dentro de um Space.
- Gestos configuráveis (ex.: swipe down para notificações).
- Testes automatizados (unitários para `AppRepository`/`AppPrefs`, e ao menos um smoke test de UI).
- Job de `pull_request` e build de `release` assinada no CI.
