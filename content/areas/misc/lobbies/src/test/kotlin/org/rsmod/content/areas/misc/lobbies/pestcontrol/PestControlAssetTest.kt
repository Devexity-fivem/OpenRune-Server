package org.rsmod.content.areas.misc.lobbies.pestcontrol

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

// Locks in every npc family PestControlGame spawns: the knight, the four
// shielded/attackable portals per difficulty suffix, and each pest form used
// by the wave pool at tiers 1, 3 and 5.
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PestControlAssetTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `portals knight and pest forms all resolve`() {
        checkNotNull(
            ServerCacheManager.getNpc("npc.pest_voidknight_control1".asRSCM(RSCMType.NPC))
        )
        for (suffix in listOf("", "_bt1")) {
            for (n in 1..4) {
                for (state in listOf("shield", "active")) {
                    val symbol =
                        if (state == "shield") {
                            "npc.pest_portal_shield_${n}_active$suffix"
                        } else {
                            "npc.pest_portal_${n}_active$suffix"
                        }
                    assertTrue(
                        ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) != null,
                        "$symbol missing",
                    )
                }
            }
        }
        for (tier in listOf("1", "3", "5")) {
            for (pest in
                listOf(
                    "npc.pest_splatter_$tier",
                    "npc.pest_ravager_$tier",
                    "npc.pest_spinner_$tier",
                    "npc.pest_brawler_$tier",
                    "npc.pest_shifter_${tier}a",
                    "npc.pest_torcher_${tier}a",
                    "npc.pest_defiler_${tier}a",
                )) {
                assertTrue(
                    ServerCacheManager.getNpc(pest.asRSCM(RSCMType.NPC)) != null,
                    "$pest missing",
                )
            }
        }
    }
}
