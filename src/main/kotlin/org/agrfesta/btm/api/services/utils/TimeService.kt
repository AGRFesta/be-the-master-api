package org.agrfesta.btm.api.services.utils

import org.springframework.stereotype.Service
import java.time.Instant

interface TimeService {
    fun nowNoNano(): Instant
}

@Service
class TimeServiceImpl: TimeService {
    override fun nowNoNano(): Instant = Instant.now().toNoNanoSec()
}

fun Instant.toNoNanoSec(): Instant = Instant.ofEpochMilli(toEpochMilli())
