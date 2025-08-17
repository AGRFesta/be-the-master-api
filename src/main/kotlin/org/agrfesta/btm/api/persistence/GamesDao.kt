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

}
