package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*

class AnalyzeCoverageUseCase {

    fun analyze(
        srsText: String,
        extractionResults: Map<String, SrsExtractionResult>,
        validationReports: Map<String, ValidationReport>
    ): CoverageMatrix {
        // Build requirements from SRS sections
        val requirements = buildRequirements(srsText, extractionResults)
        val endpoints = validationReports.keys.toList()

        val cells = mutableListOf<CoverageCell>()

        for (req in requirements) {
            for (endpoint in endpoints) {
                val report = validationReports[endpoint]
                if (report == null) {
                    cells.add(CoverageCell(
                        requirementId = req.id,
                        endpoint = endpoint,
                        status = CoverageStatus.NOT_APPLICABLE,
                        score = 0f
                    ))
                    continue
                }

                // Check if this requirement's fields are relevant to this endpoint
                val matchedFields = req.fields.filter { field ->
                    report.matches.any { it.fieldName.equals(field, ignoreCase = true) }
                }
                val conflictFields = req.fields.filter { field ->
                    report.typeConflicts.any { it.fieldName.equals(field, ignoreCase = true) }
                }
                val missingFields = req.fields.filter { field ->
                    report.missingInBackend.any { it.fieldName.equals(field, ignoreCase = true) }
                }

                val relevantCount = matchedFields.size + conflictFields.size + missingFields.size

                if (relevantCount == 0) {
                    cells.add(CoverageCell(
                        requirementId = req.id,
                        endpoint = endpoint,
                        status = CoverageStatus.NOT_APPLICABLE,
                        score = 0f
                    ))
                } else {
                    val score = matchedFields.size.toFloat() / relevantCount
                    val status = when {
                        score >= 0.9f -> CoverageStatus.FULLY_COVERED
                        score >= 0.3f -> CoverageStatus.PARTIALLY_COVERED
                        else -> CoverageStatus.NOT_COVERED
                    }
                    val details = buildString {
                        if (matchedFields.isNotEmpty()) append("✓ ${matchedFields.joinToString()}")
                        if (missingFields.isNotEmpty()) append(" ✗ Missing: ${missingFields.joinToString()}")
                        if (conflictFields.isNotEmpty()) append(" ⚠ Conflict: ${conflictFields.joinToString()}")
                    }
                    cells.add(CoverageCell(
                        requirementId = req.id,
                        endpoint = endpoint,
                        status = status,
                        score = score,
                        details = details
                    ))
                }
            }
        }

        val coveredCells = cells.filter { it.status != CoverageStatus.NOT_APPLICABLE }
        val overallCoverage = if (coveredCells.isNotEmpty()) {
            coveredCells.map { it.score }.average().toFloat()
        } else 0f

        return CoverageMatrix(
            requirements = requirements,
            endpoints = endpoints,
            cells = cells,
            overallCoverage = overallCoverage
        )
    }

    private fun buildRequirements(
        srsText: String,
        extractionResults: Map<String, SrsExtractionResult>
    ): List<SrsRequirement> {
        val requirements = mutableListOf<SrsRequirement>()
        var reqIndex = 1

        // Group rules by their likely section
        val sectionKeywords = mapOf(
            "Authentication" to listOf("auth", "login", "register", "password", "token"),
            "User Management" to listOf("user", "profile", "avatar", "phone", "email"),
            "Product Management" to listOf("product", "price", "category", "stock", "image"),
            "Order Management" to listOf("order", "cart", "checkout", "shipping"),
            "Wishlist" to listOf("wishlist", "favorite"),
            "Reviews" to listOf("review", "rating", "comment"),
            "Coupons" to listOf("coupon", "discount", "promo")
        )

        // Collect unique fields from all extractions grouped by section
        val sectionFields = mutableMapOf<String, MutableSet<String>>()

        for ((_, result) in extractionResults) {
            for (rule in result.rules) {
                val section = sectionKeywords.entries.find { (_, keywords) ->
                    keywords.any { kw -> rule.fieldName.contains(kw, ignoreCase = true) ||
                            rule.description.contains(kw, ignoreCase = true) }
                }?.key ?: "General"

                sectionFields.getOrPut(section) { mutableSetOf() }.add(rule.fieldName)
            }
        }

        for ((section, fields) in sectionFields) {
            requirements.add(SrsRequirement(
                id = "REQ-${String.format("%03d", reqIndex++)}",
                section = section,
                description = "$section requirements (${fields.size} fields)",
                fields = fields.toList()
            ))
        }

        return requirements
    }
}
