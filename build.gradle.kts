plugins {
    java
}

import java.util.Properties

group = "io.github.ciaassured"
version = gitVersion()

fun gitVersion(): String {
    val described = providers.exec {
        commandLine("git", "describe", "--tags", "--dirty", "--always")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

    if (described.isEmpty()) {
        return "0.0.0-dev"
    }

    val dirty = described.endsWith("-dirty")
    val clean = described.removeSuffix("-dirty")

    val exactTag = Regex("""^v?(\d+\.\d+\.\d+)$""").matchEntire(clean)
    if (exactTag != null) {
        return withDirtySuffix(exactTag.groupValues[1], dirty)
    }

    val commitsAfterTag = Regex("""^v?(\d+\.\d+\.\d+)-(\d+)-g([0-9a-f]+)$""").matchEntire(clean)
    if (commitsAfterTag != null) {
        val baseVersion = commitsAfterTag.groupValues[1]
        val commits = commitsAfterTag.groupValues[2]
        val sha = commitsAfterTag.groupValues[3]
        return withDirtySuffix("$baseVersion-dev.$commits+g$sha", dirty)
    }

    return withDirtySuffix("0.0.0-dev+g$clean", dirty)
}

fun withDirtySuffix(version: String, dirty: Boolean): String {
    if (!dirty) return version
    return if (version.contains("+")) "$version.dirty" else "$version+dirty"
}

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

tasks.register<Copy>("deployPlugin") {
    group = "deployment"
    description = "Builds YRush, removes old deployed YRush jars, and copies the plugin jar to the local Paper plugins folder."

    dependsOn(tasks.named("build"))

    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(providers.provider { paperPluginsDir() })

    doFirst {
        val pluginsDir = paperPluginsDir()
        pluginsDir.listFiles { file ->
            file.isFile && file.name.matches(Regex("(?i)yrush.*\\.jar"))
        }?.forEach { file ->
            if (!file.delete()) {
                throw GradleException("Could not delete old YRush jar: ${file.absolutePath}")
            }
            logger.lifecycle("Deleted old YRush jar: ${file.absolutePath}")
        }
    }
}

tasks.register<Exec>("runPaperServer") {
    group = "deployment"
    description = "Starts the configured local Paper server."

    standardInput = System.`in`

    doFirst {
        workingDir = paperServerDir()
        commandLine("java", "-jar", paperServerJar().absolutePath, "nogui")
    }
}

tasks.register("deployAndRun") {
    group = "deployment"
    description = "Builds YRush, deploys it, then starts Paper."

    dependsOn(tasks.named("deployPlugin"))
    finalizedBy(tasks.named("runPaperServer"))
}
