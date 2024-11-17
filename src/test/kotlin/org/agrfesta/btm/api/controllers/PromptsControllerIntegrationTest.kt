package org.agrfesta.btm.api.controllers

import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PromptsControllerIntegrationTest {
    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
            .let { PostgreSQLContainer(it) }

    }

    @LocalServerPort private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
    }

    @TestFactory
    fun returnsTokenCountsOfAPrompt() = listOf(
        "" to 0,
        "Nel mezzo del cammin di nostra vita" to 10,
        "mi ritrovai" to 4,
        "per una selva oscura" to 6
    ).map {
        dynamicTest("${it.first} -> ${it.second}") {
            val result = given()
                .contentType(ContentType.JSON)
                .body("""{"prompt":"${it.first}"}""")
                .`when`()
                .post("/prompts/tokens-count")
                .then()
                .statusCode(200)
                .extract()
                .`as`(TokenCountResponse::class.java)

            result.count shouldBe it.second
        }
    }

}
