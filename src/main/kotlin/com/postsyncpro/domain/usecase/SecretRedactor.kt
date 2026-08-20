package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchExecutionFailure
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Central redaction policy shared by execution, diagnostics and persistence. */
class SecretRedactor(
    private val replacement: String = "<redacted>"
) {
    private val json = Json { isLenient = false }
    private val sensitiveUrlParameter = Regex(
        "((?:^|[?&])(?:access[_-]?token|refresh[_-]?token|token|api[_-]?key|key|secret|password|passwd|signature|authorization)=)([^&#\\s]*)",
        RegexOption.IGNORE_CASE
    )
    private val urlUserInfo = Regex(
        "(?i)\\b(https?://)([^/?#@\\s]+)@"
    )
    private val sensitiveJsonField = Regex(
        """(?i)("(?:authorization|cookie|set[_-]?cookie|password|passwd|access[_-]?token|refresh[_-]?token|id[_-]?token|client[_-]?secret|api[_-]?key|secret|signature|session(?:id)?)"\s*:\s*)("(?:\\.|[^"\\])*"|[^,}\]\s]+)"""
    )

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().filter { it.isLetterOrDigit() }
        return normalized in exactSensitiveKeys ||
            normalized.endsWith("token") ||
            normalized.endsWith("secret") ||
            normalized.endsWith("password") ||
            normalized.endsWith("apikey")
    }

    fun redactText(value: String, explicitSecrets: Set<String> = emptySet()): String {
        val bounded = value.take(MAX_REDACTED_TEXT_CHARS)
        val explicitRedacted = redactExplicitSecretsOnePass(
            bounded,
            explicitSecrets,
            MAX_REDACTED_TEXT_CHARS,
            truncateOnOverflow = true
        )
        return redactStructuralText(
            explicitRedacted,
            value.length > bounded.length,
            MAX_REDACTED_TEXT_CHARS,
            truncateOnOverflow = true
        )
    }

    fun redactBody(body: String, explicitSecrets: Set<String> = emptySet()): String =
        redactBodyWithin(
            body,
            explicitSecrets,
            MAX_REDACTED_TEXT_CHARS,
            truncateOnOverflow = true
        )

    /** Persistence variant: returns a complete sanitized body or fails before a store can write it. */
    fun redactBodyForPersistence(
        body: String,
        explicitSecrets: Set<String> = emptySet(),
        maxOutputCharacters: Int
    ): String {
        require(maxOutputCharacters in 1..MAX_PERSISTENCE_REDACTED_CHARS) {
            "Persistence redaction limit is invalid"
        }
        return redactBodyWithin(
            body,
            explicitSecrets,
            maxOutputCharacters,
            truncateOnOverflow = false
        )
    }

    private fun redactBodyWithin(
        body: String,
        explicitSecrets: Set<String>,
        maxOutputCharacters: Int,
        truncateOnOverflow: Boolean
    ): String {
        if (!truncateOnOverflow && body.length > maxOutputCharacters) {
            throw IllegalArgumentException("Request body exceeds the safe persistence redaction limit")
        }
        val bounded = body.take(maxOutputCharacters)
        val explicitRedacted = redactExplicitSecretsOnePass(
            bounded,
            explicitSecrets,
            maxOutputCharacters,
            truncateOnOverflow
        )
        val parsed = if (hasSafeJsonDepth(explicitRedacted)) {
            runCatching { json.parseToJsonElement(explicitRedacted) }.getOrNull()
        } else null
        val keyRedacted = if (parsed == null) explicitRedacted else redactJson(parsed).toString()
        val fallbackRedacted = sensitiveJsonField.replace(keyRedacted) {
            "${it.groupValues[1]}\"$replacement\""
        }
        return boundedOutput(
            redactStructuralText(
                fallbackRedacted,
                maxOutputCharacters = maxOutputCharacters,
                truncateOnOverflow = truncateOnOverflow
            ),
            body.length > bounded.length,
            maxOutputCharacters,
            truncateOnOverflow
        )
    }

    fun redactHeaders(
        headers: List<WorkbenchRequestField>,
        explicitSecrets: Set<String> = emptySet()
    ): List<WorkbenchRequestField> = headers.map { header ->
        header.copy(
            value = if (header.sensitive || isSensitiveKey(header.key)) {
                replacement
            } else {
                redactText(header.value, explicitSecrets)
            },
            sensitive = header.sensitive || isSensitiveKey(header.key)
        )
    }

    fun sanitizeHistoryEntry(entry: WorkbenchHistoryEntry): WorkbenchHistoryEntry {
        val secrets = collectSecrets(entry.request, entry.variables)
        return entry.copy(
            request = sanitizeDraft(entry.request, secrets),
            variables = entry.variables.mapValues { (_, variable) ->
                if (variable.secret) variable.copy(value = "")
                else variable.copy(value = redactText(variable.value, secrets))
            },
            response = entry.response?.let { sanitizeResponse(it, secrets) },
            failure = entry.failure?.let {
                WorkbenchExecutionFailure(it.kind, redactText(it.message, secrets))
            },
            assertions = entry.assertions.map { sanitizeAssertion(it, secrets) },
            assertionResults = entry.assertionResults.map { result ->
                result.copy(
                    assertionId = redactText(result.assertionId, secrets),
                    message = redactText(result.message, secrets)
                )
            }
        )
    }

    /** Removes credential-shaped operands before declarative checks cross a persistence boundary. */
    fun sanitizeAssertion(
        assertion: WorkbenchAssertion,
        explicitSecrets: Set<String> = emptySet()
    ): WorkbenchAssertion {
        fun safe(value: String) = redactText(value, explicitSecrets)
        fun changed(value: String, sanitized: String) = value != sanitized
        return when (assertion) {
            is WorkbenchAssertion.StatusEquals -> assertion.copy(id = safe(assertion.id))
            is WorkbenchAssertion.StatusRange -> assertion.copy(id = safe(assertion.id))
            is WorkbenchAssertion.ResponseTimeMax -> assertion.copy(id = safe(assertion.id))
            is WorkbenchAssertion.HeaderExists -> assertion.copy(
                id = safe(assertion.id),
                name = safe(assertion.name)
            )
            is WorkbenchAssertion.HeaderEquals -> {
                val name = safe(assertion.name)
                val expected = safe(assertion.expected)
                val nameLost = changed(assertion.name, name)
                val sensitive = assertion.sensitive || isSensitiveKey(assertion.name) ||
                    nameLost || changed(assertion.expected, expected)
                assertion.copy(
                    id = safe(assertion.id),
                    enabled = assertion.enabled && !nameLost,
                    name = name,
                    expected = if (sensitive) "" else expected,
                    sensitive = sensitive
                )
            }
            is WorkbenchAssertion.HeaderContains -> {
                val name = safe(assertion.name)
                val expected = safe(assertion.expected)
                val nameLost = changed(assertion.name, name)
                val sensitive = assertion.sensitive || isSensitiveKey(assertion.name) ||
                    nameLost || changed(assertion.expected, expected)
                assertion.copy(
                    id = safe(assertion.id),
                    enabled = assertion.enabled && !nameLost,
                    name = name,
                    expected = if (sensitive) "" else expected,
                    sensitive = sensitive
                )
            }
            is WorkbenchAssertion.BodyContains -> {
                val expected = safe(assertion.expected)
                val sensitive = assertion.sensitive || changed(assertion.expected, expected)
                assertion.copy(
                    id = safe(assertion.id),
                    expected = if (sensitive) "" else expected,
                    sensitive = sensitive
                )
            }
            is WorkbenchAssertion.JsonPathExists -> assertion.copy(
                id = safe(assertion.id),
                path = safe(assertion.path)
            )
            is WorkbenchAssertion.JsonPathEquals -> {
                val path = safe(assertion.path)
                val expected = safe(assertion.expectedJson)
                val pathLost = changed(assertion.path, path)
                val sensitive = assertion.sensitive || jsonPathTargetsSensitiveValue(assertion.path) ||
                    pathLost || changed(assertion.expectedJson, expected)
                assertion.copy(
                    id = safe(assertion.id),
                    enabled = assertion.enabled && !pathLost,
                    path = path,
                    expectedJson = if (sensitive) "" else expected,
                    sensitive = sensitive
                )
            }
        }
    }

    fun sanitizeResponse(response: WorkbenchResponse, secrets: Set<String>): WorkbenchResponse =
        response.copy(
            headers = redactHeaders(response.headers, secrets),
            body = redactBody(response.body, secrets)
        )

    fun collectSecrets(
        request: WorkbenchRequestDraft,
        variables: Map<String, com.postsyncpro.domain.model.WorkbenchVariable> = emptyMap()
    ): Set<String> = buildSet {
        variables.values.filter { it.secret }.mapTo(this) { it.value }
        request.query.filter { it.sensitive || isSensitiveKey(it.key) }.mapTo(this) { it.value }
        request.headers.filter { it.sensitive || isSensitiveKey(it.key) }.mapTo(this) { it.value }
        sensitiveUrlParameter.findAll(request.url).forEach { match ->
            val encoded = match.groupValues[2]
            add(encoded)
            runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8) }
                .getOrNull()
                ?.let { add(it) }
        }
        urlUserInfo.findAll(request.url).forEach { match ->
            val encoded = match.groupValues[2]
            add(encoded)
            encoded.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { add(it) }
            runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8) }
                .getOrNull()
                ?.let { decoded ->
                    add(decoded)
                    decoded.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { add(it) }
                }
        }
        if (request.bodyEnabled) {
            if (request.bodySensitive && request.body.isNotEmpty()) add(request.body)
            addAll(collectCredentialBodyValues(request.body))
        }
        when (val auth = request.auth) {
            WorkbenchAuth.None -> Unit
            is WorkbenchAuth.Bearer -> add(auth.token)
            is WorkbenchAuth.Basic -> {
                add(auth.password)
                val basic = java.util.Base64.getEncoder().encodeToString(
                    "${auth.username}:${auth.password}".toByteArray(Charsets.UTF_8)
                )
                add(basic)
            }
            is WorkbenchAuth.ApiKey -> add(auth.value)
        }
    }.filterTo(linkedSetOf()) { it.isNotEmpty() }

    private fun sanitizeDraft(request: WorkbenchRequestDraft, secrets: Set<String>) = request.copy(
        url = redactText(request.url, secrets),
        query = request.query.map { field ->
            field.copy(
                value = if (field.sensitive || isSensitiveKey(field.key)) replacement
                else redactText(field.value, secrets)
            )
        },
        headers = redactHeaders(request.headers, secrets),
        body = if (request.bodySensitive && request.body.isNotEmpty()) replacement
        else redactBody(request.body, secrets),
        auth = when (val auth = request.auth) {
            WorkbenchAuth.None -> WorkbenchAuth.None
            is WorkbenchAuth.Bearer -> auth.copy(token = "")
            is WorkbenchAuth.Basic -> auth.copy(password = "")
            is WorkbenchAuth.ApiKey -> auth.copy(value = "")
        }
    )

    private fun redactJson(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (isSensitiveKey(key)) JsonPrimitive(replacement) else redactJson(value)
        })
        is JsonArray -> JsonArray(element.map(::redactJson))
        else -> element
    }

    private fun hasSafeJsonDepth(input: String, maxDepth: Int = 128): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        input.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> if (++depth > maxDepth) return false
                    '}', ']' -> depth--
                }
            }
        }
        return true
    }

    /**
     * Replaces secrets against the original input only. Emitted markers are never scanned again,
     * so short secrets such as `e` cannot recursively expand `<redacted>`.
     */
    private fun redactExplicitSecretsOnePass(
        value: String,
        explicitSecrets: Set<String>,
        maxOutputCharacters: Int,
        truncateOnOverflow: Boolean
    ): String {
        val patterns = explicitSecrets.asSequence()
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (patterns.isEmpty() || value.isEmpty()) return value
        val totalPatternCharacters = patterns.sumOf { it.length.toLong() }
        if (patterns.size > MAX_SECRET_PATTERNS ||
            patterns.any { it.length > MAX_SINGLE_SECRET_CHARACTERS } ||
            totalPatternCharacters > MAX_SECRET_PATTERN_CHARACTERS
        ) {
            return replacement
        }

        val root = SecretTrieNode()
        patterns.forEach { pattern ->
            var node = root
            pattern.forEach { character ->
                node = node.children.getOrPut(character, ::SecretTrieNode)
            }
            node.terminal = true
        }

        val maximumSteps = minOf(
            MAX_SECRET_MATCH_STEPS.toLong(),
            value.length.toLong() * SECRET_MATCH_STEP_MULTIPLIER + totalPatternCharacters
        )
        var steps = 0L
        var index = 0
        val output = StringBuilder(minOf(value.length, maxOutputCharacters))
        while (index < value.length && output.length < maxOutputCharacters) {
            var node = root
            var cursor = index
            var longestMatch = 0
            while (cursor < value.length) {
                val next = node.children[value[cursor]] ?: break
                node = next
                cursor++
                if (++steps > maximumSteps) return replacement
                if (node.terminal) longestMatch = cursor - index
            }
            if (longestMatch > 0) {
                appendBounded(output, replacement, maxOutputCharacters)
                index += longestMatch
            } else {
                output.append(value[index])
                index++
            }
        }
        return boundedOutput(
            output.toString(),
            index < value.length,
            maxOutputCharacters,
            truncateOnOverflow
        )
    }

    private fun redactStructuralText(
        value: String,
        truncated: Boolean = false,
        maxOutputCharacters: Int,
        truncateOnOverflow: Boolean
    ): String {
        var redacted = urlUserInfo.replace(value) { "${it.groupValues[1]}$replacement@" }
        redacted = sensitiveUrlParameter.replace(redacted) { "${it.groupValues[1]}$replacement" }
        return boundedOutput(redacted, truncated, maxOutputCharacters, truncateOnOverflow)
    }

    private fun collectCredentialBodyValues(body: String): Set<String> {
        if (body.isBlank()) return emptySet()
        val bounded = body.take(MAX_BODY_SECRET_SCAN_CHARS)
        val values = linkedSetOf<String>()
        val parsed = if (hasSafeJsonDepth(bounded)) {
            runCatching { json.parseToJsonElement(bounded) }.getOrNull()
        } else null
        if (parsed != null) {
            val stack = ArrayDeque<Pair<JsonElement, Boolean>>()
            stack.add(parsed to false)
            var visited = 0
            while (stack.isNotEmpty() && visited++ < MAX_BODY_JSON_NODES) {
                val (element, inheritedSensitive) = stack.removeLast()
                when (element) {
                    is JsonObject -> element.forEach { (key, child) ->
                        stack.add(child to (inheritedSensitive || isSensitiveKey(key)))
                    }
                    is JsonArray -> element.forEach { child -> stack.add(child to inheritedSensitive) }
                    is JsonPrimitive -> if (inheritedSensitive && element.content.isNotEmpty()) {
                        values += element.content
                    }
                }
            }
        }

        formField.findAll(bounded).forEach { match ->
            val rawKey = match.groupValues[1]
            val decodedKey = decodeUrlComponent(rawKey)
            if (isSensitiveKey(decodedKey)) {
                val rawValue = match.groupValues[2]
                if (rawValue.isNotEmpty()) values += rawValue
                decodeUrlComponent(rawValue).takeIf { it.isNotEmpty() }?.let(values::add)
            }
        }
        if ('<' in bounded) {
            xmlElement.findAll(bounded).forEach { match ->
                if (isSensitiveKey(match.groupValues[1])) {
                    match.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let(values::add)
                }
            }
            xmlAttribute.findAll(bounded).forEach { match ->
                if (isSensitiveKey(match.groupValues[1])) {
                    match.groupValues[3].takeIf { it.isNotEmpty() }?.let(values::add)
                }
            }
        }
        return values
    }

    private fun decodeUrlComponent(value: String): String = runCatching {
        java.net.URLDecoder.decode(value, Charsets.UTF_8)
    }.getOrDefault(value)

    private fun appendBounded(output: StringBuilder, value: String, maxOutputCharacters: Int) {
        val remaining = maxOutputCharacters - output.length
        if (remaining > 0) output.append(value, 0, minOf(value.length, remaining))
    }

    private fun boundedOutput(
        value: String,
        truncated: Boolean,
        maxOutputCharacters: Int,
        truncateOnOverflow: Boolean
    ): String {
        if (!truncated && value.length <= maxOutputCharacters) return value
        if (!truncateOnOverflow) {
            throw IllegalArgumentException("Sanitized request body exceeds the persistence limit")
        }
        val suffix = "<truncated>"
        val prefixLength = (maxOutputCharacters - suffix.length).coerceAtLeast(0)
        return value.take(prefixLength) + suffix.take(maxOutputCharacters - prefixLength)
    }

    private class SecretTrieNode {
        val children = HashMap<Char, SecretTrieNode>()
        var terminal: Boolean = false
    }

    private fun jsonPathTargetsSensitiveValue(path: String): Boolean {
        val bounded = path.take(4_096).trimEnd()
        val lastDot = bounded.substringAfterLast('.', "")
            .substringBefore('[')
            .trim(' ', '\'', '"')
        if (lastDot.isNotBlank() && isSensitiveKey(lastDot)) return true
        val bracketKey = bounded.substringAfterLast('[', "")
            .substringBefore(']')
            .trim(' ', '\'', '"')
        return bracketKey.isNotBlank() && bracketKey.any(Char::isLetter) && isSensitiveKey(bracketKey)
    }

    private companion object {
        const val MAX_REDACTED_TEXT_CHARS = 2 * 1024 * 1024
        const val MAX_PERSISTENCE_REDACTED_CHARS = 16 * 1024 * 1024
        const val MAX_SECRET_PATTERNS = 1_000
        const val MAX_SINGLE_SECRET_CHARACTERS = 4 * 1024
        const val MAX_SECRET_PATTERN_CHARACTERS = 256L * 1024L
        const val MAX_SECRET_MATCH_STEPS = 16_000_000
        const val SECRET_MATCH_STEP_MULTIPLIER = 8L
        const val MAX_BODY_SECRET_SCAN_CHARS = 2 * 1024 * 1024
        const val MAX_BODY_JSON_NODES = 100_000
        val formField = Regex("(?:^|[&;])([^=&;\\s]{1,256})=([^&;\\s]*)")
        val xmlElement = Regex(
            "<([A-Za-z_][A-Za-z0-9_.:-]{0,127})[^>]*>([^<]{0,65536})</\\1\\s*>",
            RegexOption.IGNORE_CASE
        )
        val xmlAttribute = Regex(
            "([A-Za-z_][A-Za-z0-9_.:-]{0,127})\\s*=\\s*([\"'])([^\"']{0,65536})\\2",
            RegexOption.IGNORE_CASE
        )
        val exactSensitiveKeys = setOf(
            "authorization",
            "proxyauthorization",
            "cookie",
            "setcookie",
            "password",
            "passwd",
            "accesstoken",
            "refreshtoken",
            "idtoken",
            "clientsecret",
            "apikey",
            "xapikey",
            "secret",
            "signature",
            "session",
            "sessionid"
        )
    }
}
