package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.ELEM
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.PHASE
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.SLOT

internal object DagonhaiAbilities {
    const val COMBAT = "combat"
    const val RITUAL = "ritual"
    const val EMPOWERED = "empowered"

    const val HEADBAR = "headbar.standard_charge_100"
    const val HEADBAR_CYCLE = 30

    private val ELEMENTS = listOf("fire", "water", "air", "earth")

    val ELEM_QUOTES =
        listOf(
            "I'll feed the flames!",
            "I'll guide the flow!",
            "I'll focus the winds!",
            "I'll strengthen the bond!",
        )

    /** Standard-spellbook obj ids that interrupt each channel element. */
    val ELEM_SPELLS =
        listOf(
            intArrayOf(3279, 3291, 3307, 3321, 21879),
            intArrayOf(3275, 3285, 3297, 3315, 21877),
            intArrayOf(3273, 3281, 3294, 3313, 21876),
            intArrayOf(3277, 3288, 3302, 3319, 21878),
        )

    val GOD_QUOTES =
        listOf(
            "Zamorak empowers us!",
            "Saradominist zeal, feed the flame!",
            "Bandosian strength, feed the flame!",
        )

    private val GOD_NAMES = listOf("zamorak", "saradomin", "bandos")

    fun elemName(elem: Int): String = ELEMENTS.getOrElse(elem) { ELEMENTS[0] }

    fun godName(god: Int): String = GOD_NAMES.getOrElse(god) { GOD_NAMES[0] }

    fun elemOfSpell(objId: Int): Int = ELEM_SPELLS.indexOfFirst { objId in it }

    private fun slotAnim(base: String): Effect =
        switch(
            SLOT,
            listOf(anim("${base}_01"), anim("${base}_02"), anim("${base}_03")),
        )

    private fun slotElemVfx(base: String): Effect =
        switch(
            SLOT,
            (1..3).map { slot ->
                switch(ELEM, ELEMENTS.map { elem -> spotanim("${base}_0${slot}_$elem") })
            },
        )

    fun channelEffects(): List<Effect> =
        listOf(
            transitionTo(RITUAL),
            setVarn(PHASE, DagonhaiArena.PHASE_RITUAL),
            switch(ELEM, ELEM_QUOTES.map(::say)),
            slotAnim("seq.npc_dagonhai_charge_cauldron_idle"),
            slotElemVfx("spotanim.vfx_dagonhai_charge_cauldron_idle"),
            headbar(HEADBAR, 0, 100, DagonhaiArena.CHANNEL_TICKS * HEADBAR_CYCLE),
        )

    fun channelEndEffects(): List<Effect> =
        listOf(
            slotAnim("seq.npc_dagonhai_charge_cauldron_end"),
            slotElemVfx("spotanim.vfx_dagonhai_charge_cauldron_end"),
            clearHeadbar(HEADBAR),
            transitionTo(COMBAT),
            setVarn(PHASE, DagonhaiArena.PHASE_COMBAT),
            nextAttackIn(DagonhaiArena.ATTACK_CYCLE),
        )

    fun channelStopEffects(): List<Effect> =
        listOf(
            interrupt(),
            slotAnim("seq.npc_dagonhai_charge_cauldron_interrupted"),
            clearHeadbar(HEADBAR),
            transitionTo(COMBAT),
            setVarn(PHASE, DagonhaiArena.PHASE_COMBAT),
            nextAttackIn(DagonhaiArena.ATTACK_CYCLE),
        )

    fun restoreEffects(): List<Effect> =
        listOf(
            interrupt(),
            transitionTo(RITUAL),
            setVarn(PHASE, DagonhaiArena.PHASE_RESTORE),
            slotAnim("seq.npc_dagonhai_charge_cauldron_idle"),
        )

    fun empowermentEffects(): List<Effect> =
        listOf(
            transitionTo(EMPOWERED),
            setVarn(PHASE, DagonhaiArena.PHASE_EMPOWERED),
            say("The ritual is complete!"),
        )

    fun barrage(splash: org.rsmod.api.bosses.spec.Area): Effect =
        sequence(
            anim("seq.npc_dagonhai_attack_magic_guthix_04"),
            spotanim("spotanim.vfx_dagonhai_attack_magic_04_guthix_energy"),
            onEach(
                playersIn(splash),
                sequence(
                    faceTarget(),
                    projectile(
                        spotanim = "spotanim.vfx_dagonhai_attack_magic_04_projanim",
                        hit =
                            hit {
                                damage(0..DagonhaiArena.BARRAGE_MAX).roll()
                                type(Magic)
                                penetration(100)
                            },
                    ),
                    hit(
                        damage = (0..DagonhaiArena.BARRAGE_MAX).roll(),
                        type = Magic,
                        delay = 2,
                    ),
                ),
            ),
            nextAttackIn(DagonhaiArena.ATTACK_CYCLE),
        )
}
