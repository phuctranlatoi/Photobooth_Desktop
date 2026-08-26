plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.phuctran.photobooth"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.github.sarxos:webcam-capture:0.3.12")
    implementation("net.java.dev.jna:jna:4.5.2")
    implementation("net.java.dev.jna:jna-platform:4.5.2")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("com.bitplan:edsdk4j:0.0.1")
    implementation("vn.payos:payos-java:2.0.1")
    implementation("com.google.firebase:firebase-admin:9.3.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

compose.desktop {
    application {
        mainClass = (project.findProperty("mainClass") as? String) ?: "com.phuctran.photobooth.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "PrettyBoothDesktop"
            packageVersion = "1.0.0"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Pretty Booth"
                upgradeUuid = "ce637e69-93f0-41d6-8092-8e4635c729ad"
            }
        }
    }
}


tasks.register<JavaExec>("runTest") {
    group = "application"
    description = "Run FrameEngineTest"
    mainClass.set("com.phuctran.photobooth.desktop.engine.FrameEngineTestKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runCalculator") {
    group = "application"
    description = "Run Layout Calculator Tool"
    mainClass.set("com.phuctran.photobooth.desktop.engine.LayoutCalculatorToolKt")
    classpath = sourceSets["main"].runtimeClasspath
    
    // Pass CLI arguments to the Java application
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}
