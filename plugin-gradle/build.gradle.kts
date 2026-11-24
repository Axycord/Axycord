plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "mmaarij.axycord"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("axycordPlugin") {
            id = "mmaarij.axycord.plugin"
            implementationClass = "mmaarij.axycord.gradle.AxycordPluginGradlePlugin"
        }
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
}

publishing {
    repositories {
        // Pushed to this repository's public `maven` branch by .github/workflows/plugin-sdk.yml.
        maven {
            name = "Public"
            url = uri(rootProject.layout.buildDirectory.dir("public-maven"))
        }
    }
}
