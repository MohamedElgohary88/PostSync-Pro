package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.ValidationRepository

class ValidateSrsUseCase(
    private val validationRepository: ValidationRepository
) {
    suspend operator fun invoke(
        rules: List<SrsRule>,
        swaggerSpec: SwaggerSpec,
        endpointPath: String,
        method: String
    ): ValidationReport {
        return validationRepository.validateSrsAgainstSwagger(rules, swaggerSpec, endpointPath, method)
    }
}
