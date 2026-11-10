plugins { id("com.gradleup.shadow") version "8.3.5" }

dependencies {
    implementation(project(":regionbridge-api"))
    implementation(project(":regionbridge-core"))
    implementation(project(":regionbridge-spigot"))
    implementation(project(":regionbridge-paper"))
    implementation(project(":regionbridge-purpur"))
    implementation(project(":regionbridge-folia"))
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("RegionBridge-${project.version}.jar")
}
tasks.build { dependsOn(tasks.shadowJar) }
