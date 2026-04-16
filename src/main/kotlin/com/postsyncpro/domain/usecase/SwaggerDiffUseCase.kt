package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*

class SwaggerDiffUseCase {

    fun compare(oldSpec: SwaggerSpec, newSpec: SwaggerSpec): SwaggerDiffResult {
        val oldEndpoints = oldSpec.paths.flatMap { (path, methods) ->
            methods.keys.map { method -> "$method:$path" to (path to method) }
        }.toMap()

        val newEndpoints = newSpec.paths.flatMap { (path, methods) ->
            methods.keys.map { method -> "$method:$path" to (path to method) }
        }.toMap()

        val addedKeys = newEndpoints.keys - oldEndpoints.keys
        val removedKeys = oldEndpoints.keys - newEndpoints.keys
        val commonKeys = oldEndpoints.keys.intersect(newEndpoints.keys)

        val addedEndpoints = addedKeys.map { key ->
            val (path, method) = newEndpoints[key]!!
            val op = newSpec.paths[path]?.get(method)
            EndpointChange(method.uppercase(), path, op?.summary ?: "")
        }

        val removedEndpoints = removedKeys.map { key ->
            val (path, method) = oldEndpoints[key]!!
            val op = oldSpec.paths[path]?.get(method)
            EndpointChange(method.uppercase(), path, op?.summary ?: "")
        }

        val modifiedEndpoints = commonKeys.mapNotNull { key ->
            val (path, method) = oldEndpoints[key]!!
            val oldOp = oldSpec.paths[path]?.get(method) ?: return@mapNotNull null
            val newOp = newSpec.paths[path]?.get(method) ?: return@mapNotNull null
            val changes = compareOperation(oldOp, newOp, oldSpec, newSpec)
            if (changes.isNotEmpty()) {
                EndpointModification(method.uppercase(), path, changes)
            } else null
        }

        val breakingCount = removedEndpoints.size +
                modifiedEndpoints.sumOf { mod -> mod.changes.count { it.isBreaking } }
        val nonBreakingCount = addedEndpoints.size +
                modifiedEndpoints.sumOf { mod -> mod.changes.count { !it.isBreaking } }

        val severity = when {
            removedEndpoints.isNotEmpty() -> DiffSeverity.CRITICAL
            breakingCount > 5 -> DiffSeverity.HIGH
            breakingCount > 0 -> DiffSeverity.MEDIUM
            nonBreakingCount > 0 -> DiffSeverity.LOW
            else -> DiffSeverity.NONE
        }

        return SwaggerDiffResult(
            oldVersion = oldSpec.info.version,
            newVersion = newSpec.info.version,
            addedEndpoints = addedEndpoints,
            removedEndpoints = removedEndpoints,
            modifiedEndpoints = modifiedEndpoints,
            breakingChangesCount = breakingCount,
            nonBreakingChangesCount = nonBreakingCount,
            severity = severity
        )
    }

    private fun compareOperation(
        old: SwaggerOperation,
        new: SwaggerOperation,
        oldSpec: SwaggerSpec,
        newSpec: SwaggerSpec
    ): List<FieldChange> {
        val changes = mutableListOf<FieldChange>()

        // Compare request body schemas
        val oldFields = extractAllFields(old.requestBody, oldSpec, "request")
        val newFields = extractAllFields(new.requestBody, newSpec, "request")
        changes.addAll(diffFields(oldFields, newFields, "request"))

        // Compare response schemas
        val oldRespFields = extractResponseFields(old, oldSpec)
        val newRespFields = extractResponseFields(new, newSpec)
        changes.addAll(diffFields(oldRespFields, newRespFields, "response"))

        // Compare parameters
        val oldParams = old.parameters.associate { it.name to it }
        val newParams = new.parameters.associate { it.name to it }

        for (name in oldParams.keys - newParams.keys) {
            val param = oldParams[name]!!
            changes.add(FieldChange(
                field = "param:$name",
                changeType = ChangeType.FIELD_REMOVED,
                oldValue = param.schema?.type,
                newValue = null,
                isBreaking = param.required,
                description = "Parameter '$name' removed${if (param.required) " (was required)" else ""}"
            ))
        }

        for (name in newParams.keys - oldParams.keys) {
            val param = newParams[name]!!
            changes.add(FieldChange(
                field = "param:$name",
                changeType = ChangeType.FIELD_ADDED,
                oldValue = null,
                newValue = param.schema?.type,
                isBreaking = param.required,
                description = "Parameter '$name' added${if (param.required) " (required)" else " (optional)"}"
            ))
        }

        return changes
    }

