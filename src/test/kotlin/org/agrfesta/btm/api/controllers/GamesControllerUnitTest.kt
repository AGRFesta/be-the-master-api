package org.agrfesta.btm.api.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import org.agrfesta.btm.api.controllers.config.MessageResponse
import org.agrfesta.btm.api.persistence.GamesDao
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(GamesController::class)
@ActiveProfiles("test")
class GamesControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val gamesDao: GamesDao
) {

    @Test fun `createGame() Returns 400 when name is missing`() {
        val responseBody: String = mockMvc.perform(
            post("/games")
                .contentType("application/json")
                .content(aGameCreationRequestJson(name = null)))
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        verify(exactly = 0) { gamesDao.createGame(any(), any()) }
        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "name is missing!"
    }

    @TestFactory
    fun `createGame() Returns 400 when description is blank`() = listOf("", " ", "   ").map {
        dynamicTest(" -> '$it'") {
            val responseBody: String = mockMvc.perform(
                post("/games")
                    .contentType("application/json")
                    .content(aGameCreationRequestJson(description = it)))
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString

            verify(exactly = 0) { gamesDao.createGame(any(), any()) }
            val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
            response.message shouldBe "description must not be blank!"
        }
    }

    @Test fun `createGame() Returns 500 when fails to persist game`() {
        val game = aGame()
        val failure = DataAccessResourceFailureException("game persist failure")
        every { gamesDao.createGame(name = game.name, description = game.description) } throws failure
        val responseBody: String = mockMvc.perform(
            post("/games")
                .contentType("application/json")
                .content(aGameCreationRequestJson(name = game.name, description = game.description)))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create game!"
    }

}
