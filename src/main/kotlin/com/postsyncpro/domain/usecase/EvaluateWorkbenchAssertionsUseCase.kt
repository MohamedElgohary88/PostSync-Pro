package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAssertionOutcome
import com.postsyncpro.domain.model.WorkbenchAssertionReport
import com.postsyncpro.domain.model.WorkbenchAssertionResult
import com.postsyncpro.domain.model.WorkbenchResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Resource ceilings for deterministic, bounded assertion evaluation. */
data class WorkbenchAssertionLimits(
    val maxAssertions: Int = 100,
    val maxResponseBodyCharacters: Int = 2_000_000,
    val maxHeaders: Int = 512,
    val maxTotalHeaderCharacters: Int = 262_144,
    val maxExpectedCharacters: Int = 65_536,
    val maxContainsNeedleCharacters: Int = 16_384,
    val maxHeaderNameCharacters: Int = 256,
    val maxAssertionIdCharacters: Int = 256,
    val maxJsonDepth: Int = 64,
    val maxJsonStructuralTokens: Int = 100_000,
    val maxJsonPathCharacters: Int = 2_048,
    val maxJsonPathSegments: Int = 64,
    val maxJsonPathSegmentCharacters: Int = 512
) {
    init {
        require(maxAssertions in 1..HARD_MAX_ASSERTIONS)
        require(maxResponseBodyCharacters in 1..HARD_MAX_BODY_CHARACTERS)
        require(maxHeaders in 1..HARD_MAX_HEADERS)
        require(maxTotalHeaderCharacters in 1..HARD_MAX_HEADER_CHARACTERS)
        require(maxExpectedCharacters in 1..HARD_MAX_EXPECTED_CHARACTERS)
        require(maxContainsNeedleCharacters in 1..HARD_MAX_EXPECTED_CHARACTERS)
        require(maxHeaderNameCharacters in 1..HARD_MAX_HEADER_NAME_CHARACTERS)
        require(maxAssertionIdCharacters in 1..HARD_MAX_ASSERTION_ID_CHARACTERS)
        require(maxJsonDepth in 1..HARD_MAX_JSON_DEPTH)
        require(maxJsonStructuralTokens in 1..HARD_MAX_JSON_TOKENS)
        require(maxJsonPathCharacters in 1..HARD_MAX_JSON_PATH_CHARACTERS)
        require(maxJsonPathSegments in 1..HARD_MAX_JSON_PATH_SEGMENTS)
        require(maxJsonPathSegmentCharacters in 1..HARD_MAX_JSON_PATH_CHARACTERS)
    }

    private companion object {
        const val HARD_MAX_ASSERTIONS = 1_000
        const val HARD_MAX_BODY_CHARACTERS = 8_000_000
        const val HARD_MAX_HEADERS = 4_096
        const val HARD_MAX_HEADER_CHARACTERS = 2_000_000
        const val HARD_MAX_EXPECTED_CHARACTERS = 262_144
        const val HARD_MAX_HEADER_NAME_CHARACTERS = 2_048
        const val HARD_MAX_ASSERTION_ID_CHARACTERS = 2_048
        const val HARD_MAX_JSON_DEPTH = 256
        const val HARD_MAX_JSON_TOKENS = 1_000_000
        const val HARD_MAX_JSON_PATH_CHARACTERS = 16_384
        const val HARD_MAX_JSON_PATH_SEGMENTS = 512
    }
}

/**
 * Evaluates declarative checks against the raw response while emitting only
 * bounded, redacted diagnostics. It deliberately supports no regex, scripts,
 * JSONPath filters, wildcards, or recursive descent.
 */
