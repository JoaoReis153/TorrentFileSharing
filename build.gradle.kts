plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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