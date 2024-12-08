// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.service

import java.lang.management.ManagementFactory
import java.text.NumberFormat

private val memoryBean by lazy {
    ManagementFactory.getMemoryMXBean()
}

private val ourNumberFormat = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 0
}

fun memoryMessage(): String {
    val heapMemoryUsage = memoryBean.heapMemoryUsage
    val totalMemory = heapMemoryUsage.max
    val usedMemory = heapMemoryUsage.used
    return "${formatMemory(usedMemory)} of ${formatMemory(totalMemory)}"
}

private fun formatMemory(memoryInBytes: Long): String {
    val mb = memoryInBytes.toDouble() / 1024 / 1024
    return if (mb > 1000) {
        "${ourNumberFormat.format(mb / 1024)}G"
    } else {
        "${ourNumberFormat.format(mb)}M"
    }
}
