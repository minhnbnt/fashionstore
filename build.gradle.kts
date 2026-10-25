import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("org.springframework.boot.aot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("net.ltgt.errorprone") version "5.1.1"
}

group = "com.minhnbnt"
version = "0.0.1-SNAPSHOT"

java.toolchain {
    languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("software.amazon.awssdk:s3:2.55.7")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.14.2")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // config/springboot.yml (dev) cũng được nạp khi test: tắt bootstrap admin + GoTrue thật để test không ghi vào GoTrue dev.
    // System property ưu tiên hơn file import nên ghi đè được.
    systemProperty("app.bootstrap-admin.email", "")
    systemProperty("gotrue.service-role-key", "")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.minhnbnt")
        option("NullAway:ExcludedClassAnnotations", "org.springframework.aot.generate.Generated")
        disable("StringConcatToTextBlock")
    }
}

tasks.named<JavaCompile>("compileAotJava") {
    options.errorprone.enabled = false
}

tasks.named<JavaCompile>("compileAotTestJava") {
    options.errorprone.enabled = false
}
