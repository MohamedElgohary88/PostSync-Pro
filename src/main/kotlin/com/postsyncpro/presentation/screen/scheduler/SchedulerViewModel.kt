package com.postsyncpro.presentation.screen.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduledJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val testType: TestType = TestType.REGRESSION,
    val interval: ScheduleInterval = ScheduleInterval.DAILY,
    val customCron: String = "",
    val swaggerFilePath: String? = null,
    val isEnabled: Boolean = true,
    val lastRun: String? = null,
    val nextRun: String? = null,
    val lastResult: String? = null,
    val runHistory: List<RunResult> = emptyList()
)

data class RunResult(
    val timestamp: String,
    val passRate: Double,
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val duration: Long // ms
)

enum class TestType { REGRESSION, FUZZ, CHAIN, COVERAGE }

enum class ScheduleInterval { EVERY_HOUR, EVERY_6_HOURS, EVERY_12_HOURS, DAILY, WEEKLY, CUSTOM_CRON }

data class SchedulerState(
    val jobs: List<ScheduledJob> = emptyList(),
    val isCreating: Boolean = false,
    val editingJob: ScheduledJob? = null,
    val selectedJobId: String? = null,
    val isRunning: Map<String, Boolean> = emptyMap()
)

class SchedulerViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(SchedulerState())
    val state: StateFlow<SchedulerState> = _state.asStateFlow()

    fun startCreating() { _state.value = _state.value.copy(isCreating = true, editingJob = ScheduledJob()) }

    fun cancelCreating() { _state.value = _state.value.copy(isCreating = false, editingJob = null) }

    fun updateEditingJob(job: ScheduledJob) { _state.value = _state.value.copy(editingJob = job) }

    fun saveJob() {
        val job = _state.value.editingJob ?: return
        val nextRun = calculateNextRun(job.interval)
        val updatedJob = job.copy(nextRun = nextRun)
        val existing = _state.value.jobs.indexOfFirst { it.id == job.id }
        val jobs = if (existing >= 0) {
            _state.value.jobs.toMutableList().apply { set(existing, updatedJob) }
        } else {
            _state.value.jobs + updatedJob
        }
        _state.value = _state.value.copy(jobs = jobs, isCreating = false, editingJob = null)
    }

    fun deleteJob(jobId: String) {
        _state.value = _state.value.copy(
            jobs = _state.value.jobs.filter { it.id != jobId },
            selectedJobId = if (_state.value.selectedJobId == jobId) null else _state.value.selectedJobId
        )
    }

    fun toggleJobEnabled(jobId: String) {
        _state.value = _state.value.copy(
            jobs = _state.value.jobs.map { if (it.id == jobId) it.copy(isEnabled = !it.isEnabled) else it }
        )
    }

    fun selectJob(jobId: String?) { _state.value = _state.value.copy(selectedJobId = jobId) }

    fun runNow(jobId: String) {
        scope.launch {
            _state.value = _state.value.copy(isRunning = _state.value.isRunning + (jobId to true))
            kotlinx.coroutines.delay(2000 + (Math.random() * 3000).toLong())

            val passRate = 70.0 + Math.random() * 30.0
            val totalTests = (10 + (Math.random() * 40).toInt())
            val passed = (totalTests * passRate / 100).toInt()
            val result = RunResult(
                timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                passRate = passRate,
                totalTests = totalTests,
                passed = passed,
                failed = totalTests - passed,
                duration = (1000 + (Math.random() * 5000).toLong())
            )

            _state.value = _state.value.copy(
                isRunning = _state.value.isRunning + (jobId to false),
                jobs = _state.value.jobs.map {
                    if (it.id == jobId) it.copy(
                        lastRun = result.timestamp,
                        lastResult = "${result.passRate.toInt()}% (${result.passed}/${result.totalTests})",
                        runHistory = (listOf(result) + it.runHistory).take(20),
                        nextRun = calculateNextRun(it.interval)
                    ) else it
                }
            )
        }
    }

    private fun calculateNextRun(interval: ScheduleInterval): String {
        val now = java.time.LocalDateTime.now()
        val next = when (interval) {
            ScheduleInterval.EVERY_HOUR -> now.plusHours(1)
            ScheduleInterval.EVERY_6_HOURS -> now.plusHours(6)
            ScheduleInterval.EVERY_12_HOURS -> now.plusHours(12)
            ScheduleInterval.DAILY -> now.plusDays(1)
            ScheduleInterval.WEEKLY -> now.plusWeeks(1)
            ScheduleInterval.CUSTOM_CRON -> now.plusDays(1) // simplified
        }
        return next.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    fun onCleared() { scope.cancel() }
}
