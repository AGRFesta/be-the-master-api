package org.agrfesta.btm.api.model.mausritter

interface Item {
    val name: String
    val slots: Int
}

interface Condition: Item {
    val effect: String
    val removal: String
}

interface Consumable: Item {
    val remainingUsage: Int
    val maxUsage: Int
}

enum class WeaponType { LIGHT, MEDIUM, HEAVY }

interface Weapon: Consumable {
    val weaponType: WeaponType
    val damage: String
}

data class ItemImpl(
    override val name: String,
    override val slots: Int = 1
) : Item

data class ConsumableImpl(
    override val name: String,
    override val remainingUsage: Int,
    override val maxUsage: Int,
    override val slots: Int = 1
) : Consumable
