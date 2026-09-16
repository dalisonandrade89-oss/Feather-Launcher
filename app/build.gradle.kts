plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------
// FIX #1 e #2: versionamento centralizado. Ao lançar uma nova build,
// basta incrementar APP_VERSION_CODE (sempre um inteiro maior que o
// anterior) e ajustar APP_VERSION_NAME. O nome do APK de saída e o
// rodapé do app (BuildConfig.VERSION_NAME) usam essas mesmas variáveis,
// então nunca ficam dessincronizados.
// ---------------------------------------------------------------------
val appVersionCode = 2
val appVersionName = "1.1.0"

android {
    namespace = "com.aviatelite.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aviatelite.launcher"
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
    // -------------------------------------------------------------
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
            // NOTA: para publicação real na Play Store, troque por uma
            // signingConfig de release própria (chave privada segura,
            // fora do repositório). Mantido no keystore de debug aqui
            // apenas para permitir builds de release "instaláveis" em
            // testes locais sem exigir configuração adicional.
            signingConfig = signingConfigs.getByName("debug")
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
    // AviateLite-v1.1.0-debug.apk / AviateLite-v1.1.0-release.apk
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "AviateLite-v${appVersionName}-${variant.buildType.name}.apk"
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
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose - somente APIs estáveis (Material3 + Foundation)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Carregamento assíncrono/leve de imagens (usado para caches de bitmap)
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
