import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

val loomClassLoader = plugins.getPlugin("dev.architectury.loom-no-remap").javaClass.classLoader
val runTaskType = loomClassLoader.loadClass("net.fabricmc.loom.task.AbstractRunTask").asSubclass(Task::class.java)
val serviceFactoryType = loomClassLoader.loadClass("net.fabricmc.loom.util.service.ScopedServiceFactory")

// Loom keeps the run task inputs below protected so only subclasses can read them, and Groovy's
// dynamic dispatch used to bypass that. They are resolved through the run task type that declares
// them and read or written through the Property API, so no value needs a cast to a generic type.
fun loomRunProperty(task: Task, getter: String): Any {
    val accessor = runTaskType.getDeclaredMethod(getter).apply { isAccessible = true }
    return accessor.invoke(task) ?: throw GradleException("Missing Loom run configuration member: $getter")
}

fun loomRunValue(task: Task, getter: String): Any? {
    val property = loomRunProperty(task, getter)
    return property.javaClass.getMethod("get").invoke(property)
}

fun setLoomRunValue(task: Task, getter: String, value: Any) {
    val property = loomRunProperty(task, getter)
    val setter = property.javaClass.methods.first {
        it.name == "set" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(value)
    }
    setter.invoke(property, value)
}

fun argumentsOf(value: Any?): List<String> = (value as? Iterable<*>)?.map { it.toString() } ?: emptyList()

fun variablesOf(value: Any?): LinkedHashMap<String, Any> {
    val variables = LinkedHashMap<String, Any>()
    (value as? Map<*, *>)?.forEach { key, variable -> variables[key.toString()] = variable ?: return@forEach }
    return variables
}

data class DevelopmentSnapshot(val directory: File, val replacements: Map<String, String>)

val snapshots = mutableMapOf<String, DevelopmentSnapshot>()

// Running clients must never load or watch archives rewritten by a later build.
val snapshotRuntime: (Task) -> DevelopmentSnapshot = { runTask ->
    val run = runTask as JavaExec
    val session = rootProject.file(".local/dev-sessions/${project.name}-${UUID.randomUUID()}")
    val root = rootProject.projectDir.toPath().toAbsolutePath().normalize()
    val sessionRoot = session.toPath().toAbsolutePath().normalize()
    if (!sessionRoot.startsWith(root.resolve(".local/dev-sessions"))) throw GradleException("Invalid development snapshot directory")
    Files.createDirectories(sessionRoot)
    val originals = run.classpath.files.toList()
    val candidates = LinkedHashSet(originals)
    listOf(project, project(":common")).forEach { module ->
        val moduleSourceSets = module.extensions.getByType(SourceSetContainer::class.java)
        listOf("main", "smokeVerification").forEach { sourceSetName ->
            val output = moduleSourceSets.findByName(sourceSetName)?.output
            if (output != null) candidates.addAll(output.files)
        }
    }
    val replacements = LinkedHashMap<String, String>()
    candidates.forEach { source ->
        val path = source.toPath().toAbsolutePath().normalize()
        val mutable = listOf(project.layout.buildDirectory.get().asFile, project(":common").layout.buildDirectory.get().asFile)
            .any { path.startsWith(it.toPath().toAbsolutePath().normalize()) }
        if (mutable && source.exists()) {
            val target = sessionRoot.resolve(root.relativize(path))
            if (source.isDirectory) {
                copy { from(source); into(target.toFile()) }
            } else {
                Files.createDirectories(target.parent)
                Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
            }
            replacements[path.toString()] = target.toString()
        }
    }
    fun rewrite(value: String): String = replacements.entries.fold(value) { current, (originalSource, target) ->
        current.replace(originalSource, target).replace(originalSource.replace('\\', '/'), target.replace('\\', '/'))
    }
    fun rewriteArguments(arguments: Iterable<String>): List<String> = arguments.map { argument ->
        val text = argument.toString()
        val prefix = listOf("-Darchitectury.runtime.transformer=", "-Darchitectury.properties=",
            "-Darchitectury.main.class=", "-Dfabric.dli.config=").firstOrNull { text.startsWith(it) }
            ?: return@map rewrite(text)
        val sourceFile = File(text.substring(prefix.length).replace("@@", " "))
        if (!sourceFile.isFile) throw GradleException("Missing development launch file: $sourceFile")
        val target = File(session, "launch/${sourceFile.name}")
        target.parentFile.mkdirs()
        when (prefix) {
            "-Darchitectury.properties=" -> {
                val properties = Properties()
                sourceFile.inputStream().use { properties.load(it) }
                properties.stringPropertyNames().forEach { key -> properties.setProperty(key, rewrite(properties.getProperty(key))) }
                target.outputStream().use { properties.store(it, "Isolated Architectury development runtime") }
            }
            "-Darchitectury.main.class=" -> {
                var mainClass = sourceFile.readText(Charsets.UTF_8).trim()
                // Architectury shares this file between inherited Loom run configurations.
                // Configuring both runs can replace DLI with the transformer itself.
                if (mainClass == "dev.architectury.transformer.TransformerRuntime") {
                    if (argumentsOf(loomRunValue(run, "getInternalJvmArgs")).none { it.startsWith("-Dfabric.dli.config=") })
                        throw GradleException("Recursive Architectury entrypoint without a Loom launch configuration")
                    mainClass = "net.fabricmc.devlaunchinjector.Main"
                }
                target.writeText(mainClass, Charsets.UTF_8)
            }
            else -> target.writeText(rewrite(sourceFile.readText(Charsets.UTF_8)), Charsets.UTF_8)
        }
        prefix + target.absolutePath
    }
    run.classpath = files(originals.map { File(rewrite(it.absolutePath)) })
    setLoomRunValue(run, "getInternalJvmArgs", rewriteArguments(argumentsOf(loomRunValue(run, "getInternalJvmArgs"))))
    run.jvmArgs = rewriteArguments(run.jvmArgs?.map { it.toString() } ?: emptyList())
    var launchEnvironment = variablesOf(loomRunValue(run, "getInternalEnvironmentVars"))
    val modClasses = launchEnvironment["MOD_CLASSES"] ?: run.environment["MOD_CLASSES"]
    if (modClasses == "{source_roots}") {
        // Loom applies its internal environment at exec(), after the JavaExec environment.
        // Resolve and replace that input so it cannot restore mutable source directories.
        val services = serviceFactoryType.getDeclaredConstructor().newInstance()
        try {
            val getOrNull = serviceFactoryType.methods.first { it.name == "getOrNull" }
            val sourceGroups = getOrNull.invoke(services, loomRunProperty(run, "getModClassesOptions"))
            if (sourceGroups != null) {
                launchEnvironment["MOD_CLASSES"] = sourceGroups.javaClass
                    .getMethod("getModClasses", String::class.java)
                    .invoke(sourceGroups, loomRunValue(run, "getRunConfigName").toString())
            }
        } finally {
            AutoCloseable::class.java.cast(services).close()
        }
    }
    launchEnvironment = launchEnvironment.entries
        .associateTo(LinkedHashMap<String, Any>()) { (key, value) -> key to rewrite(value.toString()) }
    setLoomRunValue(run, "getInternalEnvironmentVars", launchEnvironment)
    run.environment.keys.toList().forEach { key -> run.environment(key, rewrite(run.environment[key].toString())) }
    logger.lifecycle("[Pupper Client] Isolated development runtime: ${root.relativize(sessionRoot)}")
    DevelopmentSnapshot(session, replacements)
}

