import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.animationstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.animationstudio"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // O primeiro plano do ícone do app é gerado em build/generated/icon-res a partir de icon/app_icon.b64.
    sourceSets {
        getByName("main") {
            res.srcDir(layout.buildDirectory.dir("generated/icon-res"))
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // PointerInputChange.historical (traço suave) e combinedClickable são APIs experimentais.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildTypes {
        getByName("release") {
            val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

// Ícone: lê o JPEG em base64 (icon/app_icon.b64) e escreve o PNG do primeiro plano do ícone adaptativo,
// com a imagem a 78% da tela para não ser cortada pela máscara redonda/arredondada do launcher.
// Se o ficheiro faltar ou estiver danificado, gera-se um quadrado liso para o build nunca falhar por isso.
val generateLauncherIcon by tasks.registering {
    val source = file("icon/app_icon.b64")
    val outDir = layout.buildDirectory.dir("generated/icon-res")
    inputs.files(source)
    outputs.dir(outDir)
    doLast {
        System.setProperty("java.awt.headless", "true")
        val size = 432
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        try {
            val bytes = Base64.getMimeDecoder().decode(source.readText().trim())
            val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalStateException("imagem inválida")
            val target = (size * 0.78).toInt()
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(img, (size - target) / 2, (size - target) / 2, target, target, null)
            g.dispose()
        } catch (e: Exception) {
            logger.warn("Ícone: não foi possível ler app_icon.b64 (${e.message}); a usar um ícone simples")
            val g = out.createGraphics()
            g.color = java.awt.Color(0x12, 0x20, 0x41)
            g.fillRect(size / 8, size / 8, size * 3 / 4, size * 3 / 4)
            g.dispose()
        }
        val dir = outDir.get().asFile.resolve("mipmap-xxxhdpi")
        dir.mkdirs()
        ImageIO.write(out, "png", dir.resolve("ic_launcher_foreground.png"))
    }
}

tasks.named("preBuild") {
    dependsOn(generateLauncherIcon)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // org.json real para testar a (des)serialização na JVM (o android.jar só tem stubs).
    testImplementation("org.json:json:20240303")
}
