plugins {
    id("java")
}

group = "tools.redstone.abstracraft"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":abstracraft-core"))
    implementation(project(":abstracraft-math"))

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}