plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "ru.ovenquill"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.3.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.3.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.yaml:snakeyaml:2.5")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.yaml.snakeyaml", "ru.ovenquill.stream.lib.snakeyaml")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

