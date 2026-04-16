package com.postsyncpro.presentation.screen.smarttests

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TestCategory { EDGE_CASE, SECURITY, PERFORMANCE, BUSINESS_LOGIC }
enum class TestPriority { HIGH, MEDIUM, LOW }

data class SmartTestScenario(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val endpoint: String,
    val method: String,
    val category: TestCategory,
    val priority: TestPriority,
    val expectedResult: String,
    val requestBody: String? = null,
    val passed: Boolean? = null
)

data class SmartTestsState(
    val specLoaded: Boolean = false,
    val specFileName: String? = null,
    val isGenerating: Boolean = false,
    val scenarios: List<SmartTestScenario> = emptyList(),
    val selectedCategory: TestCategory? = null,
    val isRunning: Boolean = false,
    val runProgress: Int = 0,
    val totalEndpoints: Int = 0,
    val generatedCount: Int = 0,
    val edgeCaseCount: Int = 0,
    val securityCount: Int = 0,
    val performanceCount: Int = 0,
    val businessLogicCount: Int = 0
)

class SmartTestsViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(SmartTestsState())
    val state: StateFlow<SmartTestsState> = _state.asStateFlow()

    fun loadSpec(filePath: String) {
        _state.value = _state.value.copy(
            specLoaded = true,
            specFileName = filePath.substringAfterLast("/").substringAfterLast("\\")
        )
    }

    fun generateTests() {
        scope.launch {
            _state.value = _state.value.copy(isGenerating = true, scenarios = emptyList())
            kotlinx.coroutines.delay(2000)

            val scenarios = mutableListOf<SmartTestScenario>()

            // Edge Cases
            scenarios.addAll(listOf(
                SmartTestScenario(name = "Empty login credentials", description = "Submit login with empty email and password fields", endpoint = "POST /api/auth/login", method = "POST", category = TestCategory.EDGE_CASE, priority = TestPriority.HIGH, expectedResult = "400 Bad Request with validation errors", requestBody = """{"email": "", "password": ""}"""),
                SmartTestScenario(name = "Unicode in user name", description = "Create user with unicode characters", endpoint = "POST /api/auth/register", method = "POST", category = TestCategory.EDGE_CASE, priority = TestPriority.MEDIUM, expectedResult = "201 Created or 400 with charset error"),
                SmartTestScenario(name = "Max length product query", description = "Search with extremely long query parameter", endpoint = "GET /api/products", method = "GET", category = TestCategory.EDGE_CASE, priority = TestPriority.LOW, expectedResult = "200 OK or 414 URI Too Long"),
                SmartTestScenario(name = "Negative quantity in order", description = "Submit order with negative quantity", endpoint = "POST /api/orders", method = "POST", category = TestCategory.EDGE_CASE, priority = TestPriority.HIGH, expectedResult = "400 Bad Request"),
                SmartTestScenario(name = "Non-existent user ID", description = "Request user with UUID that doesn't exist", endpoint = "GET /api/users/{id}", method = "GET", category = TestCategory.EDGE_CASE, priority = TestPriority.MEDIUM, expectedResult = "404 Not Found"),
            ))

            // Security Tests
            scenarios.addAll(listOf(
                SmartTestScenario(name = "SQL injection in login", description = "Attempt SQL injection through email field", endpoint = "POST /api/auth/login", method = "POST", category = TestCategory.SECURITY, priority = TestPriority.HIGH, expectedResult = "400 Bad Request, no SQL execution"),
                SmartTestScenario(name = "XSS in user profile", description = "Inject script tag in user name", endpoint = "PUT /api/users/{id}", method = "PUT", category = TestCategory.SECURITY, priority = TestPriority.HIGH, expectedResult = "Input sanitized or rejected"),
                SmartTestScenario(name = "IDOR on user deletion", description = "Delete another user's account", endpoint = "DELETE /api/users/{id}", method = "DELETE", category = TestCategory.SECURITY, priority = TestPriority.HIGH, expectedResult = "403 Forbidden"),
                SmartTestScenario(name = "Expired token access", description = "Access protected endpoint with expired JWT", endpoint = "GET /api/orders/{id}", method = "GET", category = TestCategory.SECURITY, priority = TestPriority.HIGH, expectedResult = "401 Unauthorized"),
                SmartTestScenario(name = "Payment tampering", description = "Modify payment amount client-side", endpoint = "POST /api/payments", method = "POST", category = TestCategory.SECURITY, priority = TestPriority.HIGH, expectedResult = "Server-side validation rejects tampered amount"),
            ))

            // Performance Tests
            scenarios.addAll(listOf(
                SmartTestScenario(name = "Large product listing", description = "Request products with no pagination limit", endpoint = "GET /api/products", method = "GET", category = TestCategory.PERFORMANCE, priority = TestPriority.MEDIUM, expectedResult = "Response within 2s, paginated"),
                SmartTestScenario(name = "Concurrent login requests", description = "Simulate 100 simultaneous logins", endpoint = "POST /api/auth/login", method = "POST", category = TestCategory.PERFORMANCE, priority = TestPriority.MEDIUM, expectedResult = "All requests handled within 5s"),
                SmartTestScenario(name = "Health check response time", description = "Health endpoint responds under 100ms", endpoint = "GET /api/health", method = "GET", category = TestCategory.PERFORMANCE, priority = TestPriority.LOW, expectedResult = "< 100ms response time"),
            ))

            // Business Logic
            scenarios.addAll(listOf(
                SmartTestScenario(name = "Double payment prevention", description = "Submit same payment twice rapidly", endpoint = "POST /api/payments", method = "POST", category = TestCategory.BUSINESS_LOGIC, priority = TestPriority.HIGH, expectedResult = "Second payment rejected as duplicate"),
                SmartTestScenario(name = "Order with out-of-stock item", description = "Place order when inventory is zero", endpoint = "POST /api/orders", method = "POST", category = TestCategory.BUSINESS_LOGIC, priority = TestPriority.HIGH, expectedResult = "400 with out-of-stock message"),
                SmartTestScenario(name = "Registration with existing email", description = "Register with email already in system", endpoint = "POST /api/auth/register", method = "POST", category = TestCategory.BUSINESS_LOGIC, priority = TestPriority.MEDIUM, expectedResult = "409 Conflict"),
            ))

            _state.value = _state.value.copy(
                isGenerating = false,
                scenarios = scenarios,
                totalEndpoints = 10,
                generatedCount = scenarios.size,
                edgeCaseCount = scenarios.count { it.category == TestCategory.EDGE_CASE },
                securityCount = scenarios.count { it.category == TestCategory.SECURITY },
                performanceCount = scenarios.count { it.category == TestCategory.PERFORMANCE },
                businessLogicCount = scenarios.count { it.category == TestCategory.BUSINESS_LOGIC }
            )
        }
    }

    fun setCategory(category: TestCategory?) { _state.value = _state.value.copy(selectedCategory = category) }

    fun runAllScenarios() {
        scope.launch {
            _state.value = _state.value.copy(isRunning = true, runProgress = 0)
            val total = _state.value.scenarios.size
            for (i in _state.value.scenarios.indices) {
                kotlinx.coroutines.delay(300 + (Math.random() * 500).toLong())
                val passed = Math.random() > 0.25
                _state.value = _state.value.copy(
                    runProgress = ((i + 1) * 100) / total,
                    scenarios = _state.value.scenarios.toMutableList().apply {
                        set(i, get(i).copy(passed = passed))
                    }
                )
            }
            _state.value = _state.value.copy(isRunning = false)
        }
    }

    fun onCleared() { scope.cancel() }
}
