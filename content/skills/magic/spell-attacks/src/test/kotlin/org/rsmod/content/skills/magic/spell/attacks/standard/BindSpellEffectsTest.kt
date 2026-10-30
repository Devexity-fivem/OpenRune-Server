package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.magic.spell.attacks.ancient.FixedRandom
import org.rsmod.content.skills.magic.spell.attacks.ancient.TestCache
import org.rsmod.content.skills.magic.spell.attacks.ancient.npc
import org.rsmod.content.skills.magic.spell.attacks.ancient.player
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BindSpellEffectsTest {
    @BeforeAll
    fun loadCache() {
        TestCache.load()
    }

    private val landing = FixedRandom(0)

    @ParameterizedTest
    @CsvSource("Bind, 8", "Snare, 16", "Entangle, 24")
    fun `freezes npc for wiki duration`(spell: BindSpell, ticks: Int) {
        val target = npc("npc.man", clock = 100)
        val effects = BindSpellEffects(landing)
        val applied = effects.bindTicks(spell, player())
        assertEquals(ticks, applied)
        assertTrue(effects.bind(target, applied))
        target.currentMapClock = 100 + ticks - 1
        assertTrue(CombatEffects.isFrozen(target), "frozen on last tick")
        target.currentMapClock = 100 + ticks
        assertFalse(CombatEffects.isFrozen(target), "thawed after duration")
    }

    @Test
    fun `swampbark adds two ticks per worn piece`() {
        val caster = player()
        val effects = BindSpellEffects(landing)
        fun equip(name: String, slot: Int) {
            caster.worn.objs[slot] = InvObj(name, 1)
        }
        assertEquals(8, effects.bindTicks(BindSpell.Bind, caster))
        equip("obj.swampbark_helm", 0)
        equip("obj.swampbark_body", 1)
        assertEquals(12, effects.bindTicks(BindSpell.Bind, caster))
        equip("obj.swampbark_legs", 2)
        assertEquals(14, effects.bindTicks(BindSpell.Bind, caster))
        assertEquals(22, effects.bindTicks(BindSpell.Snare, caster))
        assertEquals(30, effects.bindTicks(BindSpell.Entangle, caster))
    }

    @Test
    fun `npc is immune to refreezing for 5 ticks after thawing`() {
        val target = npc("npc.man", clock = 100)
        val effects = BindSpellEffects(landing)
        assertTrue(effects.bind(target, 8))

        target.currentMapClock = 110
        assertFalse(effects.bind(target, 8))
        assertFalse(CombatEffects.isFrozen(target))

        target.currentMapClock = 113
        assertTrue(effects.bind(target, 8))
    }

    @Test
    fun `freeze-resistant npcs are not frozen`() {
        val target = npc("npc.gemstone_crab")
        assertFalse(BindSpellEffects(landing).bind(target, 24))
        assertFalse(CombatEffects.isFrozen(target))
    }

    @Test
    fun `freezes players`() {
        val target = player()
        assertTrue(BindSpellEffects(landing).bind(target, 24))
        assertTrue(target.isFrozen)
        assertEquals(24, target.freezeTimerInterval())
    }

    @Test
    fun `protect from magic does not shorten the freeze`() {
        val target = player()
        VarPlayerIntMapSetter.set(target, "varbit.prayer_protectfrommagic", 1)
        assertTrue(BindSpellEffects(landing).bind(target, 8))
        assertEquals(8, target.freezeTimerInterval())
    }

    @OptIn(InternalApi::class)
    private fun Player.freezeTimerInterval(): Int {
        val timer = "timer.combat_freeze".asRSCM(RSCMType.TIMER).toShort()
        return timerMap.extractInterval(checkNotNull(timerMap[timer]))
    }
}
