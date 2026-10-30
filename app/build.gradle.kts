import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val operatorPassword: String = localProps.getProperty("mohammad.operatorPassword") ?: ""

android {
    namespace = "ir.mohammad.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.mohammad.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.6.0"
        val escapedPassword = operatorPassword
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "OPERATOR_PASSWORD", "\"$escapedPassword\"")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(files("libs/mohammad-core.aar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
