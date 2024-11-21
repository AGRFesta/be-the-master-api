package org.agrfesta.test.mothers

import org.agrfesta.btm.api.model.Embedding
import java.util.*
import kotlin.random.Random.Default.nextFloat

fun aRandomUniqueString(): String = UUID.randomUUID().toString()

fun anEmbedding(size: Int = 1536): Embedding = FloatArray(size) { nextFloat() * 2 - 1 }
