@file:JvmName("UnblocktLanguageServer")
// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.apache.logging.log4j.kotlin.logger
import tse.unblockt.ls.protocol.LanguageClient
import tse.unblockt.ls.protocol.runLanguageServer
import tse.unblockt.ls.rpc.Transport
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

val ourLaunchId = UUID.randomUUID().toString()

private const val PRODUCT_NAME = "Unblockt"

fun main(args: Array<String>) {
    val logPath = args.first()
    configureLogger(Paths.get(logPath))

    val logger = logger("entrypoint")
    logger.info("Starting language server...")
    logger.info("Logs path: $logPath")

    runBlocking {
        runLanguageServer(PRODUCT_NAME, Transport.StdIO, LanguageClient.Factory.Default) { client ->
            KotlinLanguageServer(client)
        }
    }
}

private fun configureLogger(path: Path) {
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
    val pathToLog = path.resolve("server.log")
    pathToLog.parent.createDirectories()

    builder.add(
        builder.newAppender("ROLLING", "RollingFile")
            .addAttribute("fileName", pathToLog.absolutePathString())
            .addAttribute("filePattern", "${pathToLog.parent.absolutePathString()}/server-%i.log")
            .add(
                builder.newLayout("PatternLayout")
                    .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
            ).addComponent(
                builder.newComponent("Policies")
                    .addComponent(builder.newComponent("OnStartupTriggeringPolicy"))
                    .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "1 MB"))
            ).addComponent(builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", "10"))
    )
    val rootLogger = builder.newRootLogger(Level.INFO)
    rootLogger.add(builder.newAppenderRef("ROLLING"))
    builder.add(rootLogger)
    Configurator.initialize(builder.build())
}