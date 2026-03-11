plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val rustProjectDir = file("src/main/rust/hoshiepub")
val uniffiOutDir = layout.buildDirectory.dir("generated/source/uniffi/main/kotlin").get().asFile
val rustJniLibsDir = layout.buildDirectory.dir("jniLibs").get().asFile
val cargo = System.getenv("HOME") + "/.cargo/bin/cargo"
val hostLibExtension = when {
    System.getProperty("os.name").lowercase().contains("mac") -> "dylib"
    System.getProperty("os.name").lowercase().contains("win") -> "dll"
    else -> "so"
}

android {
    namespace = "de.manhhao.hoshi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.manhhao.hoshi"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++23"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf("com/sun/jna/**")
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("arm64-v8a", "x86_64")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    sourceSets["main"].java.directories.add("build/generated/source/uniffi/main/kotlin")
    sourceSets["main"].jniLibs.directories.add("build/jniLibs")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.jna)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val buildRustHost by tasks.registering(Exec::class) {
    workingDir = rustProjectDir
    commandLine(cargo, "build")
}

val generateUniffiKotlin by tasks.registering(Exec::class) {
    dependsOn(buildRustHost)
    workingDir = rustProjectDir

    val hostLibPath = rustProjectDir.resolve("target/debug/libhoshiepub.$hostLibExtension")

    commandLine(
        cargo, "run", "--bin", "uniffi-bindgen", "--",
        "generate",
        "--library", hostLibPath.absolutePath,
        "--language", "kotlin",
        "--out-dir", uniffiOutDir.absolutePath
    )
}

val buildRustAndroidDebug by tasks.registering(Exec::class) {
    workingDir = rustProjectDir
    commandLine(
        cargo, "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", rustJniLibsDir.absolutePath,
        "build"
    )
}

val buildRustAndroidRelease by tasks.registering(Exec::class) {
    workingDir = rustProjectDir
    commandLine(
        cargo, "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", rustJniLibsDir.absolutePath,
        "build",
        "--release"
    )
}

tasks.named("preBuild") {
    dependsOn(generateUniffiKotlin)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateUniffiKotlin)
    source("build/generated/source/uniffi/main/kotlin")
}

afterEvaluate {
    tasks.named("preDebugBuild") {
        dependsOn(buildRustAndroidDebug)
    }
    tasks.named("preReleaseBuild") {
        dependsOn(buildRustAndroidRelease)
    }
}
