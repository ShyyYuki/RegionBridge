plugins { java }

subprojects {
    apply(plugin = "java-library")
    group = "io.github.shyyyuki"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    if (name == "regionbridge-api" || name == "regionbridge-core") {
        apply(plugin = "maven-publish")
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") { from(components["java"]) }
            }
        }
    }
}