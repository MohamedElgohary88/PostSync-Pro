package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.AiMode
import com.postsyncpro.domain.model.SystemProfile

class DetectSystemProfileUseCase {
    operator fun invoke(): SystemProfile {
        val runtime = Runtime.getRuntime()
        val totalRam = runtime.maxMemory() / (1024 * 1024)
        val freeRam = runtime.freeMemory() / (1024 * 1024)
        val osName = System.getProperty("os.name") ?: "Unknown"
        val osArch = System.getProperty("os.arch") ?: "Unknown"
        val cpuCores = runtime.availableProcessors()

        val systemTotalRam = getSystemTotalRam()
        val recommendedMode = if (systemTotalRam >= 8192) AiMode.LOCAL else AiMode.CLOUD
        val recommendation = if (systemTotalRam >= 8192) {
            "Your system has ${systemTotalRam}MB RAM with $cpuCores cores. Local AI is recommended for privacy."
        } else {
            "Your system has ${systemTotalRam}MB RAM. Cloud AI is recommended for better performance."
        }

        return SystemProfile(
            totalRamMb = systemTotalRam,
            availableRamMb = freeRam,
            osName = osName,
            osArch = osArch,
            cpuCores = cpuCores,
            recommendedMode = recommendedMode,
            recommendation = recommendation
        )
    }

    private fun getSystemTotalRam(): Long {
        return try {
            val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                osBean.totalMemorySize / (1024 * 1024)
            } else {
                Runtime.getRuntime().maxMemory() / (1024 * 1024)
            }
        } catch (e: Exception) {
            Runtime.getRuntime().maxMemory() / (1024 * 1024)
        }
    }
}
