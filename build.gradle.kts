import com.google.protobuf.gradle.id
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("net.ltgt.errorprone") version "5.1.1"
    id("com.google.protobuf") version "0.10.1"
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
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("com.pgvector:pgvector:0.1.6")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.14.2")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    // InProcessServerBuilder/ChannelBuilder nằm ở artifact riêng từ grpc-java 1.8x.
    testImplementation("io.grpc:grpc-inprocess")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    // Khớp protobuf-java do BOM của Boot quản (4.35.1), lệch là ProtobufRuntimeVersionException.
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.83.1" }
    }
    generateProtoTasks {
        // all() chạy 2 lần cho cùng một task ở plugin 0.10.x nên phải tự chặn trùng.
        all().forEach { task ->
            task.plugins {
                if (findByName("grpc") == null) {
                    id("grpc")
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // TC 2.x chỉ đọc biến env NGAY TRONG executor JVM để quyết định có khởi động Ryuk hay không.
    // Nếu env này mất giữa daemon -> executor, Ryuk sẽ được dựng lên và kill + DELETE container
    // giữa chừng khi executor bị SIGTERM hoặc có run chồng chéo (quan sát được qua journal podman).
    // Khai báo ở đây để bảo đảm mọi executor đều tắt Ryuk, không phụ thuộc shell gọi gradlew.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // config/springboot.yml (dev) cũng được nạp khi test: tắt bootstrap admin + GoTrue thật để test không ghi vào GoTrue dev.
    // System property ưu tiên hơn file import nên ghi đè được.
    systemProperty("app.bootstrap-admin.email", "")
    systemProperty("gotrue.service-role-key", "")
    // app.demo-seed bật như dev, nhưng ảnh demo là file fake — xem TestcontainersConfiguration#demoImageFetcher.
}

tasks.test {
    // OpenApiExportTest dựng riêng 1 context + ghi đè docs/openapi.json mỗi lần chạy (~40s).
    // Tắt mặc định; cần spec thì chạy ./gradlew openApiExport.
    exclude("**/OpenApiExportTest*")
}

tasks.register<Test>("openApiExport") {
    group = "verification"
    description = "Chạy riêng OpenApiExportTest để export docs/openapi.json khi cần sync spec sang FE"
    // Khai báo output để Gradle re-run khi file bị xoá (nếu không, task báo UP-TO-DATE oan)
    outputs.file(layout.projectDirectory.file("docs/openapi.json"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/OpenApiExportTest.class")
    shouldRunAfter(tasks.test)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.minhnbnt")
        option("NullAway:ExcludedClassAnnotations", "org.springframework.aot.generate.Generated")
        disable("StringConcatToTextBlock")
        // Mã sinh từ proto: NullAway/style của project không áp lên.
        excludedPaths.set(".*/build/generated/.*")
        disableWarningsInGeneratedCode.set(true)
    }
}
