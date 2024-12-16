import java.io.File
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties

gradle.projectsEvaluated {
    println("unblockt:java:home:" + System.getProperty("java.home"))
    allprojects {
        val kotlinExtension = project.extensions.findByName("kotlin") ?: return@allprojects
        val instance = multiplatformImportRef(project)

        println("unblockt:project:${name}")
        println("unblockt:project-directory:${path}")
        println("unblockt:project-build-file:$buildFile")

        val sourceSetsRef = kotlinExtension.sourceSetsRef
        val commonMainSourceSet = sourceSetsRef.findByName("commonMain")

        (listOfNotNull(commonMainSourceSet) + sourceSetsRef).forEach { kss ->
            val sourceSetName = kss.nameRef
            println("unblockt:source-set:$sourceSetName")
            val compilations = kss.compilationsRef
            val allPlatforms = mutableSetOf<Any>()
            compilations.forEach { compilation ->
                val platform = compilation.platformTypeRef
                allPlatforms.add(platform)
                println("unblockt:source-set:platform:${platform}")
            }
            val deps: Set<Any> = instance.resolveDependenciesRef(sourceSetName)
            printDependencies(deps)
            if (allPlatforms.any { it.isAndroid }) {
                compilations.forEach { compilation ->
                    val compileDepenciesRef = compilation.compileDependenciesRef
                    if (compileDepenciesRef is Configuration && compileDepenciesRef.isCanBeResolved) {
                        if (compileDepenciesRef.name.contains("implementation", true)) {
                            compileDepenciesRef.incoming.dependencies.forEach { dep: Dependency ->
                                when(dep) {
                                    is ProjectDependency -> {
                                        val project = dep.dependencyProject
                                        println("unblockt:dependency:project:${project.name}")
                                        println("unblockt:dependency:project:path:${project.path}")
                                    }
                                    else -> {
                                    }
                                }
                            }
                        }
                    }
                }
                val extensionVersion = kotlinExtension.extensionVersion
                if (extensionVersion == "2.1.0") {
                    val binaryDependenciesResolverRef = binaryDependenciesResolverRef(this)
                    val binaryDeps = binaryDependenciesResolverRef.resolveRef(kss)
                    printDependencies(binaryDeps)
                }
            }
        }
    }
}

fun printDependencies(deps: Set<Any>) {
    deps.forEach { dep ->
        when {
            dep.isSourceDepRef -> {
                val sourceCoordinates = dep.coordinatesRef
                println("unblockt:dependency:source:${sourceCoordinates.projectCoordinatesRef.projectNameRef}")
                println("unblockt:dependency:source-set:${sourceCoordinates.sourceSetNameRef}")
            }

            dep.isBinaryDepRef -> {
                val coordinates = dep.coordinatesRef
                val module = coordinates.moduleRef
                println("unblockt:dependency:artifact:$module")
                dep.classpathRef.forEach { cp: File ->
                    println("unblockt:dependency:artifact:path:${cp.path}")
                }
            }

            dep.isProjectDepRef -> {
                val coordinates = dep.coordinatesRef
                println("unblockt:dependency:project:${coordinates.projectNameRef}")
                println("unblockt:dependency:project:path:${coordinates.projectPathRef}")
            }

            else -> {}
        }
    }
}

val Any.extensionVersion: String?
    get() {
        val prop = this::class.memberProperties.find { it.name == "compilerVersion" } as? KProperty1<Any, *>
        val call = prop?.call(this) as? Property<String>
        return call?.get()
    }

val Any.compileDependenciesRef: FileCollection
    get() {
        val prop = this::class.memberProperties.find { it.name == "compileDependencyFiles" } as KProperty1<Any, *>
        return prop.call(this) as FileCollection
    }

val Any.compileConfigurationNameRef: String
    get() {
        val prop = this::class.memberProperties.find { it.name == "compileDependencyConfigurationName" } as KProperty1<Any, *>
        return prop.call(this) as String
    }

