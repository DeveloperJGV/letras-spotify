import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// El Client ID de la app de Spotify vive en local.properties (spotify.clientId=...), fuera de git.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val spotifyClientId = localProps.getProperty("spotify.clientId", "")
// Javbots (la oficina en la PC) comparte lo que suena por su puerto de pantallas. Sin token, no se usa.
val javbotsToken = localProps.getProperty("javbots.token", "")
val javbotsHost = localProps.getProperty("javbots.host", "")
val javbotsPort = localProps.getProperty("javbots.port", "3101")

android {
    namespace = "dev.jgv.letras"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.jgv.letras"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "JAVBOTS_TOKEN", "\"$javbotsToken\"")
        buildConfigField("String", "JAVBOTS_HOST", "\"$javbotsHost\"")
        buildConfigField("int", "JAVBOTS_PORT", javbotsPort)
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // App personal, no se publica: se firma con la llave de debug para instalarla directo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Android 5.1 es a propósito: es la tablet que hay.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
