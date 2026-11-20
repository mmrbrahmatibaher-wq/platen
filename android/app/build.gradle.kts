import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mahdi.platen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mahdi.platen"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "3.2"
        vectorDrawables.useSupportLibrary = true
    }

    // Release signing, in order of preference:
    //  1. environment variables PLATEN_KEYSTORE, PLATEN_KEYSTORE_PASSWORD, PLATEN_KEY_ALIAS, PLATEN_KEY_PASSWORD
    //  2. keystore.properties (storeFile, storePassword, keyAlias, keyPassword) in this folder or in keystore/
    //  3. otherwise the Android debug key, so anyone can build an installable release APK.
    // Keystores and keystore.properties are git-ignored and never committed.
    val ksProps = Properties().apply {
        listOf("keystore.properties", "keystore/keystore.properties").map { rootProject.file(it) }
            .firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signing(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: ksProps.getProperty(prop)
    val storePath = signing("PLATEN_KEYSTORE", "storeFile")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath).takeIf { it.exists() } ?: file(storePath)
                storePassword = signing("PLATEN_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signing("PLATEN_KEY_ALIAS", "keyAlias")
                keyPassword = signing("PLATEN_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "2048m"
            it.jvmArgs("-Djava.awt.headless=true")
            it.systemProperty("platen.screenshots", rootProject.file("screenshots").absolutePath)
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
}
