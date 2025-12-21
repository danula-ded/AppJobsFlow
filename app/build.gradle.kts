import java.util.Properties

plugins {
    id("com.android.application") version "8.8.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

val localProps = Properties().apply {
    val lp = rootProject.file("local.properties")
    if (lp.exists()) lp.inputStream().use { stream -> this.load(stream) }
}

fun prop(name: String): String =
    (project.findProperty(name)?.toString())
        ?: localProps.getProperty(name)
        ?: System.getenv(name)
        ?: ""

android {
    namespace = "com.github.jobsflow.appjobsflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.jobsflow.appjobsflow"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Securely inject HH credentials via Gradle properties or CI env (no hardcoding)
        buildConfigField(
            "String",
            "HH_CLIENT_ID",
            "\"${prop("HH_CLIENT_ID")}\""
        )
        buildConfigField(
            "String",
            "HH_CLIENT_SECRET",
            "\"${prop("HH_CLIENT_SECRET")}\""
        )
        buildConfigField(
            "String",
            "HH_REDIRECT_URI",
            "\"${prop("HH_REDIRECT_URI")}\""
        )
    }

    signingConfigs {
        if (
            project.hasProperty("KEYSTORE_PATH") &&
            project.hasProperty("KEYSTORE_PASSWORD") &&
            project.hasProperty("KEY_ALIAS") &&
            project.hasProperty("KEY_PASSWORD")
        ) {
            create("release") {
                storeFile = file("../" + project.property("KEYSTORE_PATH") as String)
                storePassword = project.property("KEYSTORE_PASSWORD") as String
                keyAlias = project.property("KEY_ALIAS") as String
                keyPassword = project.property("KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.10"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    implementation("androidx.compose.foundation:foundation")

    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    implementation(libs.androidx.activity.compose)

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

