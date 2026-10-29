plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.luhaopeng.flicktv"
    compileSdk = 30

    defaultConfig {
        applicationId = "com.luhaopeng.flicktv"
        minSdk = 21
        targetSdk = 30
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            // 性能测量使用 release 同等优化，但以 debug key 签名并使用独立包名，避免覆盖正式应用。
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.0.5" }
    packagingOptions { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lintOptions {
        // 个人侧载项目按需求固定 targetSdk 30，不参与 Google Play 上架政策检查。
        disable("ExpiredTargetSdkVersion")
    }
}

configurations.all {
    resolutionStrategy.force(
        "androidx.core:core:1.6.0",
        "androidx.core:core-ktx:1.6.0"
    )
}

dependencies {
    val compose = "1.0.5"
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.activity:activity-compose:1.3.1")
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.material:material:$compose")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("io.coil-kt:coil-compose:1.4.0")

    // compileSdk 30 下使用 ExoPlayer 2.x；它是 Media3 ExoPlayer 的兼容前身。
    implementation("com.google.android.exoplayer:exoplayer-core:2.16.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.16.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.16.1")

    // Room 2.3 保持 API 30 兼容；额外 SQLite JDBC 仅服务 Apple Silicon 构建期校验。
    implementation("androidx.room:room-runtime:2.3.0")
    implementation("androidx.room:room-ktx:2.3.0")
    kapt("androidx.room:room-compiler:2.3.0")
    kapt("org.xerial:sqlite-jdbc:3.36.0.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose")
}
