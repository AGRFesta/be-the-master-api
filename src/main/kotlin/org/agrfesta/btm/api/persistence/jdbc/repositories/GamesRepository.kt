package org.agrfesta.btm.api.persistence.jdbc.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.agrfesta.btm.api.persistence.jdbc.entities.GameEntity
import org.springframework.dao.DataAccessException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Repository for accessing and manipulating games in the database.
 *
 * This repository works directly with the `btm.games` table.
 */
@Repository
class GamesRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * Checks whether a game exists with the given name.
     *
     * @param name The unique name of the game.
     * @return `true` if a game exists with the given name, `false` otherwise.
     */
    fun existsByName(name: String): Boolean {
        val sql = "SELECT EXISTS (SELECT 1 FROM btm.games WHERE name = :name)"
        val params = mapOf("name" to name)
        return jdbcTemplate.queryForObject(sql, params, Boolean::class.java) ?: false
    }

    /**
     * Finds a game by its unique identifier.
     *
     * @param id The unique identifier of the game (UUID).
     * @return The matching [GameEntity], or `null` if no game exists with the given ID.
     *
     * @throws DataAccessException if a database access error occurs.
     */
    fun findById(id: UUID): GameEntity? {
        val sql = """
            SELECT id, name, description
            FROM btm.games
            WHERE id = :id
        """
        val params = mapOf("id" to id)

        return try {
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                GameEntity(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    description = rs.getString("description")
                )
            }
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    /**
     * Retrieves a game by its unique identifier.
     *
     * @param id The unique identifier of the game (UUID).
     * @return The [GameEntity] matching the given ID.
     *
     * @throws EmptyResultDataAccessException if no game exists with the given ID.
     * @throws DataAccessException if a database access error occurs.
     */
    fun getById(id: UUID): GameEntity {
        val sql = """
        SELECT id, name, description
        FROM btm.games
        WHERE id = :id
    """
        val params = mapOf("id" to id)

        return jdbcTemplate.queryForObject(sql, params) { rs, _ ->
            GameEntity(
                id = UUID.fromString(rs.getString("id")),
                name = rs.getString("name"),
                description = rs.getString("description")
            )
        }!!
    }


    /**
     * Finds a game by its unique name.
     *
     * @param name The unique name of the game.
     * @return The matching [GameEntity], or `null` if no game exists with the given [name].
     *
     * @throws DataAccessException if a database access error occurs.
     */
    fun findByName(name: String): GameEntity? {
        val sql = """
            SELECT id, name, description
            FROM btm.games
            WHERE name = :name
        """
        val params = mapOf("name" to name)

        return try {
            jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                GameEntity(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    description = rs.getString("description")
                )
            }
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    /**
     * Retrieves a game by its unique name.
     *
     * @param name The unique name of the game.
     * @return The [GameEntity] matching the given [name].
     *
     * @throws EmptyResultDataAccessException if no game exists with the given [name].
     * @throws DataAccessException if a database access error occurs.
     */
    fun getByName(name: String): GameEntity {
        val sql = """
            SELECT id, name, description
            FROM btm.games
            WHERE name = :name
        """
        val params = mapOf("name" to name)

        return jdbcTemplate.queryForObject(sql, params) { rs, _ ->
                GameEntity(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    description = rs.getString("description")
                )
            }!!
    }

    /**
     * Inserts a new game into the database.
     *
     * The caller is responsible for ensuring the uniqueness of the game name
     * and providing a valid identifier.
     *
     * @param id The unique identifier for the game (UUID).
     * @param name The unique name of the game.
     * @param description An optional description of the game.
     *
     * @throws DataAccessException if a database access error occurs,
     *         for example if the game name already exists (unique constraint violation).
     */
    fun insert(id: UUID, name: String, description: String?) {
        val sql = """
            INSERT INTO btm.games (id, name, description)
            VALUES (:id, :name, :description)
        """

        val params = mapOf(
            "id" to id,
            "name" to name,
            "description" to description
        )

        jdbcTemplate.update(sql, params)
    }
}
