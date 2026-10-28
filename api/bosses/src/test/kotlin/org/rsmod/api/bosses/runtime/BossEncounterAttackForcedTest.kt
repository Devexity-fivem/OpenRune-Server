package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.bosses.dsl.resetAnim
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.ForcedAbility
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class BossEncounterAttackForcedTest {
    @Test
    fun `attack-forced ability fires inside the min-max window and resets`() {
        val type = NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 200)
        val npc = Npc(type, CoordGrid(0, 1, 1, 0, 0))
        val spec =
            BossSpec(
                npcTypes = listOf("npc.boss"),
                stats = BossStats(attackRate = 2),
                abilities =
                    mapOf(
                        "auto" to resetAnim(),
                        "special" to resetAnim(),
                    ),
                phases =
                    mapOf(
                        "combat" to
                            PhaseSpec(
                                name = "combat",
                                selector =
                                    Selector.WeightedRandom(listOf(WeightedRef("auto", weight = 1))),
                                forceAbilities =
                                    listOf(
                                        ForcedAbility(
                                            period = 0,
                                            ability = "special",
                                            attackMin = 8,
                                            attackMax = 10,
                                        )
                                    ),
                            )
                    ),
                triggers = emptyList(),
            )
        val encounter = BossEncounter(npc, spec, MapClock())
        val selector = spec.phases.getValue("combat").selector

        val picked = (1..60).map { tick -> encounter.selectAbility(selector, tick) }
        val fireIdx = picked.withIndex().filter { it.value == "special" }.map { it.index }
        check(fireIdx.isNotEmpty()) { "special never fired: $picked" }
        val gaps = listOf(fireIdx.first()) + fireIdx.zipWithNext().map { (a, b) -> b - a }
        check(gaps.all { it in 8..11 }) { "gaps outside 8-10 attack window: $gaps" }
        assertEquals("special", picked[fireIdx.first()])
        assertNotEquals("special", picked[fireIdx.first() + 1])
    }
}
