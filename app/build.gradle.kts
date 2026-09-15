plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.umilauncher.kiss"
    // Umidigi A11 Pro roda Android 11 (API 30) de fábrica
    compileSdk = 34

    defaultConfig {
        applicationId = "com.umilauncher.kiss"
        minSdk = 26           // cobre o A11 Pro (Android 11) com folga
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            // Reduz drasticamente o tamanho do APK e a memória de classes/recursos
            // carregados em runtime -> menos pressão sobre os 4GB de RAM.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // Núcleo mínimo do Compose - SEM Material3 completo, sem animações extras,
    // sem Coil/Glide (ícones são tratados manualmente e cacheados em memória
    // controlada, ver IconCache.kt) para manter o footprint de memória baixo.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation") // LazyColumn etc.
    // Usamos só o essencial do Material3 (evita puxar ícones extendidos, que
    // são pesados). Ícones usados: os poucos SVG locais em ui/theme/Icons.kt
    implementation("androidx.compose.material3:material3")

    implementation("androidx.datastore:datastore-preferences:1.1.1") // config leve (favoritos, ordem)

    testImplementation("junit:junit:4.13.2")
}
