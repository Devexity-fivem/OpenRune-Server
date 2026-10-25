package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.bosses.dsl.resetAnim
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.ForcedAbility
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class BossEncounterForcedAbilityTest {
    @Test
    fun `once-gated forced ability fires below the hp threshold and only once`() {
        val encounter = encounter(hp = 200)
        assertNull(encounter.selectPriorityAbility(tick = 10, target = null))

        encounter.npc.hitpoints = 99
        assertEquals("summon", encounter.selectPriorityAbility(tick = 11, target = null))

        encounter.startAttack("summon", tick = 11)
        assertNull(encounter.selectPriorityAbility(tick = 15, target = null))
        assertNull(encounter.selectPriorityAbility(tick = 16, target = null))
    }

    @Test
    fun `forced ability without once refires while the condition holds`() {
        val encounter = encounter(hp = 99, once = false)
        assertEquals("summon", encounter.selectPriorityAbility(tick = 10, target = null))
        encounter.startAttack("summon", tick = 10)
        assertEquals("summon", encounter.selectPriorityAbility(tick = 14, target = null))
    }

    private fun encounter(hp: Int, once: Boolean = true): BossEncounter {
        val type = NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 200)
        val npc = Npc(type, CoordGrid(0, 1, 1, 0, 0)).apply { hitpoints = hp }
        val spec =
            BossSpec(
                npcTypes = listOf("npc.boss"),
                stats = BossStats(attackRate = 4),
                abilities = mapOf("summon" to resetAnim()),
                phases =
                    mapOf(
                        "combat" to
                            PhaseSpec(
                                name = "combat",
                                forceAbilities =
                                    listOf(
                                        ForcedAbility(
                                            period = 0,
                                            ability = "summon",
                                            condition = Condition.HpBelow(0.5),
                                            once = once,
                                        )
                                    ),
                            )
                    ),
                triggers = emptyList(),
            )
        return BossEncounter(npc, spec, MapClock())
    }
}
