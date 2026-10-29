package org.rsmod.content.bosses.worldbosses

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector

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
    fun `moons specs match wiki mechanics`() {
        val specs =
            mapOf(
                "blood" to Triple(bloodMoonSpec(), listOf("rain", "jaguar"), intArrayOf(100, 200, 300)),
                "blue" to Triple(blueMoonSpec(), listOf("freeze", "storm"), intArrayOf(0, 0, 0)),
                "eclipse" to Triple(eclipseMoonSpec(), listOf("rays", "clones"), intArrayOf(0, 0, 0)),
            )
        for ((name, entry) in specs) {
            val (spec, branches, lifesteal) = entry
            assertEquals(6, spec.stats.attackRate, "$name attack speed should be 6 ticks")

            val melee = spec.abilities.getValue("melee") as Effect.Sequence
            val hit = melee.effects.filterIsInstance<Effect.Hit>().single()
            assertEquals(100, hit.penetration, "$name standard attack ignores prayers")
            assertEquals(lifesteal[0], hit.lifesteal)

            val hit2 = hit.onHit as? Effect.Hit
            val hit3 = hit2?.onHit as? Effect.Hit
            assertEquals(0..4, ((hit.damage as DamageExpr.Accuracy).on as DamageExpr.Roll).range)
            assertEquals(0..8, ((hit2?.damage as DamageExpr.Accuracy).on as DamageExpr.Roll).range)
            assertEquals(0..20, ((hit3?.damage as DamageExpr.Accuracy).on as DamageExpr.Roll).range)
            assertNull(hit3?.onHit, "$name triple-hit chain ends after third hit")
            assertEquals(lifesteal[1], hit2?.lifesteal)
            assertEquals(lifesteal[2], hit3?.lifesteal)
            if (name == "blood") {
                assertEquals(30, hit3?.lifestealMin, "blood third heal floors at 30")
                assertEquals(50, hit3?.lifestealMax, "blood third heal caps at 50")
            }

            val phase = spec.phases.getValue("combat")
            val forced = phase.forceAbilities.single { it.attackMin != null }
            assertEquals(6, forced.attackMin)
            assertEquals(6, forced.attackMax)
            assertEquals("special", forced.ability)

            val special = spec.abilities.getValue("special") as Effect.Choose
            assertEquals(branches, (special.selector as Selector.Rotation).sequence)
            assertEquals(branches.toSet(), special.branches.keys)
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
