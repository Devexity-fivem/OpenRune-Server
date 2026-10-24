package org.rsmod.content.bosses.giantmole

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.player.output.spam
import org.rsmod.api.table.LightSourcesRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class GiantMole @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = giantMoleSpec()

    init {
        deps.extensionRegistry.register("giant_mole.burrow") { _, npc, _, _ -> burrow(npc) }
    }

    private fun burrow(npc: Npc) {
        val burrowTile = npc.coords
        if (Random.nextInt(DIRT_CHANCE) == 0) {
            extinguishLights(burrowTile)
        }
        val dest = RESURFACE_SPOTS.filter { it != burrowTile }.random()
        npc.telejump(deps.collision, dest)
    }

    private fun extinguishLights(burrowTile: CoordGrid) {
        val openSources =
            LightSourcesRow.all().filter { it.lit.internalName in OPEN_LIGHT_SOURCES }
        for (player in deps.playerList) {
            if (player == null) continue
            if (abs(player.coords.x - burrowTile.x) > 2 || abs(player.coords.z - burrowTile.z) > 2) {
                continue
            }
            var snuffed = false
            for (inv in listOf(player.inv, player.worn)) {
                for ((slot, obj) in inv.objs.withIndex()) {
                    val row = openSources.firstOrNull { it.lit.id == obj?.id } ?: continue
                    inv[slot] = InvObj(row.unlit, obj!!.count, obj!!.vars)
                    snuffed = true
                }
            }
            if (snuffed) {
                player.spam("The mole's dirt has extinguished your light source!")
            }
        }
    }

    companion object {
        // Wiki notes an additional, undocumented chance of dirt extinguishing
        // open light sources (candles/torches/oil lamps) in melee range.
        const val DIRT_CHANCE = 3

        val OPEN_LIGHT_SOURCES =
            setOf("lit_candle", "lit_black_candle", "torch_lit", "oil_lamp_lit")

        val RESURFACE_SPOTS =
            listOf(
                CoordGrid(1739, 5177, 0),
                CoordGrid(1741, 5188, 0),
                CoordGrid(1756, 5164, 0),
                CoordGrid(1761, 5161, 0),
                CoordGrid(1760, 5215, 0),
                CoordGrid(1772, 5200, 0),
                CoordGrid(1776, 5241, 0),
                CoordGrid(1736, 5227, 0),
            )
    }
}

fun giantMoleSpec(): BossSpec = boss("npc.mole_giant") {
    stats(attackRate = 4)

    val melee = ability("melee") {
        anim("seq.mole_attack")
        hit {
            damage(0..21).roll()
            type(Melee)
        }
    }

    val burrow =
        ability(
            "burrow",
            chance(
                4,
                sequence(
                    anim("seq.mole_burrow_down"),
                    spotanim("spotanim.mole_mud_splat_spotanim"),
                    wait(2),
                    external("giant_mole.burrow"),
                    anim("seq.mole_burrow_up"),
                    spotanim("spotanim.mole_mud_hole_up_spotanim"),
                    interrupt(),
                ),
            ),
        )

    phase("combat") {
        weightedSelectorRandom { +random(melee, weight = 1) }
    }

    onIncomingHit(burrow, requires = BURROW_WINDOW and !MAGIC_SPLASH)
}

private val BURROW_WINDOW: Condition =
    Condition.HpBelow(0.5, inclusive = true) and !Condition.HpBelow(0.05)

private val MAGIC_SPLASH: Condition = hitStyle(Magic) and !hitDamageAtLeast(1)
