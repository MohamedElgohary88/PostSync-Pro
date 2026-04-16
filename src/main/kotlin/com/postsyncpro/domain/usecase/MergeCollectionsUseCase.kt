package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.PostmanSyncRepository

class MergeCollectionsUseCase(
    private val syncRepository: PostmanSyncRepository
) {
    suspend fun parsePostmanCollection(json: String): PostmanCollection {
        return syncRepository.parsePostmanCollection(json)
    }

    suspend fun parseSwaggerSpec(json: String): SwaggerSpec {
        return syncRepository.parseSwaggerSpec(json)
    }

    suspend fun merge(oldCollection: PostmanCollection, newSource: Any): MergeResult {
        return syncRepository.mergeCollections(oldCollection, newSource)
    }

    suspend fun export(collection: PostmanCollection): String {
        return syncRepository.exportCollection(collection)
    }
}
