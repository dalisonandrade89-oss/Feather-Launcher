plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------
// Versionamento semântico (MAJOR.MINOR.PATCH), a partir da v1.2.0:
//   MAJOR — mudança incompatível (ex.: apps precisam ser reconfigurados)
//   MINOR — funcionalidade nova, compatível com o que já existia
//   PATCH — correção de bug ou de performance, sem funcionalidade nova
// A cada versão nova: incremente appVersionCode (sempre +1, inteiro
// crescente) e ajuste appVersionName — e registre a mudança em
// CHANGELOG.md. O nome do APK de saída e o rodapé do app
// (BuildConfig.VERSION_NAME) usam estas mesmas variáveis, então nunca
// ficam dessincronizados.
// ---------------------------------------------------------------------
val appVersionCode = 12
val appVersionName = "1.6.1"

android {
    namespace = "com.feather.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.feather.launcher"
        // minSdk 26 -> necessário para ApplicationInfo.category (classificação de apps)
        // e para APIs estáveis do AppWidgetHost usadas aqui.
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables { useSupportLibrary = true }
    }

    // -------------------------------------------------------------
    // FIX #1: "não dá para instalar como atualização, exige desinstalar"
    // Esse erro (INSTALL_FAILED_UPDATE_INCOMPATIBLE) acontece quando o
    // APK novo é assinado com uma chave diferente da instalada. Isso é
    // muito comum em debug: por padrão, CADA máquina/computador gera seu
    // próprio ~/.android/debug.keystore automaticamente, então a build
    // feita numa máquina não bate com a instalada de outra build/máquina.
    //
    // Solução: declaramos um debug.keystore FIXO, versionado dentro do
    // projeto (app/keystore/debug.keystore), e apontamos o buildType
    // debug para ele explicitamente. Assim, qualquer pessoa/CI que gerar
    // a build a partir deste repositório assina com a MESMA chave,
    // permitindo instalar novas versões por cima da anterior sem
    // desinstalar.
    //
    // FIX #20 (segurança, item #6 da auditoria): o buildType release
    // usava essa MESMA chave de debug — versionada no repositório, com
    // senha pública ("android"). Num repositório público com CI que
    // publica o release já assinado, qualquer pessoa podia extrair essa
    // chave e assinar um APK malicioso com a mesma assinatura, que o
    // Android aceitaria como "atualização" por cima da instalação
    // legítima — grave, considerando que o app pede Acesso a
    // Notificações. Agora o release usa uma signingConfig própria, lida
    // de variáveis de ambiente (nunca hardcoded, nunca commitada). Sem
    // essas variáveis definidas (build local, sem os secrets do CI),
    // cai de volta pro keystore de debug — para continuar funcionando
    // sem configuração extra em testes locais, só não é mais o que vai
    // pro release publicado pelo CI.
    // -------------------------------------------------------------
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    // Nota: o workflow do GitHub Actions manda "" (string vazia), não a
    // variável simplesmente ausente, quando o secret não está configurado
    // — por isso isNullOrBlank(), não apenas != null.
    val hasReleaseSigningEnv = !releaseStoreFile.isNullOrBlank()

    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (hasReleaseSigningEnv) {
                storeFile = file(releaseStoreFile!!)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigningEnv) {
                signingConfigs.getByName("release")
            } else {
                // Fallback só para build local sem os secrets configurados
                // (ver RELEASING.md para gerar e configurar a chave real).
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Habilita BuildConfig.VERSION_NAME / VERSION_CODE, usados para
        // mostrar a versão no rodapé do app (FIX #2).
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // FIX #2: nome do APK de saída incluindo a versão, ex.:
    // Feather-v1.2.0-debug.apk / Feather-v1.2.0-release.apk
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "Feather-v${appVersionName}-${variant.buildType.name}.apk"
            }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // FIX #26 (P2 da auditoria): sem Kotlin 2.0 (que traz "strong
    // skipping" por padrão), o compilador do Compose trata qualquer
    // List/Map/Set puro do Kotlin como "instável" (podem ser mutáveis
    // por baixo), então telas que recebem esses tipos como parâmetro
    // NUNCA pulam recomposição — cada notificação recompunha Home,
    // Gaveta e Widgets, mesmo as duas últimas não tendo nada relevante
    // mudado. kotlinx.collections.immutable é reconhecido como estável
    // pelo compilador do Compose mesmo sem strong skipping — mais
    // seguro que trocar a versão do Kotlin do projeto inteiro sem
    // conseguir compilar pra validar.
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose - somente APIs estáveis (Material3 + Foundation)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")

    // FIX #3 (fluidez): permite que o ART aplique, já na instalação, a
    // compilação AOT ("speed profile") descrita em src/main/baseline-prof.txt
    // nos caminhos quentes de composição/scroll, em vez de esperar o JIT
    // esquentar em runtime — reduz jank nos primeiros usos após instalar.
    // Sem esta dependência, um baseline-prof.txt no projeto é ignorado.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
