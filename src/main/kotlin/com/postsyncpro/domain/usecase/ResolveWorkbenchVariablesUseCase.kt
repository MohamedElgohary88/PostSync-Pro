package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.VariableResolutionError
import com.postsyncpro.domain.model.VariableResolutionResult
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchVariable

class ResolveWorkbenchVariablesUseCase(
    private val maxExpansionDepth: Int = 20,
    private val maxExpandedCharacters: Int = 5 * 1024 * 1024,
    private val maxExpansionOperations: Int = 10_000,
    private val redactor: SecretRedactor = SecretRedactor()
) {
    private val template = Regex("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}")

    init {
        require(maxExpansionDepth in 1..100)
        require(maxExpandedCharacters > 0)
        require(maxExpansionOperations > 0)
    }

    operator fun invoke(
        draft: WorkbenchRequestDraft,
        variables: Map<String, WorkbenchVariable>
    ): VariableResolutionResult {
        val errors = linkedSetOf<VariableResolutionError>()
        val sensitiveValues = linkedSetOf<String>()
        val expansionOperations = intArrayOf(0)

        fun expand(value: String, location: String): String = expandTemplate(
            input = value,
            location = location,
            variables = variables,
            stack = emptyList(),
            depth = 0,
            errors = errors,
            sensitiveValues = sensitiveValues,
            expansionOperations = expansionOperations
        )

        fun resolveFields(fields: List<WorkbenchRequestField>, location: String) =
            fields.mapIndexed { index, field ->
                if (!field.enabled) return@mapIndexed field
                val resolved = field.copy(
                    key = expand(field.key, "$location[$index].key"),
                    value = expand(field.value, "$location[$index].value")
                )
                if (field.sensitive && resolved.value.isNotEmpty()) {
                    sensitiveValues += resolved.value
                }
                resolved
            }

        val resolvedAuth = when (val auth = draft.auth) {
            WorkbenchAuth.None -> WorkbenchAuth.None
            is WorkbenchAuth.Bearer -> WorkbenchAuth.Bearer(
                expand(auth.token, "auth.bearer").also {
                    if (it.isNotEmpty()) sensitiveValues += it
                }
            )
            is WorkbenchAuth.Basic -> WorkbenchAuth.Basic(
                username = expand(auth.username, "auth.username"),
                password = expand(auth.password, "auth.password").also {
                    if (it.isNotEmpty()) sensitiveValues += it
                }
            )
            is WorkbenchAuth.ApiKey -> WorkbenchAuth.ApiKey(
                name = expand(auth.name, "auth.apiKey.name"),
                value = expand(auth.value, "auth.apiKey.value").also {
                    if (it.isNotEmpty()) sensitiveValues += it
                },
                location = auth.location
            )
        }

        val resolved = draft.copy(
            name = expand(draft.name, "name"),
            url = expand(draft.url, "url"),
            query = resolveFields(draft.query, "query"),
            headers = resolveFields(draft.headers, "headers"),
            body = expand(draft.body, "body"),
            auth = resolvedAuth
        )

        return if (errors.isEmpty()) {
            VariableResolutionResult.Success(
                ResolvedWorkbenchRequest(resolved, sensitiveValues.filterTo(linkedSetOf()) { it.isNotEmpty() })
            )
        } else {
            VariableResolutionResult.Failure(errors.toList())
        }
    }

    private fun expandTemplate(
        input: String,
        location: String,
        variables: Map<String, WorkbenchVariable>,
        stack: List<String>,
        depth: Int,
        errors: MutableSet<VariableResolutionError>,
        sensitiveValues: MutableSet<String>,
        expansionOperations: IntArray
    ): String {
        if (input.length > maxExpandedCharacters) {
            errors += VariableResolutionError.ExpansionLimit(stack.lastOrNull() ?: "<template>", location)
            return input.take(maxExpandedCharacters)
        }

        val expanded = template.replace(input) { match ->
            val name = match.groupValues[1]
            expansionOperations[0]++
            if (expansionOperations[0] > maxExpansionOperations) {
                errors += VariableResolutionError.ExpansionLimit(name, location)
                return@replace match.value
            }
            val variable = variables[name]
            if (variable == null || !variable.enabled) {
                errors += VariableResolutionError.Missing(name, location)
                return@replace match.value
            }

            val cycleAt = stack.indexOf(name)
            if (cycleAt >= 0) {
                errors += VariableResolutionError.Cycle(stack.drop(cycleAt) + name, location)
                return@replace match.value
            }

            if (depth >= maxExpansionDepth) {
                errors += VariableResolutionError.ExpansionLimit(name, location)
                return@replace match.value
            }

            val value = expandTemplate(
                input = variable.value,
                location = location,
                variables = variables,
                stack = stack + name,
                depth = depth + 1,
                errors = errors,
                sensitiveValues = sensitiveValues,
                expansionOperations = expansionOperations
            )
            if ((variable.secret || redactor.isSensitiveKey(name)) && value.isNotEmpty()) {
                sensitiveValues += value
            }
            value
        }

        if (expanded.length > maxExpandedCharacters) {
            errors += VariableResolutionError.ExpansionLimit(stack.lastOrNull() ?: "<template>", location)
            return expanded.take(maxExpandedCharacters)
        }
        return expanded
    }
}
