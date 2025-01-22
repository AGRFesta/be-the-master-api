package org.agrfesta.btm.api.controllers

import org.agrfesta.btm.api.model.Game
import org.agrfesta.btm.api.persistence.jdbc.repositories.GlossariesRepository
import org.agrfesta.btm.api.services.utils.TimeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/glossaries/{game}")
class GlossariesController(
    private val glossariesRepository: GlossariesRepository,
    private val timeService: TimeService
) {

    @PostMapping("/entries")
    fun addEntries(@PathVariable game: Game, @RequestBody entries: Map<String, String>): ResponseEntity<Any> {
        val glossary = glossariesRepository.getAllEntriesByGame(game)
        val duplicates = mutableMapOf<String, String>()
        var count = 0
        entries.forEach {
            if (glossary.containsKey(it.key)) {
                duplicates[it.key] = it.value
            } else {
                glossariesRepository.insertEntry(game, it.key, it.value, timeService.nowNoNano())
                count++
            }
        }
        return ResponseEntity.ok(
            AddGlossaryItemsResponse(
                entriesAdded = count,
                duplicates = duplicates
            )
        )
    }

}

data class AddGlossaryItemsResponse(
    val entriesAdded: Int,
    val duplicates: Map<String, String>
)
