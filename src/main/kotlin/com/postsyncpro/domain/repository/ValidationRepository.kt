package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*

interface ValidationRepository {
    suspend fun validateSrsAgainstSwagger(
        rules: List<SrsRule>,
        swaggerSpec: SwaggerSpec,
        endpointPath: String,
        method: String
    ): ValidationReport
}
