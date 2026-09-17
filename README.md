# Feather Launcher

Launcher Android minimalista inspirado no antigo **Aviate Launcher**, escrito em **Kotlin + Jetpack Compose**.

Otimizado para aparelhos de entrada com pouca RAM — desenvolvido e testado no **Umidigi A11 Pro** (MediaTek Helio G25, 4 GB RAM, tela 1080p @ 60Hz).

> ⚠️ **Nota de nomenclatura:** o projeto foi renomeado para "Feather Launcher", mas o código-fonte, o pacote (`com.aviatelite.launcher`) e o nome exibido no aparelho (`strings.xml` → `AviateLite`) ainda usam o nome antigo, **Aviate Lite**. Isso não afeta o funcionamento, mas veja a seção [Débito técnico conhecido](#débito-técnico-conhecido) se for renomear.

---

## Índice

- [Objetivo do projeto](#objetivo-do-projeto)
- [Funcionalidades](#funcionalidades)
- [Como abrir e rodar](#como-abrir-e-rodar)
- [CI/CD — build automático do APK](#cicd--build-automático-do-apk)
- [Arquitetura](#arquitetura)
- [Decisões de baixo consumo de RAM](#decisões-de-baixo-consumo-de-ram)
- [Decisões de fluidez a 60 Hz](#decisões-de-fluidez-a-60-hz)
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
| **Home** (centro, inicial) | Relógio, resumo da última notificação, apps recentes, e a grade de apps do *Space* atual |
| **Gaveta de apps** (direita) | Lista completa e alfabética dos apps instalados, com busca em tempo real |

Outras funcionalidades:

- **Spaces (abas contextuais):** 3 abas padrão — *Dia a Dia*, *Trabalho*, *Noite* — mais quantas abas personalizadas o usuário quiser criar, renomear ou excluir. Um app só aparece em um Space se for explicitamente vinculado a ele pela gaveta (toque longo → marcar/desmarcar Spaces).
- **Apps recentes:** fileira com os últimos apps abertos, exibida na Home.
- **Resumo de notificações:** a última notificação recebida aparece resumida na Home (requer permissão manual de "Acesso a notificações", concedida pelo próprio usuário).
- **Aparência:** alternância entre tema Escuro, Claro e Preto AMOLED, além de cor de destaque (*accent color*) personalizada via seletor de cores.
- **Widgets nativos:** suporte a `AppWidgetHost` para adicionar, redimensionar e remover widgets de outros apps, com fluxo completo de bind/configuração.
- **Busca:** filtro "contains" simples e sem acentuação na gaveta de apps.

## Como abrir e rodar

1. Abra a pasta do projeto no Android Studio (Koala ou mais recente).
2. Deixe o Gradle sincronizar — usa **AGP 8.5.2**, **Kotlin 1.9.24** e **Compose BOM 2024.09.02**.
3. Rode em um dispositivo com **Android 8.0 (API 26)** ou superior — testado em Android 11, versão de fábrica do A11 Pro.
4. Ao abrir pela primeira vez, o Android perguntará se você quer usar o Feather Launcher como launcher padrão (por causa do `category.HOME` no manifesto). Escolha **"Sempre"** para testar como app principal.

> **Sobre o `gradlew` ausente:** este repositório não versiona o `gradle-wrapper.jar` (é um binário, e evitei gerá-lo fora de uma máquina com acesso normal a `services.gradle.org`). O Android Studio detecta isso automaticamente e oferece para gerar o wrapper ao abrir o projeto — ou, se preferir, rode `gradle wrapper` uma vez com o Gradle já instalado localmente para passar a usar `./gradlew` no dia a dia. O CI (abaixo) não depende do wrapper.

### Assinatura (debug)

O projeto versiona um `debug.keystore` fixo em `app/keystore/debug.keystore`. Isso evita o problema comum de `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, que acontece quando cada máquina gera seu próprio keystore de debug automaticamente — com uma chave compartilhada no repositório, qualquer build (local ou do CI) pode ser instalada por cima da anterior sem precisar desinstalar.

> Para publicação real (Play Store ou distribuição fora de testes), troque a `signingConfig` de `release` por uma chave própria e privada, fora do repositório — hoje ela reaproveita o keystore de debug apenas para conveniência de testes locais.

## CI/CD — build automático do APK

O workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) compila o projeto automaticamente a cada `push` nas branches `main`, `master` ou `principal`, e publica o APK de debug gerado como artefato da execução.

Para baixar: **Actions → [execução desejada] → Artifacts → `app-debug`**.

O workflow instala o Gradle diretamente via `gradle/actions/setup-gradle`, em vez de depender do `./gradlew` (coerente com a ausência do wrapper, ver acima).

## Arquitetura

```
LauncherApplication (Application)
 └─ instâncias globais únicas (repositórios, prefs)

MainActivity (Activity única)
 ├─ LauncherViewModel            -> estado da UI em StateFlow, por área
 │   ├─ AppRepository            -> PackageManager.queryIntentActivities (thread de background)
 │   ├─ AppPrefs                 -> SharedPreferences (tema, Spaces, vínculos, recentes)
 │   ├─ WidgetPrefs               -> SharedPreferences (widgets adicionados e seus spans)
 │   └─ NotificationRepository    -> alimentado pelo NotificationListenerService
 │
 └─ LauncherApp (Compose raiz)
     ├─ WidgetsPanelScreen        -> grade de widgets nativos (AppWidgetHost)
     ├─ HomeScreen                -> relógio, notificação, recentes, apps do Space atual
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
| Sem Room/SQLite | Os dados persistidos (tema, Spaces, vínculos, recentes, widgets) são poucos e simples — `SharedPreferences` evita o footprint do SQLite residente em memória. |
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

## Débito técnico conhecido

Este projeto vem evoluindo rápido e a limpeza de detalhes menores ainda não acompanhou o ritmo. Vale a pena resolver em algum momento:

- **Nome inconsistente:** o app é "Feather Launcher" no README e no nome do repositório, mas o pacote (`com.aviatelite.launcher`), o projeto raiz (`AviateLiteLauncher`) e o nome exibido no aparelho (`strings.xml` → `AviateLite`) ainda usam o nome antigo. Renomear o pacote depois de publicado exige cuidado (muda a `applicationId`).
- **Dependência do Coil não utilizada:** `io.coil-kt:coil-compose` está declarado em `app/build.gradle.kts`, mas nenhum arquivo do projeto o importa — os ícones são carregados manualmente em `AppRepository`. Isso aumenta o tamanho do APK sem necessidade; pode ser removido.
- **CI cobre só builds de debug:** o workflow atual (`build.yml`) roda apenas em `push` para `main`/`master`/`principal` e gera só o APK de debug — não há gatilho em `pull_request` nem execução manual (`workflow_dispatch`), e não há job de build de `release` nem de testes.

## Próximos passos sugeridos

Não incluídos ainda de propósito, para manter o footprint mínimo do MVP:

- Reordenar favoritos/apps por drag-and-drop dentro de um Space.
- Gestos configuráveis (ex.: swipe down para notificações).
- Testes automatizados (unitários para `AppRepository`/`AppPrefs`, e ao menos um smoke test de UI).
- Job de `pull_request` e build de `release` assinada no CI.
