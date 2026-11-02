package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcMode
import dev.openrune.types.util.ParamMapBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.rsmod.api.combat.commons.npc.queueCombatRetaliateWithAssist
import org.rsmod.api.config.refs.params
import org.rsmod.api.testing.GameTestExtension
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.map.CoordGrid

@ExtendWith(GameTestExtension::class)
class NpcCallForHelpTest {
    @Test
    fun GameTestState.`call for help pulls nearby same-type allies`() =
        runGameTest {
            val warrior = callHelpType()
            val other = callHelpType()
            val attacked = spawnNpc(CoordGrid(0, 50, 50, 0, 0), warrior)
            val ally = spawnNpc(CoordGrid(0, 50, 50, 0, 3), warrior)
            val bystander = spawnNpc(CoordGrid(0, 50, 50, 0, 4), other)
            player.placeAt(attacked.coords.translateZ(1))

            attacked.queueCombatRetaliateWithAssist(player, npcRepo = npcRepo)

            assertTrue("queue.com_retaliate_player" in attacked.queueList)
            assertTrue("queue.com_retaliate_player" in ally.queueList)
            assertFalse("queue.com_retaliate_player" in bystander.queueList)
        }

    @Test
    fun GameTestState.`call for help respects the param range`() =
        runGameTest {
            val warrior = callHelpType()
            val attacked = spawnNpc(CoordGrid(0, 50, 50, 0, 0), warrior)
            val distant = spawnNpc(CoordGrid(0, 50, 50, 0, 6), warrior)
            player.placeAt(attacked.coords.translateZ(1))

            attacked.queueCombatRetaliateWithAssist(player, npcRepo = npcRepo)

            assertTrue("queue.com_retaliate_player" in attacked.queueList)
            assertFalse("queue.com_retaliate_player" in distant.queueList)
        }

    private fun callHelpType() =
        npcTypeFactory.create {
            name = "Warrior"
            size = 1
            hitpoints = 7
            defaultMode = NpcMode.None
            paramMap =
                ParamMapBuilder()
                    .apply { this[params.aggro_call_help] = ASSIST_RANGE }
                    .toParamMap()
        }

    private companion object {
        private const val ASSIST_RANGE = 5
    }
}
