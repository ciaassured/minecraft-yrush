plugins {
    java
}

import java.util.Properties

group = "io.github.ciaassured"
version = "1.0.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

val localProperties = Properties()
val localPropertiesFile = layout.projectDirectory.file("local.properties").asFile
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

fun localProperty(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

fun paperServerDir(): File {
    val value = localProperty("paperServerDir")
        ?: throw GradleException(
            "Missing paperServerDir in local.properties. " +
                "Create local.properties with paperServerDir=/absolute/path/to/paper-server"
        )

    if (value.startsWith("~")) {
        throw GradleException("paperServerDir must be an absolute path. Do not use '~': $value")
    }

    val dir = file(value)
    if (!dir.isDirectory) {
        throw GradleException("paperServerDir does not exist or is not a directory: ${dir.absolutePath}")
    }
    return dir
}

fun paperPluginsDir(): File {
    val explicit = localProperty("paperServerPluginsDir")
    if (explicit != null) {
        if (explicit.startsWith("~")) {
            throw GradleException("paperServerPluginsDir must be an absolute path. Do not use '~': $explicit")
        }
        return file(explicit)
    }
    return paperServerDir().resolve("plugins")
}

fun yrushGeneratedConfigFile(): File = paperPluginsDir().resolve("YRush").resolve("config.yml")

fun paperServerJar(): File {
    val serverDir = paperServerDir()
    val explicit = localProperty("paperServerJar")
    if (explicit != null) {
        val jar = file(explicit).takeIf { it.isAbsolute } ?: serverDir.resolve(explicit)
        if (!jar.isFile) {
            throw GradleException("paperServerJar does not exist: ${jar.absolutePath}")
        }
        return jar
    }

    val jars = serverDir.listFiles { file ->
        file.isFile && file.name.matches(Regex("(?i)paper.*\\.jar"))
    }?.sortedBy { it.name }.orEmpty()

    return when (jars.size) {
        0 -> throw GradleException(
            "Could not infer Paper server jar in ${serverDir.absolutePath}. " +
                "Add paperServerJar=paper-x.y.z.jar to local.properties."
        )
        1 -> jars.single()
        else -> throw GradleException(
            "Multiple Paper jars found in ${serverDir.absolutePath}: ${jars.joinToString { it.name }}. " +
                "Add paperServerJar=<jar name> to local.properties."
        )
    }
}

tasks.register("deleteGeneratedPluginConfig") {
    group = "deployment"
    description = "Deletes the generated YRush config so bundled config changes are picked up locally."

    doLast {
        val configFile = yrushGeneratedConfigFile()
        if (configFile.isFile) {
            if (!configFile.delete()) {
                throw GradleException("Could not delete generated YRush config: ${configFile.absolutePath}")
            }
            logger.lifecycle("Deleted generated YRush config: ${configFile.absolutePath}")
        }
    }
}

tasks.register<Copy>("deployPlugin") {
    group = "deployment"
    description = "Builds YRush, refreshes the generated config, and copies the plugin jar to the local Paper plugins folder."

    dependsOn(tasks.named("build"), tasks.named("deleteGeneratedPluginConfig"))

    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(providers.provider { paperPluginsDir() })
}

tasks.register<Exec>("runPaperServer") {
    group = "deployment"
    description = "Refreshes the generated config and starts the configured local Paper server."

    dependsOn(tasks.named("deleteGeneratedPluginConfig"))

    standardInput = System.`in`

    doFirst {
        workingDir = paperServerDir()
        commandLine("java", "-jar", paperServerJar().absolutePath, "nogui")
    }
}

tasks.register("deployAndRun") {
    group = "deployment"
    description = "Builds YRush, refreshes the generated config, deploys it, then starts Paper."

    dependsOn(tasks.named("deployPlugin"))
    finalizedBy(tasks.named("runPaperServer"))
}
