package org.agrfesta.btm.api.model.mausritter

data class ReducibleAttribute(
    val actual: Int,
    val max: Int
)

data class Character(
    val name: String,
    val background: String,
    val birthsign: String,
    val disposition: String,
    val coat: String,
    val physicalDetail: String,
    val str: ReducibleAttribute,
    val dex: ReducibleAttribute,
    val wil: ReducibleAttribute,
    val hp: ReducibleAttribute,
    val pips: Int,
    val inventory: Collection<Item>
)
