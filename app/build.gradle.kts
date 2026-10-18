import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signature de publication : créez « keystore.properties » à la racine du projet (voir LISEZMOI.md).
// La MÊME clé de signature doit servir pour toutes les versions, sinon Android refuse la mise à jour.
val signature = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
    // Publication automatique (GitHub Actions) : la clé arrive par variables d'environnement.
    System.getenv("FLUXLITE_KEYSTORE")?.let { chemin ->
        setProperty("storeFile", chemin)
        setProperty("storePassword", System.getenv("FLUXLITE_KEYSTORE_PASSWORD") ?: "")
        setProperty("keyAlias", System.getenv("FLUXLITE_KEY_ALIAS") ?: "fluxlite")
        setProperty("keyPassword", System.getenv("FLUXLITE_KEYSTORE_PASSWORD") ?: "")
    }
}

android {
    namespace = "com.daoakashi.fluxlite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daoakashi.fluxlite"
        minSdk = 26
        targetSdk = 36
        // À chaque nouvelle version publiée : augmenter versionCode (entier) ET versionName.
        versionCode = 4
        versionName = "1.3.0"
        // Site officiel (GitHub Pages) : l'application y vérifie ses mises à jour.
        // Si le site change d'adresse, modifiez ces deux lignes puis publiez une nouvelle version.
        buildConfigField("String", "SITE_URL", "\"https://daoakashi.github.io/flux-web\"")
        buildConfigField("String", "UPDATE_URL", "\"https://daoakashi.github.io/flux-web/fluxlite/version.json\"")
    }

    signingConfigs {
        if (signature.isNotEmpty()) {
            create("publication") {
                val chemin = signature.getProperty("storeFile")
                storeFile = File(chemin).let { if (it.isAbsolute) it else rootProject.file(chemin) }
                storePassword = signature.getProperty("storePassword")
                keyAlias = signature.getProperty("keyAlias")
                keyPassword = signature.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Réduction du code désactivée : compilation plus sûre (l'APK reste léger, ~10 Mo).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signature.isNotEmpty()) signingConfig = signingConfigs.getByName("publication")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.code.scanner)
    debugImplementation(libs.compose.ui.tooling)
}
