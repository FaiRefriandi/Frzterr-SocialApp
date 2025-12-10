plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.frzlss.frzterr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frzlss.frzterr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
