import java.security.MessageDigest
import java.net.URI

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop")
    js {
        browser()
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs) }
        }
    }
}

compose.desktop {
    application { mainClass = "dev.jigsaw.MainKt" }
}

compose.resources { packageOfResClass = "dev.jigsaw.resources" }

// Run after webpack and resource copying: precache every shipped runtime/resource,
// including lazy Compose fonts and Skiko payloads, without a hand-maintained list.
tasks.named("jsBrowserDistribution") {
    val workerTemplate = layout.projectDirectory.file("src/pwa/service-worker.js")
    val distribution = layout.buildDirectory.dir("dist/js/productionExecutable").get().asFile
    inputs.file(workerTemplate)
    doLast {
        val assets = distribution.walkTopDown().filter {
            it.isFile && it.name != "service-worker.js" && it.extension != "map"
        }.sortedBy { it.relativeTo(distribution).invariantSeparatorsPath }.toList()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(workerTemplate.asFile.readBytes())
        assets.forEach {
            digest.update(it.relativeTo(distribution).invariantSeparatorsPath.toByteArray())
            digest.update(it.readBytes())
        }
        val version = digest.digest().joinToString("") { "%02x".format(it) }
        val paths = assets.joinToString(",\n") {
            // URI encoding also handles spaces in future bundled resource names.
            val path = URI(null, null, it.relativeTo(distribution).invariantSeparatorsPath, null).toASCIIString()
            "\"$path\""
        }
        distribution.resolve("service-worker.js").writeText(workerTemplate.asFile.readText()
            .replace("__VERSION__", "\"$version\"")
            .replace("__ASSETS__", "[$paths]"))
    }
}
