package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrMultiplayerTest {
    @Test
    fun GameTestState.`every participant receives the round result exactly once`() =
        runGameTest(Gotr::class) {
            val gate = placeMapLoc(CoordGrid(3615, 9483), "loc.gotr_barrier")
            val friend = registerPlayer(CoordGrid(3616, 9482))
            for (participant in listOf(player, friend)) {
                participant.setBaseLevel(RUNECRAFTING, 27)
                participant.setCurrentLevel(RUNECRAFTING, 27)
            }
            player.placeAt(CoordGrid(3615, 9482))
            player.opLoc1(gate)
            friend.opLoc1(gate)
            advance(205)

            val guardianType = ServerCacheManager.getNpc("npc.gotr_great_guardian".asRSCM(RSCMType.NPC))!!
            val guardian = spawnNpc(CoordGrid(3612, 9496), guardianType)
            for (participant in listOf(player, friend)) {
                participant.withProtectedAccess {
                    vars["varbit.gotr_elemental_earned_this_game"] = 200
                    vars["varbit.gotr_catalytic_earned_this_game"] = 200
                }
            }
            advance()
            friend.setVarp("varp.gotr_elemental_reward_points", 3)
            friend.setVarp("varp.gotr_catalytic_reward_points", 5)
            val friendXp = friend.statMap.getFineXP(RUNECRAFTING)
            player.placeAt(CoordGrid(3611, 9495))
            player.inv[0] = InvObj("obj.gotr_guardian_stone_elemental", 500)
            player.opNpc1(guardian)
            advanceUntil({ player.inv.count("obj.gotr_guardian_stone_elemental") == 0 }, timeoutTicks = 10)
            advance(4)

            assertTrue(friend.statMap.getFineXP(RUNECRAFTING) > friendXp)
            val awardedXp = friend.statMap.getFineXP(RUNECRAFTING)
            advance(8)
            assertEquals(awardedXp, friend.statMap.getFineXP(RUNECRAFTING))
            assertEquals(5, friend.vars["varp.gotr_elemental_reward_points"])
            assertEquals(7, friend.vars["varp.gotr_catalytic_reward_points"])
            assertEquals(10, player.vars["varp.gotr_elemental_reward_points"])
            assertEquals(2, player.vars["varp.gotr_catalytic_reward_points"])
        }

    @Test
    fun GameTestState.`logging out removes a participant from charge scaling`() =
        runGameTest(Gotr::class) {
            val gate = placeMapLoc(CoordGrid(3615, 9483), "loc.gotr_barrier")
            val friend = registerPlayer(CoordGrid(3616, 9482))
            for (participant in listOf(player, friend)) {
                participant.setBaseLevel(RUNECRAFTING, 27)
                participant.setCurrentLevel(RUNECRAFTING, 27)
            }
            player.placeAt(CoordGrid(3615, 9482))
            player.opLoc1(gate)
            friend.opLoc1(gate)
            advance(205)
            eventBus.publish(SessionStateEvent.Logout(friend))
            unregisterPlayer(friend)

            val guardianType = ServerCacheManager.getNpc("npc.gotr_great_guardian".asRSCM(RSCMType.NPC))!!
            val guardian = spawnNpc(CoordGrid(3612, 9496), guardianType)
            player.placeAt(CoordGrid(3611, 9495))
            player.inv[0] = InvObj("obj.gotr_guardian_stone_elemental", 250)
            player.opNpc1(guardian)
            advanceUntil({ player.inv.count("obj.gotr_guardian_stone_elemental") == 0 }, timeoutTicks = 10)
            advance(4)

            assertEquals(1, player.vars["varbit.gotr_player_has_finished_game"])
        }

    private companion object {
        const val RUNECRAFTING = "stat.runecrafting"
    }
}
