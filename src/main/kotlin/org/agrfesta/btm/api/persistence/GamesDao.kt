package org.agrfesta.btm.api.persistence

import org.agrfesta.btm.api.model.Game

interface GamesDao {

    fun findGameByName(name: String): Game?

}
