package org.rsmod.content.bosses.worldbosses

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
class WorldBossesSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }

        private val SYMBOL_REGEX =
            Regex("\"(npc|seq|spotanim|projanim|synth|headbar|hitmark)\\.([a-z_0-9]+)\"")

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
    fun `moons vis props are 1hp statues and real forms are hide_npc multinpcs`() {
        val vis =
            mapOf(
                "npc.pmoon_boss_blood_moon" to "npc.pmoon_boss_blood_moon_vis",
                "npc.pmoon_boss_blue_moon" to "npc.pmoon_boss_blue_moon_vis",
                "npc.pmoon_boss_eclipse_moon" to "npc.pmoon_boss_eclipse_moon_vis",
            )
        val hideNpc = "varbit.pmoon_boss_hide_npc".asRSCM(RSCMType.VARBIT)
        for ((real, statue) in vis) {
            val realType =
                checkNotNull(ServerCacheManager.getNpc(real.asRSCM(RSCMType.NPC))) { real }
            val statueType =
                checkNotNull(ServerCacheManager.getNpc(statue.asRSCM(RSCMType.NPC))) { statue }
            assertEquals(1, statueType.hitpoints, "$statue should be a 1hp prop")
            assertEquals(-1, statueType.multiVarBit, "$statue should not be a multinpc")
            assertEquals(500, realType.hitpoints, "$real combat hp")
            assertEquals(hideNpc, realType.multiVarBit, "$real transform selector")
            assertEquals(statueType.id, realType.multiNpc[0], "$real should render $statue at rest")
        }
    }

    @Test
    fun `all spec symbols resolve`() {
        val symbols = moduleSymbols()
        for ((type, names) in symbols) {
            for (name in names) {
                assertDoesNotThrow({ name.asRSCM(type) }) {
                    "symbol '$name' does not resolve as ${type.prefix}"
                }
            }
        }
    }
}