afterEvaluate {
    tasks.withType(runTaskType).forEach { run ->
        // Prepare after compilation, before JavaExec finalizes its input file collection.
        val preparation = tasks.register("${run.name}Snapshot") {
            group = "internal"
            dependsOn(Callable {
                run.taskDependencies.getDependencies(run).filter { it.name != "${run.name}Snapshot" }
            })
            doLast { snapshots[run.name] = snapshotRuntime(run) }
        }
        run.dependsOn(preparation)
    }
}

tasks.register("verifyDevRuntimeSnapshot") {
    group = "verification"
    description = "Checks isolation of development classes, Architectury inputs and loader source groups."
    val runClient = tasks.named<JavaExec>("runClient")
    dependsOn(Callable { tasks.named("runClientSnapshot") })
    doLast {
        val task = runClient.get()
        val snapshot = snapshots[task.name] ?: throw GradleException("Development runtime snapshot was not created")
        if (snapshot.replacements.isEmpty()) throw GradleException("No development outputs were isolated")
        val internalClasspath = FileCollection::class.java.cast(loomRunProperty(task, "getInternalClasspath"))
        internalClasspath.files.forEach { entry ->
            if (snapshot.replacements.containsKey(entry.absolutePath)) throw GradleException("Mutable classpath entry retained: ${entry}")
        }
        val properties = Properties()
        File(snapshot.directory, "launch/.properties").inputStream().use { properties.load(it) }
        val internalEnvironmentVars = variablesOf(loomRunValue(task, "getInternalEnvironmentVars"))
        val launchInputs = mutableListOf<Any?>(
            File(snapshot.directory, "launch/.transforms").readText(Charsets.UTF_8),
            File(snapshot.directory, "launch/launch.cfg").readText(Charsets.UTF_8),
            task.environment.toString(),
            internalEnvironmentVars.toString(),
        )
        if (internalEnvironmentVars["MOD_CLASSES"] == "{source_roots}")
            throw GradleException("Unresolved loader source groups retained")
        launchInputs.addAll(properties.values)
        if (File(snapshot.directory, "launch/.main_class").readText(Charsets.UTF_8).trim()
                == "dev.architectury.transformer.TransformerRuntime")
            throw GradleException("Recursive Architectury entrypoint retained")
        snapshot.replacements.keys.forEach { original ->
            if (launchInputs.any { it != null && (it.toString().contains(original)
                        || it.toString().contains(original.replace('\\', '/'))) })
                throw GradleException("Mutable development launch input retained: ${original}")
        }
        logger.lifecycle("[Pupper Client] Verified ${snapshot.replacements.size} isolated development outputs for ${project.name}.")
    }
}