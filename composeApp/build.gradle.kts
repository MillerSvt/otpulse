import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val uploadStoreFile = providers.environmentVariable("OTPULSE_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("OTPULSE_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("OTPULSE_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("OTPULSE_UPLOAD_KEY_PASSWORD").orNull
val hasUploadSigning = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { it != null }

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.otpulse.app.ComposeApp")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(compose.preview)
            implementation(libs.play.services.code.scanner)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
        }
    }
}

android {
    namespace = "com.otpulse.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.otpulse.app"
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 33
        versionName = "0.12.0-beta02"
        resValue("string", "app_name", "OTPulse")
    }

    signingConfigs {
        if (hasUploadSigning) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "OTPulse Dev")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        // OTPulse uses ComponentActivity directly and has no Fragment dependency.
        // This detector incorrectly assumes registerForActivityResult is called on FragmentActivity.
        disable += "InvalidFragmentVersionForActivityResult"
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    buildFeatures.compose = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
