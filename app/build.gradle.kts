import java.awt.Color
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

    // Assinatura de release: só se o CI (ou o utilizador) definir KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD.
    // Sem essas variáveis o release sai sem assinar (app-release-unsigned.apk).
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() && File(it).exists() }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    // O lint de release não deve impedir a geração dos pacotes.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // O primeiro plano do ícone do app é gerado em build/generated/icon-res a partir de icon/app_icon_*.b64.
    sourceSets {
        getByName("main") {
            res.srcDir(layout.buildDirectory.dir("generated/icon-res"))
            // Texturas da AnimBrush (stamps.abpack), copiadas de third_party/AnimBrush/assets se existirem.
            assets.srcDir(layout.buildDirectory.dir("generated/animbrush-assets"))
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
}

// Ícone: junta as partes em base64 (icon/app_icon_01.b64, 02, …) e descodifica o JPEG; escreve o PNG do primeiro
// plano do ícone adaptativo, com a imagem a 78% da tela para não ser cortada pela máscara do launcher.
// Se faltar ou estiver danificado, gera-se um quadrado liso para o build nunca falhar por isso.
val generateLauncherIcon by tasks.registering {
    val parts = fileTree("icon") { include("app_icon_*.b64") }
    val outDir = layout.buildDirectory.dir("generated/icon-res")
    inputs.files(parts)
    outputs.dir(outDir)
    doLast {
        System.setProperty("java.awt.headless", "true")
        val size = 432
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        try {
            val text = parts.files.sortedBy { it.name }.joinToString("") { it.readText().trim() }
            val bytes = Base64.getMimeDecoder().decode(text)
            val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalStateException("imagem inválida")
            val target = (size * 0.78).toInt()
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(img, (size - target) / 2, (size - target) / 2, target, target, null)
            g.dispose()
        } catch (e: Exception) {
            logger.warn("Ícone: não foi possível ler icon/app_icon_*.b64 (${e.message}); a usar um ícone simples")
            val g = out.createGraphics()
            g.color = Color(0x12, 0x20, 0x41)
            g.fillRect(size / 8, size / 8, size * 3 / 4, size * 3 / 4)
            g.dispose()
        }
        val dir = outDir.get().asFile.resolve("mipmap-xxxhdpi")
        dir.mkdirs()
        ImageIO.write(out, "png", dir.resolve("ic_launcher_foreground.png"))
    }
}

// AnimBrush.zip na raiz do repositório: extrai para third_party/AnimBrush (sem a pasta de topo, se existir) quando
// a pasta ainda não está lá. Corre antes da configuração do CMake e do copyAnimBrushPack.
val extractAnimBrush by tasks.registering(Copy::class) {
    val zip = rootProject.layout.projectDirectory.file("AnimBrush.zip").asFile
    val target = rootProject.layout.projectDirectory.dir("third_party/AnimBrush").asFile
    onlyIf { zip.exists() && !File(target, "include").exists() }
    from({ zipTree(zip) })
    into(target)
    includeEmptyDirs = false
    eachFile {
        val parts = relativePath.segments
        if (parts.size > 1 && parts[0] == "AnimBrush") {
            relativePath = RelativePath(true, *parts.drop(1).toTypedArray())
        }
    }
    exclude("**/__MACOSX/**", "**/.DS_Store")
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name.startsWith("generateJsonModel")) {
        dependsOn(extractAnimBrush)
    }
}

// AnimBrush: só o pack de texturas vai para os assets (os PNG originais ficam de fora). Sem a pasta, não faz nada.
val copyAnimBrushPack by tasks.registering(Copy::class) {
    val pack = rootProject.layout.projectDirectory.file("third_party/AnimBrush/assets/stamps.abpack").asFile
    from(pack)
    into(layout.buildDirectory.dir("generated/animbrush-assets"))
    dependsOn(extractAnimBrush)
    onlyIf { pack.exists() }
}

tasks.named("preBuild") {
    dependsOn(generateLauncherIcon)
    dependsOn(copyAnimBrushPack)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
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
