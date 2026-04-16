package com.postsyncpro.domain.model

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class PostmanCollection(
    val info: CollectionInfo,
    val item: List<PostmanItem> = emptyList(),
    val variable: List<CollectionVariable> = emptyList(),
    val auth: PostmanAuth? = null
)

@Serializable
data class CollectionInfo(
    val name: String,
    val description: String = "",
    @SerialName("_postman_id") val postmanId: String = "",
    val schema: String = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
)

@Serializable
data class PostmanItem(
    val name: String,
    val request: PostmanRequest? = null,
    val response: List<PostmanResponse> = emptyList(),
    val item: List<PostmanItem> = emptyList(), // folders
    val event: List<PostmanEvent> = emptyList(),
    val description: String = ""
)

@Serializable
data class PostmanRequest(
    val method: String = "GET",
    val header: List<PostmanHeader> = emptyList(),
    val body: PostmanBody? = null,
    val url: PostmanUrl? = null,
    val auth: PostmanAuth? = null,
    val description: String = ""
)

@Serializable
data class PostmanUrl(
    val raw: String = "",
    val protocol: String = "",
    val host: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val query: List<PostmanQueryParam> = emptyList(),
    val variable: List<PostmanPathVariable> = emptyList()
)

@Serializable
data class PostmanQueryParam(
    val key: String,
    val value: String = "",
    val disabled: Boolean = false,
    val description: String = ""
)

@Serializable
data class PostmanPathVariable(
    val key: String,
    val value: String = "",
    val description: String = ""
)

@Serializable
data class PostmanHeader(
    val key: String,
    val value: String = "",
    val type: String = "text",
    val disabled: Boolean = false,
    val description: String = ""
)

@Serializable
data class PostmanBody(
    val mode: String = "raw",
    val raw: String = "",
    val formdata: List<PostmanFormData> = emptyList(),
    val urlencoded: List<PostmanUrlEncoded> = emptyList(),
    val options: PostmanBodyOptions? = null
)

@Serializable
data class PostmanFormData(
    val key: String,
    val value: String = "",
    val type: String = "text",
    val description: String = "",
    val disabled: Boolean = false
)

@Serializable
data class PostmanUrlEncoded(
    val key: String,
    val value: String = "",
    val type: String = "text",
    val description: String = "",
    val disabled: Boolean = false
)

@Serializable
data class PostmanBodyOptions(
    val raw: PostmanRawLanguage? = null
)

@Serializable
data class PostmanRawLanguage(
    val language: String = "json"
)

@Serializable
data class PostmanAuth(
    val type: String = "noauth",
    val bearer: List<PostmanAuthParam> = emptyList(),
    val basic: List<PostmanAuthParam> = emptyList(),
    val apikey: List<PostmanAuthParam> = emptyList()
)

@Serializable
data class PostmanAuthParam(
    val key: String,
    val value: String = "",
    val type: String = "string"
)

@Serializable
data class PostmanEvent(
    val listen: String, // "test" or "prerequest"
    val script: PostmanScript
)

@Serializable
data class PostmanScript(
    val id: String = "",
    val exec: List<String> = emptyList(),
    val type: String = "text/javascript"
)

@Serializable
data class PostmanResponse(
    val name: String = "",
    val status: String = "",
    val code: Int = 0,
    val body: String = ""
)

@Serializable
data class CollectionVariable(
    val key: String,
    val value: String = "",
    val type: String = "string",
    val disabled: Boolean = false
)