val Any.coordinatesRef: Any
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "coordinates" } as KProperty1<Any, *>
        return prop.call(this) as Any
    }

val Any.classpathRef: Collection<File>
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "classpath" } as KProperty1<Any, *>
        return prop.call(this) as Collection<File>
    }

val Any.moduleRef: String
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "module" } as KProperty1<Any, *>
        return prop.call(this) as String
    }

val Any.projectPathRef: Any
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "projectPath" } as KProperty1<Any, *>
        return prop.call(this) as Any
    }

val Any.sourceSetNameRef: String
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "sourceSetName" } as KProperty1<Any, *>
        return prop.call(this) as String
    }

val Any.projectNameRef: Any
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "projectName" } as KProperty1<Any, *>
        return prop.call(this) as Any
    }

val Any.projectRef: Project
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "project" } as KProperty1<Any, *>
        return prop.call(this) as Project
    }

val Any.platformTypeRef: Any
    get() {
        val prop = this::class.memberProperties.find { it.name == "platformType" } as KProperty1<Any, *>
        return prop.call(this) as Any
    }

val Any.projectCoordinatesRef: Any
    get() {
        val prop = this::class.declaredMemberProperties.find { it.name == "project" } as KProperty1<Any, *>
        return prop.call(this) as Any
    }

val Any.sourceSetsRef: NamedDomainObjectContainer<Any>
    get() {
        val prop = this::class.memberProperties.find { it.name == "sourceSets" } as KProperty1<Any, *>
        return prop.call(this) as NamedDomainObjectContainer<Any>
    }

val Any.nameRef: String
    get() {
        val prop = this::class.declaredFunctions.find { it.name == "getName" } as KFunction<*>
        return prop.call(this) as String
    }

val Any.dependsOnRef: Collection<Any>
    get() {
        val prop = this::class.memberProperties.find { it.name == "dependsOn" } as KProperty1<Any, *>
        return prop.call(this) as Collection<Any>
    }

val Any.compilationsRef: Collection<Any>
    get() {
        val prop = this::class.memberProperties.find { it.name == "compilations" } as KProperty1<Any, *>
        return prop.call(this) as Collection<Any>
    }

val Any.isAndroid: Boolean
    get() = toString() == "androidJvm"

fun multiplatformImportRef(project: Project): Any {
    val extension = project.extensions.findByName("kotlin")!!
    val clazz = extension::class.java.classLoader.loadClass("org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport")
    val instance = clazz.declaredMethods.find { it.name == "instance" }!!
    return instance.invoke(clazz, project) as Any
}

fun binaryDependenciesResolverRef(project: Project): Any {
    val extension = project.extensions.findByName("kotlin")!!
    val clazz = extension::class.java.classLoader.loadClass("org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeJvmAndAndroidPlatformDependencyResolverKt")
    val function = clazz.declaredMethods.find { it.name == "IdeJvmAndAndroidPlatformBinaryDependencyResolver" } as Method
    function.isAccessible = true
    return function.invoke(clazz, project) as Any
}

fun Any.resolveDependenciesRef(name: String): Set<Any> {
    val func = this::class.declaredFunctions.find { it.name == "resolveDependencies" } as KFunction<*>
    return func.call(this, name) as Set<Any>
}

fun Any.resolveRef(kotlinSourceSet: Any): Set<Any> {
    val func = this::class.declaredFunctions.find { it.name == "resolve" } as KFunction<*>
    return func.call(this, kotlinSourceSet) as Set<Any>
}

val Any.isSourceDepRef: Boolean
    get() {
        return this::class.simpleName == "IdeaKotlinSourceDependency"
    }

val Any.isProjectDepRef: Boolean
    get() {
        return this::class.simpleName == "IdeaKotlinProjectArtifactDependency"
    }

val Any.isBinaryDepRef: Boolean
    get() {
        return this::class.simpleName == "IdeaKotlinResolvedBinaryDependency"
    }
