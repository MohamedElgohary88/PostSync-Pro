package com.postsyncpro.data.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object AiApiClient {

    fun create(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }

            install(Logging) {
                level = LogLevel.NONE
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }

            expectSuccess = false
        }
    }

    fun createForFuzzTesting(): HttpClient {
        return HttpClient(CIO) {
            install(Logging) {
                level = LogLevel.NONE
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }

            expectSuccess = false
        }
    }
}
