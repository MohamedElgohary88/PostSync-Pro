import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "com.postsyncpro"
version = "2.0.0"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Ktor Client (Networking + AI API)
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-logging:2.3.12")

    // Ktor Server (Module 6: Smart Mock Server)
    // Ktor 2.3.12 pins an old Netty line; keep the compatible 4.1 API while
    // forcing the security-patched release across every Netty module.
    implementation(platform("io.netty:netty-bom:4.1.136.Final"))
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Safe OpenAPI YAML parsing + linear-time JSON-schema pattern checks
    implementation("org.yaml:snakeyaml:2.6")
    implementation("com.google.re2j:re2j:1.8")

    // Koin Dependency Injection
    implementation("io.insert-koin:koin-core:3.5.6")

    // PDF Generation (Module 5: QA Reports)
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Data Faker (Module 6: Mock Server realistic data)
    implementation("net.datafaker:datafaker:2.4.2")

    // SQLite Database (Module 10: Regression Reporter)
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.56.0")

    // File Dialogs
    implementation("ca.weblite:java-objc-bridge:1.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}

compose.desktop {
    application {
        mainClass = "com.postsyncpro.MainKt"

        nativeDistributions {
            modules("java.instrument", "java.naming", "java.sql", "jdk.management", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PostSync Pro"
            packageVersion = "2.0.0"
            description = "AI-Powered QA Automation Desktop Application"
            vendor = "PostSync Pro"

            macOS {
                bundleID = "com.postsyncpro"
            }

            windows {
                menuGroup = "PostSync Pro"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            linux {
                packageName = "postsync-pro"
            }
        }
    }
}
