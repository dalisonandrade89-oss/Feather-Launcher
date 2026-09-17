Feather Launcher
Launcher minimalista inspirado no Aviate Launcher, escrito em Kotlin + Jetpack Compose, otimizado para o Umidigi A11 Pro (MediaTek Helio G25, 4GB RAM, tela 1080p @ 60Hz) e outros aparelhos de entrada com pouca RAM.
Intenção do Projeto
O Feather Launcher foi concebido com o objetivo primário de ser um launcher extremamente leve, limpo e eficiente. O foco do projeto é priorizar o menor uso possível de memória RAM, inicialização rápida e máxima fluidez na navegação para dispositivos com hardware limitado, eliminando animações pesadas, bibliotecas redundantes e elementos visuais desnecessários.
CI/CD — build automático do APK
O workflow .github/workflows/android-build.yml compila o projeto a cada push/pull request (ou manualmente pela aba Actions do GitHub, via "Run workflow") e publica o APK de debug gerado como artefato da execução — baixe em Actions → [execução] → Artifacts.

Detalhe importante: este repositório não versiona o gradle-wrapper.jar (arquivo binário — evitei gerá-lo fora de uma máquina com acesso normal ao services.gradle.org). Por isso o workflow instala o Gradle diretamente via gradle/actions/setup-gradle, em vez de depender do ./gradlew. Isso não afeta a build no GitHub Actions em nada.

Para desenvolvimento local no Android Studio, isso também não é um problema: ao abrir o projeto, o Studio detecta a ausência do wrapper e oferece para gerá-lo automaticamente (ou rode gradle wrapper uma vez, se você já tiver o Gradle instalado localmente, para passar a usar ./gradlew no dia a dia).
Como abrir
Abra a pasta do projeto no Android Studio (Koala ou mais recente).
Deixe o Gradle sincronizar (usa AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.09.02).
Rode no dispositivo (minSdk 26, testado em Android 11, que é o que o A11 Pro roda de fábrica).
Ao abrir pela primeira vez, o Android vai perguntar se você quer usar o Feather Launcher como launcher padrão (por causa do category.HOME no manifesto) — escolha "Sempre" para testar como app principal.
Arquitetura (por que cada peça existe)
LauncherApp (Application)      -> instâncias globais únicas (repo, cache, prefs)

 ├─ AppRepository              -> PackageManager.queryIntentActivities (IO thread)

 ├─ IconCache                  -> LruCache dimensionado por ActivityManager.memoryClass

 └─ FavoritesStore             -> DataStore Preferences (sem Room/SQLite)

MainActivity (Activity única)

 └─ LauncherViewModel           -> StateFlow único de UI, filtro em memória

     ├─ HomeScreen              -> favoritos em texto, sem ícones, swipe-up abre gaveta

     └─ AppDrawerScreen         -> lista completa com busca + ícones sob demanda
Decisões de baixo consumo de RAM (o pedido principal)
Decisão
Por quê
Sem Room/SQLite
Só há um conjunto de chaves favoritas — DataStore evita o footprint do SQLite residente.
Sem Glide/Coil
Ícones são poucos, locais e finitos; uma lib de imagem completa (com seus próprios caches, threads e infra) é overhead desnecessário — implementamos um IconCache próprio, minúsculo e auditável.
Ícones baixados de resolução
Decodificados e redimensionados para ~48dp antes de cachear (IconCache.kt), em vez de manter os ícones adaptativos em resolução nativa (que podem passar de 190px).
LruCache dimensionado dinamicamente
O tamanho do cache de ícones é uma fração pequena (~6%) do ActivityManager.getMemoryClass(), então em aparelhos com pouca RAM o cache automaticamente fica menor.
onTrimMemory implementado
Sob pressão de memória do sistema, o cache de ícones é esvaziado antes que o launcher vire alvo do low-memory killer.
Home sem ícones
A tela mais vista (Home) mostra só texto — zero bitmaps retidos persistentemente.
Sem Navigation Compose
Duas telas alternadas por if sobre um StateFlow — evita o peso da lib de navegação para um caso tão simples.
largeHeap = false
De propósito: não pedimos heap grande — isso incentivaria acumular memória em vez de ser econômico.
Minify + shrinkResources no release
Reduz classes/recursos carregados em runtime.

Decisões de fluidez a 60Hz (segundo ponto de atenção)
O Helio G25 usa uma GPU PowerVR GE8320, que é modesta mesmo para 60fps com UI complexa. Por isso:

Sem sombras/elevação (Card do Material3 foi evitado) — cada sombra é uma camada de composição/blur extra na GPU.
Sem transições/crossfade entre Home e Drawer — troca instantânea.
Sem ripple nos toques que só navegam (usamos indication = null), mantendo o ripple padrão apenas onde já é gratuito (combinedClickable dos itens de lista, que o usuário espera ver reagir ao toque).
LazyColumn com key = app.key em ambas as listas, evitando recomposição/relayout desnecessários ao filtrar ou rolar.
Filtro de busca "contains" simples, não fuzzy — barato o suficiente para rodar a cada tecla sem soluçar o frame.
windowAnimationStyle = null e splash sem translucidez no tema, evitando o "flash" de transição de janela do sistema ao abrir a Home.
configChanges tratado no manifesto para a Activity não ser recriada (com toda a árvore de Compose remontada) em mudanças triviais.
Próximos passos sugeridos (não incluídos aqui de propósito, para manter o footprint mínimo do MVP)
Reordenar favoritos por drag-and-drop.
Gestos configuráveis (ex.: swipe down para notificações).
Widget de data/hora na Home (opcional, cada widget nativo tem seu próprio custo de memória/composição — vale medir antes de adicionar).
