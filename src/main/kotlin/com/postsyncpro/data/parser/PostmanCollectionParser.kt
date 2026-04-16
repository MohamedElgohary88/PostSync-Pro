package com.postsyncpro.data.parser

import com.postsyncpro.domain.model.PostmanCollection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PostmanCollectionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    fun parse(jsonString: String): PostmanCollection {
        return json.decodeFromString<PostmanCollection>(jsonString)
    }

    fun serialize(collection: PostmanCollection): String {
        return json.encodeToString(collection)
    }
}
