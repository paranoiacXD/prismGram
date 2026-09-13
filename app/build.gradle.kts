import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.prismgram"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prismgram"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("int", "TG_API_ID", localProps.getProperty("TG_API_ID", "0"))
        buildConfigField(
            "String",
            "TG_API_HASH",
            "\"${localProps.getProperty("TG_API_HASH", "")}\""
        )
    }

    lint {
        // agp 8.7 chokes on the android-37.1 platform ("For input string: 37.1"),
        // not worth blocking release builds over lint anyway
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            // debug builds run compose/kotlin completely unoptimized, which is a
            // big part of the jank. minify + shrink gives the real performance
            isMinifyEnabled = true
            isShrinkResources = true
            // signed with the debug key so it can update the installed app
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // animated webp + gif decoding, telegram "static" stickers are often animated webp
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.airbnb.android:lottie-compose:6.6.6")
    // qr login, so you never need a code when another device is logged in
    implementation("com.google.zxing:core:3.5.3")
    // telegram gifs are mp4, coil cant play those
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.44")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
