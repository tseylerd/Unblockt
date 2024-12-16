import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion = "2.1.0"
val intellijVersion = "241.194"

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.0.10"
    application
}

group = "tse.unblockt.language.server"
version = "0.0.1"

repositories {
    mavenCentral()

    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    maven(url = "https://cache-redirector.jetbrains.com/intellij-third-party-dependencies")
}

application {
    mainClass.set("tse.unblockt.ls.server.EntrypointKt")
    applicationDefaultJvmArgs = listOf("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--enable-preview")
}
val jarDirectory = file("$rootDir/server/build/jars")

tasks.jar {
    manifest {
        attributes["Main-Class"] = "tse.unblockt.ls.server.EntrypointKt"
        archiveFileName.set("server.jar")
        destinationDirectory.set(jarDirectory)
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks {
    register<Copy>("gatherDependencies") {
        from(configurations.compileClasspath).into(jarDirectory)
    }
}

fun modifyClasspath(collection: FileCollection): FileCollection {
    val utilBase: List<File> = collection.files.filter {
        it.name.startsWith("util-base") ||
                it.name.startsWith("core-impl") ||
                it.name.startsWith("extensions") ||
                it.name.startsWith("core")
    }
    val files = files(*utilBase.toTypedArray())
    val collectionWithoutFile = collection - files
    return files + collectionWithoutFile
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(freeCompilerArgs.get() + listOf("-Xcontext-receivers"))
    }
}
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<CreateStartScripts> {
    doLast {
        val text = unixScript.readText()
        val classpathLine = text.lines().find { it.startsWith("CLASSPATH") }!!
        val modifiedClasspathLine = classpathLine.replace(
            "\$APP_HOME/lib/server.jar",
            "\$APP_HOME/lib/server.jar:\$APP_HOME/lib/util-base-$intellijVersion.jar:\$APP_HOME/lib/core-impl-$intellijVersion.jar"
        )
        unixScript.writeText(text.replace(classpathLine, modifiedClasspathLine))
    }
}

afterEvaluate {
    val sourceSets = listOf(sourceSets.test.get(), sourceSets.main.get())
    sourceSets.forEach { sourceSet ->
        sourceSet.runtimeClasspath = modifyClasspath(sourceSet.runtimeClasspath)
    }
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.1")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")
    implementation("org.mapdb:mapdb:3.1.0")
    implementation("org.apache.logging.log4j:log4j-iostreams:2.24.1")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation(kotlin("stdlib"))
    implementation("org.gradle:gradle-tooling-api:7.3-20210825160000+0000")
    listOf(
        "com.jetbrains.intellij.platform:util",
        "com.jetbrains.intellij.platform:util-text-matching",
        "com.jetbrains.intellij.platform:util-base",
        "com.jetbrains.intellij.platform:core",
        "com.jetbrains.intellij.platform:core-impl",
        "com.jetbrains.intellij.platform:util-diff",
        "com.jetbrains.intellij.platform:extensions",
    ).forEach {
        implementation("$it:$intellijVersion") { isTransitive = false }
    }

    listOf(
        "org.jetbrains.kotlin:high-level-api-fir-for-ide",
        "org.jetbrains.kotlin:high-level-api-for-ide",
        "org.jetbrains.kotlin:low-level-api-fir-for-ide",
        "org.jetbrains.kotlin:analysis-api-platform-interface-for-ide",
        "org.jetbrains.kotlin:symbol-light-classes-for-ide",
        "org.jetbrains.kotlin:analysis-api-standalone-for-ide",
        "org.jetbrains.kotlin:high-level-api-impl-base-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-common-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fir-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-ir-for-ide",
    ).forEach {
        implementation("$it:$kotlinVersion") { isTransitive = false }
    }
    implementation("org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion")


    testImplementation(kotlin("test"))
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.7")
}

tasks.test {
    maxHeapSize = "8192m"
    jvmArgs("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
        "--enable-preview")
    useJUnitPlatform()
}
