plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.obdfacil.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.obdfacil.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.7"
    }

    // Assinatura fixa: o mesmo certificado em toda compilação, para o Android aceitar
    // instalar a versão nova por cima e manter o histórico. A senha vem do segredo
    // SIGNING_PASSWORD do GitHub (ninguém mais a vê); a chave é criada pelo próprio CI.
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
    val keystoreFile = file("obdfacil.keystore")
    val hasFixedKey = !signingPassword.isNullOrEmpty() && keystoreFile.exists()
    signingConfigs {
        create("fixa") {
            if (hasFixedKey) {
                storeFile = keystoreFile
                storePassword = signingPassword
                keyAlias = "obdfacil"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasFixedKey) "fixa" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