    private fun extractAllFields(
        requestBody: SwaggerRequestBody?,
        spec: SwaggerSpec,
        prefix: String
    ): Map<String, FieldInfo> {
        val fields = mutableMapOf<String, FieldInfo>()
        requestBody?.content?.get("application/json")?.schema?.let { schema ->
            val resolved = resolveRef(schema, spec)
            flattenSchema(resolved, prefix, fields, resolved.required.toSet())
        }
        return fields
    }

    private fun extractResponseFields(
        operation: SwaggerOperation,
        spec: SwaggerSpec
    ): Map<String, FieldInfo> {
        val fields = mutableMapOf<String, FieldInfo>()
        val resp = operation.responses["200"] ?: operation.responses["201"]
        resp?.content?.get("application/json")?.schema?.let { schema ->
            val resolved = resolveRef(schema, spec)
            flattenSchema(resolved, "response", fields, resolved.required.toSet())
        }
        return fields
    }

    private fun flattenSchema(
        schema: SwaggerSchema,
        prefix: String,
        fields: MutableMap<String, FieldInfo>,
        requiredSet: Set<String>
    ) {
        for ((name, prop) in schema.properties) {
            val key = "$prefix.$name"
            fields[key] = FieldInfo(prop.type, name in requiredSet)
            if (prop.type == "object" && prop.properties.isNotEmpty()) {
                flattenSchema(prop, key, fields, prop.required.toSet())
            }
        }
    }

    private fun diffFields(
        old: Map<String, FieldInfo>,
        new: Map<String, FieldInfo>,
        context: String
    ): List<FieldChange> {
        val changes = mutableListOf<FieldChange>()

        for (key in old.keys - new.keys) {
            changes.add(FieldChange(
                field = key, changeType = ChangeType.FIELD_REMOVED,
                oldValue = old[key]?.type, newValue = null,
                isBreaking = true,
                description = "Field '$key' removed from $context"
            ))
        }

        for (key in new.keys - old.keys) {
            val info = new[key]!!
            changes.add(FieldChange(
                field = key, changeType = ChangeType.FIELD_ADDED,
                oldValue = null, newValue = info.type,
                isBreaking = info.required,
                description = "Field '$key' added to $context${if (info.required) " (required)" else ""}"
            ))
        }

        for (key in old.keys.intersect(new.keys)) {
            val oldInfo = old[key]!!
            val newInfo = new[key]!!

            if (oldInfo.type != newInfo.type) {
                changes.add(FieldChange(
                    field = key, changeType = ChangeType.TYPE_CHANGED,
                    oldValue = oldInfo.type, newValue = newInfo.type,
                    isBreaking = true,
                    description = "Type changed: '${oldInfo.type}' → '${newInfo.type}'"
                ))
            }

            if (!oldInfo.required && newInfo.required) {
                changes.add(FieldChange(
                    field = key, changeType = ChangeType.REQUIRED_ADDED,
                    oldValue = "optional", newValue = "required",
                    isBreaking = true,
                    description = "Field '$key' became required"
                ))
            } else if (oldInfo.required && !newInfo.required) {
                changes.add(FieldChange(
                    field = key, changeType = ChangeType.REQUIRED_REMOVED,
                    oldValue = "required", newValue = "optional",
                    isBreaking = false,
                    description = "Field '$key' became optional"
                ))
            }
        }

        return changes
    }

    private fun resolveRef(schema: SwaggerSchema, spec: SwaggerSpec): SwaggerSchema {
        if (schema.ref.isNotEmpty()) {
            val refName = schema.ref.substringAfterLast("/")
            return spec.components?.schemas?.get(refName) ?: schema
        }
        return schema
    }

    private data class FieldInfo(val type: String, val required: Boolean)
}
