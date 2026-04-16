package com.postsyncpro.presentation.screen.testhistory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TestFilter { ALL, FUZZ, REGRESSION, CHAIN }
enum class TrendDirection { UP, DOWN, STABLE }

data class TestRun(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String,
    val testType: String,
    val passRate: Double,
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val duration: Long // ms
)

data class TestHistoryState(
    val runs: List<TestRun> = emptyList(),
    val filter: TestFilter = TestFilter.ALL,
    val totalRuns: Int = 0,
    val avgPassRate: Double = 0.0,
    val bestRunRate: Double = 0.0,
    val worstRunRate: Double = 100.0,
    val trend: TrendDirection = TrendDirection.STABLE,
    val selectedRunId: String? = null
)

class TestHistoryViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(TestHistoryState())
    val state: StateFlow<TestHistoryState> = _state.asStateFlow()

    init { loadSampleData() }

    private fun loadSampleData() {
        val types = listOf("Fuzz", "Regression", "Chain")
        val runs = (0 until 15).map { i ->
            val passRate = 65.0 + Math.random() * 35.0
            val total = 10 + (Math.random() * 50).toInt()
            val passed = (total * passRate / 100).toInt()
            TestRun(
                timestamp = java.time.LocalDateTime.now().minusHours((i * 6).toLong())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                testType = types[i % 3],
                passRate = passRate,
                totalTests = total,
                passed = passed,
                failed = total - passed,
                duration = 500 + (Math.random() * 5000).toLong()
            )
        }
        updateStats(runs)
    }

    fun setFilter(filter: TestFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun selectRun(runId: String?) {
        _state.value = _state.value.copy(selectedRunId = runId)
    }

    private fun updateStats(runs: List<TestRun>) {
        if (runs.isEmpty()) return
        val avgRate = runs.map { it.passRate }.average()
        val best = runs.maxOf { it.passRate }
        val worst = runs.minOf { it.passRate }
        val recent = runs.take(3).map { it.passRate }.average()
        val older = runs.drop(3).take(3).map { it.passRate }.averageOrNull() ?: recent
        val trend = when {
            recent - older > 3 -> TrendDirection.UP
            older - recent > 3 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
        _state.value = TestHistoryState(
            runs = runs, totalRuns = runs.size, avgPassRate = avgRate,
            bestRunRate = best, worstRunRate = worst, trend = trend
        )
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()

    fun onCleared() { scope.cancel() }
}
