package com.postsyncpro.domain.model

data class ValidationReport(
    val endpointPath: String,
    val method: String,
    val matches: List<FieldMatch>,
    val missingInBackend: List<FieldMismatch>,
    val missingInSrs: List<FieldMismatch>,
    val typeConflicts: List<TypeConflict>,
    val overallScore: Float // 0.0 to 1.0
) {
    val totalIssues: Int get() = missingInBackend.size + missingInSrs.size + typeConflicts.size
}

data class FieldMatch(
    val fieldName: String,
    val srsType: String,
    val swaggerType: String,
    val isRequired: Boolean
)

data class FieldMismatch(
    val fieldName: String,
    val source: MismatchSource,
    val expectedType: String = "",
    val isRequired: Boolean = false,
    val description: String = ""
)

enum class MismatchSource {
    SRS_ONLY,      // exists in SRS but not in Swagger
    SWAGGER_ONLY   // exists in Swagger but not in SRS
}

data class TypeConflict(
    val fieldName: String,
    val srsType: String,
    val swaggerType: String,
    val srsRequired: Boolean,
    val swaggerRequired: Boolean,
    val description: String = ""
)
