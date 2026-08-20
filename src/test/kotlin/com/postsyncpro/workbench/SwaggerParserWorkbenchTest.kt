package com.postsyncpro.workbench

import com.postsyncpro.data.parser.SwaggerParseLimits
import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.SwaggerSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwaggerParserWorkbenchTest {
    @Test
    fun `parses safe YAML and merges path-level parameters`() {
        val spec = SwaggerParser.parse(
            """
            openapi: 3.0.3
            info:
              title: Users
              version: "1"
            paths:
              /users/{id}:
                parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      type: string
                get:
                  parameters:
                    - name: verbose
                      in: query
                      schema:
                        type: boolean
                  responses:
                    "200":
                      description: ok
            """.trimIndent()
        )

        val operation = spec.paths.getValue("/users/{id}").getValue("get")
        assertEquals(listOf("id", "verbose"), operation.parameters.map { it.name })
    }

    @Test
    fun `resolves local parameter component refs before decoding`() {
        val spec = SwaggerParser.parse(
            """
            {
              "openapi":"3.0.3",
              "info":{"title":"T","version":"1"},
              "components":{"parameters":{"Trace":{"name":"X-Trace","in":"header","schema":{"type":"string"}}}},
              "paths":{"/ping":{"get":{"parameters":[{"${'$'}ref":"#/components/parameters/Trace"}],"responses":{"200":{"description":"ok"}}}}}
            }
            """.trimIndent()
        )
        assertEquals("X-Trace", spec.paths.getValue("/ping").getValue("get").parameters.single().name)
    }

    @Test
    fun `schema cycles and depth limits are stopped safely`() {
        val spec = SwaggerParser.parse(
            """
            {
              "openapi":"3.0.3",
              "info":{"title":"T","version":"1"},
              "paths":{},
              "components":{"schemas":{
                "A":{"type":"object","properties":{"b":{"${'$'}ref":"#/components/schemas/B"}}},
                "B":{"type":"object","properties":{"a":{"${'$'}ref":"#/components/schemas/A"}}}
              }}
            }
            """.trimIndent()
        )
        val resolved = SwaggerParser.resolveSchemaRef(
            SwaggerSchema(ref = "#/components/schemas/A"),
            spec,
            maxDepth = 8
        )
        assertEquals("object", resolved.type)
        assertNotNull(resolved.properties["b"])

        val deeplyNested = "[".repeat(20) + "0" + "]".repeat(20)
        assertFailsWith<IllegalArgumentException> {
            SwaggerParser.parse(
                """{"openapi":"3.0.3","info":{"title":"T","version":"1"},"paths":{},"x":$deeplyNested}""",
                SwaggerParseLimits(maxNestingDepth = 10)
            )
        }
    }

    @Test
    fun `rejects unsupported versions and oversized input`() {
        assertFailsWith<IllegalArgumentException> {
            SwaggerParser.parse("""{"swagger":"2.0","info":{"title":"T","version":"1"},"paths":{}}""")
        }
        assertFailsWith<IllegalArgumentException> {
            SwaggerParser.parse(
                """{"openapi":"3.0.3","info":{"title":"${"x".repeat(500)}","version":"1"},"paths":{}}""",
                SwaggerParseLimits(maxInputBytes = 128)
            )
        }
    }

    @Test
    fun `yaml aliases are bounded`() {
        val aliases = (1..10).joinToString(",") { "*item" }
        val yaml = """
            openapi: 3.0.3
            info: &item { title: T, version: "1" }
            paths: {}
            x: [$aliases]
        """.trimIndent()
        assertFailsWith<Exception> {
            SwaggerParser.parse(yaml, SwaggerParseLimits(maxAliases = 2))
        }
    }
}
