package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*

interface PostmanSyncRepository {
    suspend fun parsePostmanCollection(jsonContent: String): PostmanCollection
    suspend fun parseSwaggerSpec(jsonContent: String): SwaggerSpec
    suspend fun mergeCollections(oldCollection: PostmanCollection, newSource: Any): MergeResult
    suspend fun exportCollection(collection: PostmanCollection): String
}
