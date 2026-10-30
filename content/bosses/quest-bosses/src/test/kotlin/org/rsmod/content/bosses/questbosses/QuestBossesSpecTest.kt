package org.rsmod.content.bosses.questbosses

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class QuestBossesSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }

        private val SYMBOL_REGEX = Regex("\"(npc|seq|spotanim|projanim|synth|headbar|hitmark)\\.([a-z_0-9]+)\"")

        private fun moduleSymbols(): Map<RSCMType, Set<String>> {
            val dir = File("src/main/kotlin")
            val byType = mutableMapOf<RSCMType, MutableSet<String>>()
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                for (match in SYMBOL_REGEX.findAll(file.readText())) {
                    val prefix = match.groupValues[1]
                    val type = RSCMType.values().firstOrNull { it.prefix == prefix } ?: continue
                    byType.getOrPut(type) { mutableSetOf() } += match.value.trim('"')
                }
            }
            return byType
        }
    }

    @Test
    fun `drakan combat families carry real stats and the afterimage decoy stays a prop`() {
        val ranis =
            listOf(
                "npc.myq4_ranis_vampyre_combat",
                "npc.myq4_ranis_vampyre_flying_combat",
                "npc.myq4_ranis_vampyre_combat_enrage",
            )
        for (symbol in ranis) {
            val type =
                checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC))) { symbol }
            assertEquals(400, type.hitpoints, "$symbol fight hp")
        }
        val lowerniel =
            listOf(
                "npc.myq6_lowerniel_combat_1",
                "npc.myq6_lowerniel_combat_2",
                "npc.myq6_lowerniel_combat_3",
                "npc.myq6_lowerniel_final_combat",
            )
        for (symbol in lowerniel) {
            val type =
                checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC))) { symbol }
            assertEquals(3000, type.hitpoints, "$symbol fight hp")
        }
        val decoy =
            checkNotNull(
                ServerCacheManager.getNpc("npc.myq6_lowerniel_hurt".asRSCM(RSCMType.NPC))
            )
        assertEquals(1, decoy.hitpoints, "afterimage decoy must stay a 1hp prop")
    }

    @Test
    fun `all spec symbols resolve`() {
        val symbols = moduleSymbols()
        for ((type, names) in symbols) {
            for (name in names) {
                assertDoesNotThrow({ name.asRSCM(type) }) { "symbol '$name' does not resolve as ${type.prefix}" }
            }
        }
    }
}
