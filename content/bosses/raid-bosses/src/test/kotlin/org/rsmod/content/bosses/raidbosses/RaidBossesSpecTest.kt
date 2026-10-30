package org.rsmod.content.bosses.raidbosses

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RaidBossesSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }

        private val SYMBOL_REGEX =
            Regex("\"(npc|seq|spotanim|projanim|synth|headbar|hitmark|loc)\\.([a-z_0-9]+)\"")

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
    fun `cox fightable forms have solo hitpoint overrides`() {
        // CoX caches 1hp types because OSRS assigns raid-scaled stats dynamically; the
        // server overlay bakes solo-normal values so spawned forms are actually fightable.
        val forms =
            mapOf(
                "npc.raids_tekton_fighting_standard" to 450,
                "npc.raids_tekton_fighting_enraged" to 600,
                "npc.raids_vanguard_melee" to 180,
                "npc.raids_vasanistirio_walking" to 225,
                "npc.raids_vespula_flying" to 200,
                "npc.raids_dogodile" to 250,
                "npc.raids_dogodile_junior" to 135,
                "npc.olm_head" to 800,
                "npc.olm_hand_left" to 400,
            )
        for ((sym, hp) in forms) {
            val type = checkNotNull(ServerCacheManager.getNpc(sym.asRSCM(RSCMType.NPC))) { sym }
            assertTrue(type.hitpoints >= hp, "$sym hitpoints=${type.hitpoints} expected >=$hp")
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
