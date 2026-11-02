package org.rsmod.content.skills.runecrafting.items

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.runecrafting.bloodEssenceCharges
import org.rsmod.game.entity.Player

object BloodEssence {
    const val INACTIVE = "obj.blood_essence_inactive"
    const val ACTIVE = "obj.blood_essence_active"
    const val BLOOD_RUNE = "obj.bloodrune"
    const val MAX_CHARGES = 1000
    const val ACTIVATE_SOUND = "synth.enchant_ruby_ring"
    private const val PROC_ROLL = 2

    fun hasActive(player: Player): Boolean = player.inv.contains(ACTIVE)

    fun ProtectedAccess.rollBloodRuneBonus(essenceConsumed: Int): Int {
        if (!inv.contains(ACTIVE)) {
            return 0
        }

        val charges = player.bloodEssenceCharges
        if (charges <= 0) {
            return 0
        }

        var extraRunes = 0
        repeat(essenceConsumed) {
            if (random.randomBoolean(PROC_ROLL)) {
                extraRunes++
            }
        }

        return extraRunes.coerceAtMost(charges)
    }

    fun ProtectedAccess.commitBloodRuneBonus(granted: Int) {
        if (granted <= 0) {
            return
        }
        player.bloodEssenceCharges -= granted
        if (player.bloodEssenceCharges <= 0) {
            mes("Your blood essence has been destroyed after crafting 1,000 runes.")
        } else {
            mes(
                "You manage to extract power from the Blood Essence and craft " +
                    "$granted extra rune${if (granted != 1) "s" else ""}.",
            )
        }
    }
}
