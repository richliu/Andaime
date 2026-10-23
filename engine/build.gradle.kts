// Pure Kotlin: no Android APIs, so the table logic runs as plain JVM unit tests
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Real PIME tables (kept in third_party, not shipped in the app), the bundled OpenCC tables and the symbol table
    systemProperty("andaime.tables", rootProject.file("third_party/pime").absolutePath)
    systemProperty("andaime.opencc", rootProject.file("app/src/main/assets/opencc").absolutePath)
    systemProperty("andaime.symbols", rootProject.file("app/src/main/assets/dayi-symbols.txt").absolutePath)
}