class EvaluateWorkbenchAssertionsUseCase(
    private val limits: WorkbenchAssertionLimits = WorkbenchAssertionLimits(),
    private val secretRedactor: SecretRedactor = SecretRedactor()
) {
    private val json = Json { isLenient = false }

    suspend operator fun invoke(
        response: WorkbenchResponse,
        assertions: List<WorkbenchAssertion>,
        sensitiveValues: Set<String> = emptySet()
    ): WorkbenchAssertionReport {
        currentCoroutineContext().ensureActive()
        val diagnosticSecrets = sensitiveValues
            .asSequence()
            .filter { it.isNotEmpty() && it.length <= limits.maxExpectedCharacters }
            .take(limits.maxAssertions)
            .toCollection(linkedSetOf())

        if (assertions.size > limits.maxAssertions) {
            return WorkbenchAssertionReport(
                listOf(
                    WorkbenchAssertionResult(
                        assertionId = RUN_ERROR_ID,
                        assertionIndex = -1,
                        outcome = WorkbenchAssertionOutcome.ERROR,
                        message = redact("The assertion run exceeds the safe assertion limit.", diagnosticSecrets)
                    )
                )
            )
        }

        val headerBudgetExceeded = response.headers.size > limits.maxHeaders ||
            response.headers.exceedsCharacterBudget(limits.maxTotalHeaderCharacters)
        val bodyBudgetExceeded = response.body.length > limits.maxResponseBodyCharacters
        val jsonDocument = LazyJsonDocument(response.body, bodyBudgetExceeded)
        val results = ArrayList<WorkbenchAssertionResult>(assertions.size)

        assertions.forEachIndexed { index, assertion ->
            currentCoroutineContext().ensureActive()
            val id = safeAssertionId(assertion.id, index, diagnosticSecrets)
            if (!assertion.enabled) {
                results += result(
                    id,
                    index,
                    WorkbenchAssertionOutcome.SKIPPED,
                    "Assertion is disabled.",
                    diagnosticSecrets
                )
                return@forEachIndexed
            }

            results += when (assertion) {
                is WorkbenchAssertion.StatusEquals -> evaluateStatusEquals(
                    assertion,
                    response,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.StatusRange -> evaluateStatusRange(
                    assertion,
                    response,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.ResponseTimeMax -> evaluateResponseTime(
                    assertion,
                    response,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.HeaderExists -> evaluateHeaderExists(
                    assertion,
                    response,
                    headerBudgetExceeded,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.HeaderEquals -> evaluateHeaderEquals(
                    assertion,
                    response,
                    headerBudgetExceeded,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.HeaderContains -> evaluateHeaderContains(
                    assertion,
                    response,
                    headerBudgetExceeded,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.BodyContains -> evaluateBodyContains(
                    assertion,
                    response,
                    bodyBudgetExceeded,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.JsonPathExists -> evaluateJsonPathExists(
                    assertion,
                    response,
                    jsonDocument,
                    id,
                    index,
                    diagnosticSecrets
                )
                is WorkbenchAssertion.JsonPathEquals -> evaluateJsonPathEquals(
                    assertion,
                    response,
                    jsonDocument,
                    id,
                    index,
                    diagnosticSecrets
                )
            }
        }
        return WorkbenchAssertionReport(results)
    }

    private fun evaluateStatusEquals(
        assertion: WorkbenchAssertion.StatusEquals,
        response: WorkbenchResponse,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        if (assertion.expected !in HTTP_STATUS_RANGE) {
            return error(id, index, "Status assertion configuration is invalid.", secrets)
        }
        return passOrFail(
            response.statusCode == assertion.expected,
            id,
            index,
            "Status code matched.",
            "Status code did not match.",
            secrets
        )
    }

    private fun evaluateStatusRange(
        assertion: WorkbenchAssertion.StatusRange,
        response: WorkbenchResponse,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        if (assertion.minimum !in HTTP_STATUS_RANGE ||
            assertion.maximum !in HTTP_STATUS_RANGE ||
            assertion.minimum > assertion.maximum
        ) {
            return error(id, index, "Status range configuration is invalid.", secrets)
        }
        return passOrFail(
            response.statusCode in assertion.minimum..assertion.maximum,
            id,
            index,
            "Status code is inside the expected range.",
            "Status code is outside the expected range.",
            secrets
        )
    }

    private fun evaluateResponseTime(
        assertion: WorkbenchAssertion.ResponseTimeMax,
        response: WorkbenchResponse,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        if (assertion.maximumMillis < 0) {
            return error(id, index, "Response-time assertion configuration is invalid.", secrets)
        }
        if (response.durationMillis < 0) {
            return error(id, index, "Response timing data is invalid.", secrets)
        }
        return passOrFail(
            response.durationMillis <= assertion.maximumMillis,
            id,
            index,
            "Response completed within the time limit.",
            "Response exceeded the time limit.",
            secrets
        )
    }

    private fun evaluateHeaderExists(
        assertion: WorkbenchAssertion.HeaderExists,
        response: WorkbenchResponse,
        budgetExceeded: Boolean,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        headerConfigurationError(assertion.name, null)?.let {
            return error(id, index, it, secrets)
        }
        if (budgetExceeded) return error(id, index, "Response headers exceed the safe assertion limit.", secrets)
        val exists = response.headers.any { it.key.equals(assertion.name, ignoreCase = true) }
        return passOrFail(
            exists,
            id,
            index,
            "Response header exists.",
            "Response header is missing.",
            secrets
        )
    }

    private fun evaluateHeaderEquals(
        assertion: WorkbenchAssertion.HeaderEquals,
        response: WorkbenchResponse,
        budgetExceeded: Boolean,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        headerConfigurationError(assertion.name, assertion.expected)?.let {
            return error(id, index, it, secrets)
        }
        if (budgetExceeded) return error(id, index, "Response headers exceed the safe assertion limit.", secrets)
        val matches = response.headers.any {
            it.key.equals(assertion.name, ignoreCase = true) &&
                it.value.equals(assertion.expected, ignoreCase = assertion.ignoreCase)
        }
        return passOrFail(
            matches,
            id,
            index,
            "Response header matched.",
            "Response header did not match.",
            secrets
        )
    }

    private suspend fun evaluateHeaderContains(
        assertion: WorkbenchAssertion.HeaderContains,
        response: WorkbenchResponse,
        budgetExceeded: Boolean,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        headerConfigurationError(assertion.name, assertion.expected)?.let {
            return error(id, index, it, secrets)
        }
        if (assertion.expected.isEmpty()) {
            return error(id, index, "Header containment value cannot be empty.", secrets)
        }
        if (assertion.expected.length > limits.maxContainsNeedleCharacters) {
            return error(id, index, "Header containment value exceeds the safe assertion limit.", secrets)
        }
        if (budgetExceeded) return error(id, index, "Response headers exceed the safe assertion limit.", secrets)

        var matches = false
        for (header in response.headers) {
            currentCoroutineContext().ensureActive()
            if (header.key.equals(assertion.name, ignoreCase = true) &&
                containsLinear(header.value, assertion.expected, assertion.ignoreCase)
            ) {
                matches = true
                break
            }
        }
        return passOrFail(
            matches,
            id,
            index,
            "Response header contains the expected value.",
            "Response header does not contain the expected value.",
            secrets
        )
    }

    private suspend fun evaluateBodyContains(
        assertion: WorkbenchAssertion.BodyContains,
        response: WorkbenchResponse,
        budgetExceeded: Boolean,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        if (assertion.expected.isEmpty()) {
            return error(id, index, "Body containment value cannot be empty.", secrets)
        }
        if (assertion.expected.length > limits.maxContainsNeedleCharacters) {
            return error(id, index, "Body containment value exceeds the safe assertion limit.", secrets)
        }
        bodyEvaluationError(response, budgetExceeded)?.let { return error(id, index, it, secrets) }
        return passOrFail(
            containsLinear(response.body, assertion.expected, assertion.ignoreCase),
            id,
            index,
            "Response body contains the expected value.",
            "Response body does not contain the expected value.",
            secrets
        )
    }

    private suspend fun evaluateJsonPathExists(
        assertion: WorkbenchAssertion.JsonPathExists,
        response: WorkbenchResponse,
        document: LazyJsonDocument,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        bodyEvaluationError(response, document.bodyBudgetExceeded)?.let {
            return error(id, index, it, secrets)
        }
        val segments = when (val parsed = parseJsonPath(assertion.path)) {
            is JsonPathParseResult.Invalid -> return error(id, index, parsed.message, secrets)
            is JsonPathParseResult.Valid -> parsed.segments
        }
        return when (val loaded = document.load()) {
            is JsonLoadResult.Invalid -> error(id, index, loaded.message, secrets)
            is JsonLoadResult.Success -> {
                val resolved = resolvePath(loaded.element, segments)
                passOrFail(
                    resolved.found,
                    id,
                    index,
                    "JSON path exists.",
                    "JSON path does not exist.",
                    secrets
                )
            }
        }
    }

    private suspend fun evaluateJsonPathEquals(
        assertion: WorkbenchAssertion.JsonPathEquals,
        response: WorkbenchResponse,
        document: LazyJsonDocument,
        id: String,
        index: Int,
        secrets: Set<String>
    ): WorkbenchAssertionResult {
        bodyEvaluationError(response, document.bodyBudgetExceeded)?.let {
            return error(id, index, it, secrets)
        }
        val segments = when (val parsed = parseJsonPath(assertion.path)) {
            is JsonPathParseResult.Invalid -> return error(id, index, parsed.message, secrets)
            is JsonPathParseResult.Valid -> parsed.segments
        }
        val expected = when (val parsed = parseExpectedJson(assertion.expectedJson)) {
            is JsonLoadResult.Invalid -> return error(id, index, parsed.message, secrets)
            is JsonLoadResult.Success -> parsed.element
        }
        return when (val loaded = document.load()) {
            is JsonLoadResult.Invalid -> error(id, index, loaded.message, secrets)
            is JsonLoadResult.Success -> {
                val resolved = resolvePath(loaded.element, segments)
                passOrFail(
                    resolved.found && resolved.value == expected,
                    id,
                    index,
                    "JSON value matched.",
                    "JSON value did not match.",
                    secrets
                )
            }
        }
    }

    private fun headerConfigurationError(name: String, expected: String?): String? = when {
        name.isBlank() -> "Header name cannot be empty."
        name.length > limits.maxHeaderNameCharacters -> "Header name exceeds the safe assertion limit."
        name.any { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f } ->
            "Header name contains invalid characters."
        expected != null && expected.length > limits.maxExpectedCharacters ->
            "Header comparison value exceeds the safe assertion limit."
        else -> null
    }

    private fun bodyEvaluationError(response: WorkbenchResponse, budgetExceeded: Boolean): String? = when {
        response.truncated || response.previewTruncated ->
            "Response body is truncated, so the body assertion was not evaluated."
        budgetExceeded -> "Response body exceeds the safe assertion limit."
        else -> null
    }

    private suspend fun parseExpectedJson(input: String): JsonLoadResult {
        if (input.length > limits.maxExpectedCharacters) {
            return JsonLoadResult.Invalid("Expected JSON exceeds the safe assertion limit.")
        }
        inspectJsonBudget(input)?.let { return JsonLoadResult.Invalid(it) }
        return parseJson(input, "Expected value is not valid JSON.")
    }

    private suspend fun parseJson(input: String, malformedMessage: String): JsonLoadResult {
        currentCoroutineContext().ensureActive()
        if (!hasStrictJsonRootSyntax(input)) return JsonLoadResult.Invalid(malformedMessage)
        return try {
            JsonLoadResult.Success(json.parseToJsonElement(input))
        } catch (_: SerializationException) {
            JsonLoadResult.Invalid(malformedMessage)
        } catch (_: IllegalArgumentException) {
            JsonLoadResult.Invalid(malformedMessage)
        }
    }

    /** kotlinx.serialization accepts some unquoted root literals; assertions require strict JSON. */
    private fun hasStrictJsonRootSyntax(input: String): Boolean {
        var start = 0
        while (start < input.length && input[start].isJsonWhitespace()) start++
        if (start == input.length) return false
        var endExclusive = input.length
        while (endExclusive > start && input[endExclusive - 1].isJsonWhitespace()) endExclusive--

        return when (input[start]) {
            '{', '[', '"' -> true
            't' -> input.regionMatches(start, "true", 0, 4) && start + 4 == endExclusive
            'f' -> input.regionMatches(start, "false", 0, 5) && start + 5 == endExclusive
            'n' -> input.regionMatches(start, "null", 0, 4) && start + 4 == endExclusive
            '-', in '0'..'9' -> isStrictJsonNumber(input, start, endExclusive)
            else -> false
        }
    }

    private fun isStrictJsonNumber(input: String, start: Int, endExclusive: Int): Boolean {
        var cursor = start
        if (input[cursor] == '-') {
            cursor++
            if (cursor == endExclusive) return false
        }
        when {
            input[cursor] == '0' -> cursor++
            input[cursor] in '1'..'9' -> {
                cursor++
                while (cursor < endExclusive && input[cursor].isDigit()) cursor++
            }
            else -> return false
        }
        if (cursor < endExclusive && input[cursor] == '.') {
            cursor++
            val fractionStart = cursor
            while (cursor < endExclusive && input[cursor].isDigit()) cursor++
            if (fractionStart == cursor) return false
        }
        if (cursor < endExclusive && (input[cursor] == 'e' || input[cursor] == 'E')) {
            cursor++
            if (cursor < endExclusive && (input[cursor] == '+' || input[cursor] == '-')) cursor++
            val exponentStart = cursor
            while (cursor < endExclusive && input[cursor].isDigit()) cursor++
            if (exponentStart == cursor) return false
        }
        return cursor == endExclusive
    }

    private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\r' || this == '\n'

    /** Returns a generic error message, or null when input is inside budget. */
    private suspend fun inspectJsonBudget(input: String): String? {
        var depth = 0
        var tokens = if (input.isBlank()) 0 else 1
        var inString = false
        var escaped = false
        input.forEachIndexed { index, char ->
            if (index and CANCELLATION_CHECK_MASK == 0) currentCoroutineContext().ensureActive()
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        tokens++
                        if (depth > limits.maxJsonDepth) return "JSON exceeds the safe nesting limit."
                        if (tokens > limits.maxJsonStructuralTokens) {
                            return "JSON exceeds the safe structural limit."
                        }
                    }
                    '}', ']' -> depth--
                    ',', ':' -> {
                        tokens++
                        if (tokens > limits.maxJsonStructuralTokens) {
                            return "JSON exceeds the safe structural limit."
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseJsonPath(path: String): JsonPathParseResult {
        if (path.length > limits.maxJsonPathCharacters) {
            return JsonPathParseResult.Invalid("JSON path exceeds the safe assertion limit.")
        }
        if (path.isEmpty() || path[0] != '$') {
            return JsonPathParseResult.Invalid("JSON path syntax is invalid.")
        }
        if (path.length == 1) return JsonPathParseResult.Valid(emptyList())

        val segments = ArrayList<JsonPathSegment>()
        var cursor = 1
        while (cursor < path.length) {
            if (segments.size >= limits.maxJsonPathSegments) {
                return JsonPathParseResult.Invalid("JSON path has too many segments.")
            }
            when (path[cursor]) {
                '.' -> {
                    cursor++
                    val start = cursor
                    while (cursor < path.length && path[cursor] != '.' && path[cursor] != '[') cursor++
                    if (start == cursor) return JsonPathParseResult.Invalid("JSON path syntax is invalid.")
                    val key = path.substring(start, cursor)
                    if (!isSimplePathProperty(key) || key.length > limits.maxJsonPathSegmentCharacters) {
                        return JsonPathParseResult.Invalid("JSON path syntax is invalid.")
                    }
                    segments += JsonPathSegment.Property(key)
                }
                '[' -> {
                    val parsed = parseBracketSegment(path, cursor)
                    if (parsed is BracketParseResult.Invalid) {
                        return JsonPathParseResult.Invalid(parsed.message)
                    }
                    parsed as BracketParseResult.Valid
                    segments += parsed.segment
                    cursor = parsed.nextCursor
                }
                else -> return JsonPathParseResult.Invalid("JSON path syntax is invalid.")
            }
        }
        return JsonPathParseResult.Valid(segments)
    }

    private fun parseBracketSegment(path: String, start: Int): BracketParseResult {
        var cursor = start + 1
        if (cursor >= path.length) return BracketParseResult.Invalid("JSON path syntax is invalid.")
        val quote = path[cursor]
        if (quote == '\'' || quote == '"') {
            cursor++
            val key = StringBuilder()
            var closedQuote = false
            while (cursor < path.length) {
                val char = path[cursor++]
                when {
                    char == quote -> {
                        closedQuote = true
                        break
                    }
                    char == '\\' -> {
                        val escaped = decodePathEscape(path, cursor)
                            ?: return BracketParseResult.Invalid("JSON path contains an invalid escape.")
                        key.append(escaped.value)
                        cursor = escaped.nextCursor
                    }
                    else -> key.append(char)
                }
                if (key.length > limits.maxJsonPathSegmentCharacters) {
                    return BracketParseResult.Invalid("JSON path segment exceeds the safe assertion limit.")
                }
            }
            if (!closedQuote || cursor >= path.length || path[cursor] != ']') {
                return BracketParseResult.Invalid("JSON path syntax is invalid.")
            }
            return BracketParseResult.Valid(JsonPathSegment.Property(key.toString()), cursor + 1)
        }

        val digitsStart = cursor
        while (cursor < path.length && path[cursor].isDigit()) cursor++
        if (digitsStart == cursor || cursor >= path.length || path[cursor] != ']') {
            return BracketParseResult.Invalid("JSON path syntax is invalid.")
        }
        val arrayIndex = path.substring(digitsStart, cursor).toIntOrNull()
            ?: return BracketParseResult.Invalid("JSON path array index is invalid.")
        return BracketParseResult.Valid(JsonPathSegment.ArrayIndex(arrayIndex), cursor + 1)
    }

    private fun decodePathEscape(path: String, cursor: Int): DecodedEscape? {
        if (cursor >= path.length) return null
        return when (val escaped = path[cursor]) {
            '\\', '\'', '"', '/' -> DecodedEscape(escaped, cursor + 1)
            'b' -> DecodedEscape('\b', cursor + 1)
            'f' -> DecodedEscape('\u000c', cursor + 1)
            'n' -> DecodedEscape('\n', cursor + 1)
            'r' -> DecodedEscape('\r', cursor + 1)
            't' -> DecodedEscape('\t', cursor + 1)
            'u' -> {
                if (cursor + 5 > path.length) return null
                val codePoint = path.substring(cursor + 1, cursor + 5).toIntOrNull(16) ?: return null
                DecodedEscape(codePoint.toChar(), cursor + 5)
            }
            else -> null
        }
    }

    private fun isSimplePathProperty(value: String): Boolean {
        if (value.isEmpty()) return false
        if (!(value[0] == '_' || value[0].isLetter())) return false
        return value.drop(1).all { it == '_' || it == '-' || it.isLetterOrDigit() }
    }

    private fun resolvePath(root: JsonElement, segments: List<JsonPathSegment>): PathResolution {
        var current = root
        for (segment in segments) {
            current = when (segment) {
                is JsonPathSegment.Property -> (current as? JsonObject)?.get(segment.name)
                is JsonPathSegment.ArrayIndex -> (current as? JsonArray)?.getOrNull(segment.index)
            } ?: return PathResolution(found = false, value = null)
        }
        return PathResolution(found = true, value = current)
    }

    /** KMP search keeps containment checks O(response + expected), including ignore-case mode. */
    private suspend fun containsLinear(haystack: String, needle: String, ignoreCase: Boolean): Boolean {
        if (needle.isEmpty()) return true
        if (needle.length > haystack.length) return false
        val prefix = IntArray(needle.length)
        var matched = 0
        for (index in 1 until needle.length) {
            if (index and CANCELLATION_CHECK_MASK == 0) currentCoroutineContext().ensureActive()
            while (matched > 0 && !sameCharacter(needle[index], needle[matched], ignoreCase)) {
                matched = prefix[matched - 1]
            }
            if (sameCharacter(needle[index], needle[matched], ignoreCase)) matched++
            prefix[index] = matched
        }
        matched = 0
        haystack.forEachIndexed { index, char ->
            if (index and CANCELLATION_CHECK_MASK == 0) currentCoroutineContext().ensureActive()
            while (matched > 0 && !sameCharacter(char, needle[matched], ignoreCase)) {
                matched = prefix[matched - 1]
            }
            if (sameCharacter(char, needle[matched], ignoreCase)) matched++
            if (matched == needle.length) return true
        }
        return false
    }

    private fun sameCharacter(left: Char, right: Char, ignoreCase: Boolean): Boolean =
        left == right || (ignoreCase && left.lowercaseChar() == right.lowercaseChar())

    private fun List<com.postsyncpro.domain.model.WorkbenchRequestField>.exceedsCharacterBudget(
        maximum: Int
    ): Boolean {
        var total = 0L
        forEach { header ->
            total += header.key.length.toLong() + header.value.length.toLong()
            if (total > maximum) return true
        }
        return false
    }

    private fun safeAssertionId(id: String, index: Int, secrets: Set<String>): String {
        if (id.isBlank() || id.length > limits.maxAssertionIdCharacters) return "assertion-${index + 1}"
        val safe = secretRedactor.redactText(id, secrets)
        return if (safe == id) id else "assertion-${index + 1}"
    }

    private fun passOrFail(
        passed: Boolean,
        id: String,
        index: Int,
        passedMessage: String,
        failedMessage: String,
        secrets: Set<String>
    ) = result(
        id,
        index,
        if (passed) WorkbenchAssertionOutcome.PASSED else WorkbenchAssertionOutcome.FAILED,
        if (passed) passedMessage else failedMessage,
        secrets
    )

    private fun error(id: String, index: Int, message: String, secrets: Set<String>) =
        result(id, index, WorkbenchAssertionOutcome.ERROR, message, secrets)

    private fun result(
        id: String,
        index: Int,
        outcome: WorkbenchAssertionOutcome,
        message: String,
        secrets: Set<String>
    ) = WorkbenchAssertionResult(
        assertionId = id,
        assertionIndex = index,
        outcome = outcome,
        message = redact(message, secrets)
    )

    private fun redact(message: String, secrets: Set<String>): String =
        secretRedactor.redactText(message, secrets).take(MAX_DIAGNOSTIC_CHARACTERS)

    private inner class LazyJsonDocument(
        private val body: String,
        val bodyBudgetExceeded: Boolean
    ) {
        private var cached: JsonLoadResult? = null

        suspend fun load(): JsonLoadResult {
            cached?.let { return it }
            if (bodyBudgetExceeded) {
                return JsonLoadResult.Invalid("Response body exceeds the safe assertion limit.").also {
                    cached = it
                }
            }
            inspectJsonBudget(body)?.let { message ->
                return JsonLoadResult.Invalid(message).also { cached = it }
            }
            return parseJson(body, "Response body is not valid JSON.").also { cached = it }
        }
    }

    private sealed interface JsonLoadResult {
        data class Success(val element: JsonElement) : JsonLoadResult
        data class Invalid(val message: String) : JsonLoadResult
    }

    private sealed interface JsonPathParseResult {
        data class Valid(val segments: List<JsonPathSegment>) : JsonPathParseResult
        data class Invalid(val message: String) : JsonPathParseResult
    }

    private sealed interface BracketParseResult {
        data class Valid(val segment: JsonPathSegment, val nextCursor: Int) : BracketParseResult
        data class Invalid(val message: String) : BracketParseResult
    }

    private sealed interface JsonPathSegment {
        data class Property(val name: String) : JsonPathSegment
        data class ArrayIndex(val index: Int) : JsonPathSegment
    }

    private data class DecodedEscape(val value: Char, val nextCursor: Int)
    private data class PathResolution(val found: Boolean, val value: JsonElement?)

    private companion object {
        val HTTP_STATUS_RANGE = 100..599
        const val RUN_ERROR_ID = "assertion-run"
        const val MAX_DIAGNOSTIC_CHARACTERS = 512
        const val CANCELLATION_CHECK_MASK = 0x1fff
    }
}
