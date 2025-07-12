package org.agrfesta.btm.api.services.utils

import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Abstraction for retrieving the current time with nanoseconds stripped.
 *
 * Useful for ensuring consistency in timestamps where nanosecond precision is unnecessary
 * or causes problems (e.g., database comparisons, logs, testing).
 */
interface TimeService {

    /**
     * Returns the current UTC time with nanoseconds truncated (millisecond precision).
     *
     * @return the current [Instant] without nanosecond precision.
     */
    fun nowNoNano(): Instant

}

/**
 * Default implementation of [TimeService] using the system clock.
 */
@Service
class TimeServiceImpl: TimeService {

    /**
     * Returns the current instant with nanoseconds set to zero.
     *
     * @return current [Instant] truncated to milliseconds.
     */
    override fun nowNoNano(): Instant = Instant.now().toNoNanoSec()

}

/**
 * Extension function that truncates an [Instant] to millisecond precision.
 *
 * @receiver the [Instant] to truncate.
 * @return a new [Instant] with the same millisecond timestamp, but with nanoseconds zeroed.
 */
fun Instant.toNoNanoSec(): Instant = Instant.ofEpochMilli(toEpochMilli())
