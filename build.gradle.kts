import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()
}

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.41"
    id("com.gradle.plugin-publish") version "0.10.1"
    id("idea")
}

gradlePlugin {
    plugins {
        create("githubRelease") {
            id = "com.jlessing.github-release"
            displayName = "GitHub Release Plugin"
            description = "Gradle Plugin written in Kotlin for creating GitHub Releases"
            implementationClass = "com.jlessing.gradle.plugins.github.release.GitHubReleasePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/jlessing-git/github-release-gradle-plugin"
    vcsUrl = "https://github.com/jlessing-git/github-release-test-gradle-plugin.git"
    tags = listOf("git", "github", "github-release", "release", "git-release")
}

group = "com.jlessing.gradle.plugins.github.release"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:2.1.6.RELEASE")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.5.0.201909110433-r")
}