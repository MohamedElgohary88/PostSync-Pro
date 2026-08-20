package com.postsyncpro.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persistable, declarative checks that can be evaluated against a captured
 * workbench response. Assertions intentionally do not support user-provided
 * regular expressions or executable scripts. Comparison operands may still
 * contain credentials; persistence layers must keep them templated, strip
 * them, or encrypt them instead of writing literal secrets to disk.
 */
@Serializable
sealed class WorkbenchAssertion {
    abstract val id: String
    abstract val enabled: Boolean

    @Serializable
    @SerialName("statusEquals")
    data class StatusEquals(
        override val id: String = "",
        override val enabled: Boolean = true,
        val expected: Int = 200
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("statusRange")
    data class StatusRange(
        override val id: String = "",
        override val enabled: Boolean = true,
        val minimum: Int = 200,
        val maximum: Int = 299
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("responseTimeMax")
    data class ResponseTimeMax(
        override val id: String = "",
        override val enabled: Boolean = true,
        val maximumMillis: Long = 1_000
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("headerExists")
    data class HeaderExists(
        override val id: String = "",
        override val enabled: Boolean = true,
        val name: String = ""
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("headerEquals")
    data class HeaderEquals(
        override val id: String = "",
        override val enabled: Boolean = true,
        val name: String = "",
        val expected: String = "",
        val ignoreCase: Boolean = false,
        /** Sensitive operands are evaluated in memory and stripped before persistence. */
        val sensitive: Boolean = false
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("headerContains")
    data class HeaderContains(
        override val id: String = "",
        override val enabled: Boolean = true,
        val name: String = "",
        val expected: String = "",
        val ignoreCase: Boolean = false,
        /** Sensitive operands are evaluated in memory and stripped before persistence. */
        val sensitive: Boolean = false
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("bodyContains")
    data class BodyContains(
        override val id: String = "",
        override val enabled: Boolean = true,
        val expected: String = "",
        val ignoreCase: Boolean = false,
        /** Sensitive operands are evaluated in memory and stripped before persistence. */
        val sensitive: Boolean = false
    ) : WorkbenchAssertion()

    @Serializable
    @SerialName("jsonPathExists")
    data class JsonPathExists(
        override val id: String = "",
        override val enabled: Boolean = true,
        val path: String = "$"
    ) : WorkbenchAssertion()

    /**
     * [expectedJson] is a JSON literal, not a display string. For example:
     * `"ready"`, `42`, `true`, `null`, or `{"state":"ready"}`.
     */
    @Serializable
    @SerialName("jsonPathEquals")
    data class JsonPathEquals(
        override val id: String = "",
        override val enabled: Boolean = true,
        val path: String = "$",
        val expectedJson: String = "null",
        /** Sensitive operands are evaluated in memory and stripped before persistence. */
        val sensitive: Boolean = false
    ) : WorkbenchAssertion()
}

@Serializable
enum class WorkbenchAssertionOutcome {
    PASSED,
    FAILED,
    ERROR,
    /** Disabled assertions remain visible without affecting [WorkbenchAssertionReport.allPassed]. */
    SKIPPED
}

/**
 * A safe diagnostic view of one evaluation. [message] never contains the raw
 * response value or the configured expected value.
 */
@Serializable
data class WorkbenchAssertionResult(
    val assertionId: String,
    val assertionIndex: Int,
    val outcome: WorkbenchAssertionOutcome,
    val message: String
)

@Serializable
data class WorkbenchAssertionReport(
    /** Input order is preserved exactly for deterministic UI and persistence. */
    val results: List<WorkbenchAssertionResult>
) {
    val passedCount: Int get() = results.count { it.outcome == WorkbenchAssertionOutcome.PASSED }
    val failedCount: Int get() = results.count { it.outcome == WorkbenchAssertionOutcome.FAILED }
    val errorCount: Int get() = results.count { it.outcome == WorkbenchAssertionOutcome.ERROR }
    val skippedCount: Int get() = results.count { it.outcome == WorkbenchAssertionOutcome.SKIPPED }

    /** Disabled checks are neutral; failures and evaluation errors are not. */
    val allPassed: Boolean
        get() = results.none {
            it.outcome == WorkbenchAssertionOutcome.FAILED ||
                it.outcome == WorkbenchAssertionOutcome.ERROR
        }

    val hasEvaluatedAssertions: Boolean
        get() = results.any { it.outcome != WorkbenchAssertionOutcome.SKIPPED }
}
