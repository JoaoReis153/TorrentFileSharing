plugins {
    application
    id("com.diffplug.spotless") version "8.10.4"
}

repositories {
    mavenCentral() // Spotless downloads the formatter from here
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

spotless {
    java {
        target("Code/src/**/*.java")
        googleJavaFormat().aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

sourceSets.main {
    java.setSrcDirs(listOf("Code/src"))
}

application {
    mainClass = "Tests.Main"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = rootDir
}