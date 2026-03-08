plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.frzterr.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frzterr.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = false
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    // Android core libs
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.1")

    // Splash
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Supabase 3.x (VERSI STABIL 3.2.x)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.1"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Optional realtime
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // Ktor engine (wajib Supabase)
    implementation("io.ktor:ktor-client-okhttp:3.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Sign-In (Credential Manager) - all versions aligned
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.moshi:moshi:1.15.0")

    //bumptech sama glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    //seialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    //buat laod pp
    implementation("io.coil-kt:coil:2.5.0")
    // IMAGE CROPPER
    implementation("com.github.yalantis:ucrop:2.2.8")

    //ripres halamin
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Shimmer Effect
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // PhotoView for Zoomable ImageView
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // Trust Wallet Core
    implementation("com.trustwallet:wallet-core:4.6.0")

    // Chart library untuk harga crypto
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // QR Code generator untuk fitur Receive
    implementation("com.google.zxing:core:3.5.2")
}
