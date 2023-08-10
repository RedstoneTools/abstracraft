plugins {
    id("java")
}

group = "tools.redstone.abstracraft"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
<<<<<<< HEAD:abstracraft-abstractions/build.gradle.kts
    implementation(project(":abstracraft-core"))
    implementation(project(":abstracraft-math"))
=======
    implementation("tools.redstone.abstracraft:core")
    implementation("tools.redstone.abstracraft:math")
>>>>>>> f4e84150e27ff4f80165c031b00e40eec13f9a91:abstractions/build.gradle.kts

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}