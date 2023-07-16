plugins {
    id("java")
}

group = "tools.redstone.abstracraft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Annotation processing
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    implementation(project(":annotation-processor"))
    annotationProcessor(project(":annotation-processor"))

    implementation("tools.redstone.abstracraft:helpers")
    implementation("tools.redstone.abstracraft:math")
}

tasks.test {
    useJUnitPlatform()
}
