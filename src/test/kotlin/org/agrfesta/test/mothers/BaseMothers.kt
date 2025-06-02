package org.agrfesta.test.mothers

import org.agrfesta.btm.api.controllers.normalize
import org.agrfesta.btm.api.model.Embedding
import java.util.*
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.Random.Default.nextFloat

fun aRandomUniqueString(): String = UUID.randomUUID().toString()

fun anEmbedding(size: Int = 1024): Embedding = FloatArray(size) { nextFloat() * 2 - 1 }
fun aNormalizedEmbedding(size: Int = 1024) = anEmbedding(size).normalize()

fun generateVectorWithDistance(base: Embedding, targetCosineDistance: Double): Embedding {
    require(targetCosineDistance in 0.0..2.0) { "Cosine distance must be between 0.0 and 2.0" }

    val targetSimilarity = 1.0 - targetCosineDistance
    require(targetSimilarity in -1.0..1.0) { "Cosine similarity must be between -1.0 and 1.0" }

    // Normalize base to unit vector
    val norm = sqrt(base.map { it * it }.sum())
    require(norm > 0f) { "Base vector must not be zero" }
    val unitBase = base.map { it / norm }.toFloatArray()

    // Generate a random vector and orthogonalize it to unitBase
    var orthogonal: FloatArray
    do {
        val rand = FloatArray(base.size) { Random.nextFloat() - 0.5f }
        val dot = unitBase.zip(rand).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
        val projection = unitBase.map { it * dot }.toFloatArray()
        orthogonal = rand.zip(projection).map { (r, p) -> r - p }.toFloatArray()
    } while (orthogonal.all { it == 0f }) // avoid degenerate case

    // Normalize orthogonal vector
    val orthNorm = sqrt(orthogonal.map { it * it }.sum())
    val unitOrth = orthogonal.map { it / orthNorm }.toFloatArray()

    // Compute target vector as linear combination at angle θ
    val angle = acos(targetSimilarity)
    val cosθ = cos(angle).toFloat()
    val sinθ = sin(angle).toFloat()

    return FloatArray(base.size) { i ->
        cosθ * unitBase[i] + sinθ * unitOrth[i]
    }
}


fun generateVectorAtEuclideanDistanceFixed(
    base: FloatArray,
    distance: Float
): FloatArray {
    require(base.size >= 2) { "Base vector too small" }
    require(abs(base.map { it * it }.sum() - 1.0f) < 1e-3) { "Base vector must be normalized" }

    val result = base.copyOf()

    // Cambia due componenti per rimanere sulla superficie della sfera
    // esempio: ruota il vettore in uno spazio 2D locale
    val i = 0
    val j = 1

    val xi = base[i]
    val xj = base[j]

    // Trova un vettore ortogonale in questo piano
    val yi = -xj
    val yj = xi

    // Costruisci v' = base + distance * ortogonale
    result[i] = (xi + distance * yi).toFloat()
    result[j] = (xj + distance * yj).toFloat()

    return result
}



fun generateSimilarVector(base: Embedding, targetCosineSimilarity: Double): Embedding {
    require(targetCosineSimilarity in -1.0..1.0) { "Similarity must be between -1.0 and 1.0" }

    val norm = sqrt(base.map { it * it }.sum())
    if (norm == 0.0f) error("Base vector must not be zero vector")
    val unitBase = base.map { it / norm }.toFloatArray()

    // Generate a random vector, then remove its projection on base to make it orthogonal
    val rand = FloatArray(base.size) { Random.nextFloat() - 0.5f }
    val dot = unitBase.zip(rand).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
    val projection = unitBase.map { it * dot }.toFloatArray()
    val orthogonal = rand.zip(projection).map { it.first - it.second }.toFloatArray()

    val orthNorm = sqrt(orthogonal.map { it * it }.sum())
    val unitOrth = if (orthNorm != 0.0f)
        orthogonal.map { it / orthNorm }.toFloatArray()
    else
    // fallback: just flip one coordinate if orthogonal fails
        unitBase.mapIndexed { i, v -> if (i == 0) -v else v }.toFloatArray()

    val angle = acos(targetCosineSimilarity.coerceIn(-1.0, 1.0))
    val cosPart = FloatArray(base.size) { i -> (unitBase[i] * cos(angle)).toFloat() }
    val sinPart = FloatArray(base.size) { i -> (unitOrth[i] * sin(angle)).toFloat() }

    return cosPart
        .zip(sinPart)
        .map { (c, s) -> c + s }
        .toFloatArray()
}

