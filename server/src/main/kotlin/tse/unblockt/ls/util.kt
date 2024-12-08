// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls

import org.apache.logging.log4j.kotlin.KotlinLogger
import org.apache.logging.log4j.kotlin.logger

val logger = logger("measurements")

inline fun <T: Any> KotlinLogger.safe(whatever: () -> T?): Result<T?> {
    return try {
        Result.success(whatever())
    } catch (t: Throwable) {
        error(t.message ?: "null", t)
        Result.failure(t)
    }
}

inline fun <T> measure(name: String, block: () -> T): T {
    val before = System.currentTimeMillis()
    val result = block()
    val after = System.currentTimeMillis()
    logger.trace("$name took ${after - before} ms")
    return result
}