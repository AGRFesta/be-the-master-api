package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.Game

interface GamesDao {

    /**
     * Finds [Game] by [name].
     *
     * @param name [Game] unique name.
     * @return found [Game] otherwise null.
     */
    fun findGameByName(name: String): Game?

    /**
     * Creates a [Game] with [name] and optional [description].
     *
     * @param name [Game] unique name.
     * @param description [Game] optional description.
     */
    fun createGame(name: String, description: String? = null)

}
