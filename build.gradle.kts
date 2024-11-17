plugins {
    alias(libs.plugins.jvm) // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.springboot)
    alias(libs.plugins.springbootManagement)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.palantir)
}

group = "org.agrfesta.btm"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.arrow.core)
    implementation(libs.jtokkit)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgress)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit")
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation(libs.ktor.client.mock)
}

docker {
    name = "agrfesta/be-the-master:${version}"
    uri("agrfesta/be-the-master:${version}")
    tag("name", "be-the-master")
    buildArgs(mapOf("name" to "be-the-master"))
    copySpec.from("build").into("build")
    pull(true)
    setDockerfile(file("Dockerfile"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
