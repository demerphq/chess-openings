plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/main").get().asFile
val generatedJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/main").get().asFile

android {
    namespace = "com.chessopenings.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chessopenings.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].apply {
        assets.srcDir(generatedAssetsDir)
        jniLibs.srcDir(generatedJniLibsDir)
    }

    androidResources {
        noCompress += "nnue"
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

val syncSharedSeedAssets = tasks.register<Sync>("syncSharedSeedAssets") {
    from(rootProject.file("../Chess Openings/Resources/openings.json"))
    from(rootProject.file("stockfish")) {
        include("nnue/*.nnue")
        into("stockfish")
    }
    into(generatedAssetsDir)
}

val syncStockfishNativeExecutables = tasks.register<Sync>("syncStockfishNativeExecutables") {
    listOf("arm64-v8a", "x86_64").forEach { abi ->
        from(rootProject.file("stockfish/$abi/stockfish")) {
            into(abi)
            rename { "libstockfish.so" }
        }
    }
    into(generatedJniLibsDir)
}

tasks.named("preBuild") {
    dependsOn(syncSharedSeedAssets)
    dependsOn(syncStockfishNativeExecutables)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
