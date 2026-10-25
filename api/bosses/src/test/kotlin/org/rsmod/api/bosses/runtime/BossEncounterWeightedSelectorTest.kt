package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.bosses.dsl.resetAnim
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class BossEncounterWeightedSelectorTest {
    @Test
    fun `weighted random selects every entry over enough rolls`() {
        val type = NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 200)
        val npc = Npc(type, CoordGrid(0, 1, 1, 0, 0))
        val spec =
            BossSpec(
                npcTypes = listOf("npc.boss"),
                stats = BossStats(attackRate = 2),
                abilities =
                    mapOf(
                        "magic" to resetAnim(),
                        "disarm" to resetAnim(),
                        "explosion" to resetAnim(),
                    ),
                phases =
                    mapOf(
                        "combat" to
                            PhaseSpec(
                                name = "combat",
                                selector =
                                    Selector.WeightedRandom(
                                        listOf(
                                            WeightedRef(ability = "magic", weight = 6),
                                            WeightedRef(ability = "disarm", weight = 1),
                                            WeightedRef(ability = "explosion", weight = 1),
                                        )
                                    ),
                            )
                    ),
                triggers = emptyList(),
            )
        val encounter = BossEncounter(npc, spec, MapClock())

        val phase = spec.phases.getValue("combat")
        val picks = (0 until 200).map { tick -> encounter.pick(phase.selector, tick) }
        assertTrue("disarm" in picks, "disarm never selected in 200 rolls: $picks")
        assertTrue("explosion" in picks, "explosion never selected in 200 rolls: $picks")
    }
}
