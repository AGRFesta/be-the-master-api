package org.agrfesta.btm.api.controllers

import arrow.core.left
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.model.PersistenceFailure
import org.agrfesta.btm.api.persistence.PartiesDao
import org.agrfesta.btm.api.persistence.RulesBitsDao
import org.agrfesta.btm.api.persistence.jdbc.repositories.CharactersRepository
import org.agrfesta.test.mothers.aRandomUniqueString
import org.agrfesta.test.mothers.aSheet
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PartiesController::class)
@ActiveProfiles("test")
class PartiesControllerUnitTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val partiesDao: PartiesDao,
) {

    @Test fun `createParty() Returns 500 when party creation fails`() {
        val name = aRandomUniqueString()
        every { partiesDao.persist(name, Game.MAUSRITTER, any()) } returns
                PersistenceFailure("creation failure").left()
        val responseBody: String = mockMvc.perform(
            post("/parties")
                .contentType("application/json")
                .content("""{"name": "$name", "game": "MAUSRITTER", "sheets": [${aSheet()},${aSheet()}]}"""))
            .andExpect(status().isInternalServerError)
            .andReturn().response.contentAsString

        val response: MessageResponse = objectMapper.readValue(responseBody, MessageResponse::class.java)
        response.message shouldBe "Unable to create party!"
    }

}
