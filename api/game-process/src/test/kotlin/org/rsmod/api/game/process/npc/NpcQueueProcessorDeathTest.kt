package org.rsmod.api.game.process.npc

import dev.openrune.types.NpcMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.testing.GameTestExtension
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.module.ExtendedModule

@ExtendWith(GameTestExtension::class)
class NpcQueueProcessorDeathTest {
    @Test
    fun GameTestState.`hits queued after lethal hit are dropped while npc is dying`() =
        runInjectedGameTest(
            NpcDeath::class,
            DeathBindingsModule(),
            NpcHitScript::class,
            NpcDeathScript::class,
        ) {
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), man)
            player.observerUUID = 1
            player.placeAt(npc.coords.translateZ(1))

            var impacts = 0
            eventBus.subscribeKeyed(NpcHitEvents.Impact::class.java, npc.visType.id.toLong()) {
                impacts++
            }

            npc.queueCombatRetaliate(player, delay = 1)
            npc.queueHit(player, delay = 1, HitType.Melee, damage = 7)
            // Sustained fire on the corpse: these hits expire in the same queue
            // pass as the freshly queued death sequence and must not be applied.
            npc.queueHit(player, delay = 1, HitType.Melee, damage = 5)
            npc.queueHit(player, delay = 1, HitType.Melee, damage = 5)

            advance(ticks = 1)
            check(npc.hitpoints == 0)
            assertTrue(npc.isDying)
            assertEquals(1, impacts)

            advanceUntil({ !npc.isSlotAssigned }, timeoutTicks = 60)
            assertEquals(1, impacts)
        }

    @Test
    fun GameTestState.`queueDeath is idempotent`() =
        runGameTest {
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), man)

            npc.queueDeath()
            npc.queueDeath()

            assertTrue(npc.isDying)
            assertEquals(1, npc.queueList.size)
        }

    private class DeathBindingsModule : ExtendedModule() {
        override fun bind() {
            newSetBinding<NpcDeathDropHook>()
            newSetBinding<NpcDeathKillHook>()
        }
    }

    private companion object {
        private val man =
            npcTypeFactory.create {
                name = "Man"
                size = 1
                hitpoints = 7
                defaultMode = NpcMode.None
            }
    }
}
