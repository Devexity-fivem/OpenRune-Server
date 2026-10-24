package org.rsmod.content.bosses.giantmole

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.cinematic.MinimapState
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.darkness by intVarBit("varbit.darkness_level")

class GiantMoleDigHook @Inject constructor(private val locRepo: LocRepository) : SpadeDigHook {

    override fun claims(player: Player): Boolean {
        if (player.coords.level != 0) return false
        val p = player.coords
        for (dx in -8..8 step 8) {
            for (dz in -8..8 step 8) {
                val near =
                    locRepo.findAll(ZoneKey.from(CoordGrid(p.x + dx, p.z + dz, 0))).any {
                        it.id in MOLE_HILLS &&
                            abs(p.x - it.coords.x) <= 3 &&
                            abs(p.z - it.coords.z) <= 3
                    }
                if (near) return true
            }
        }
        return false
    }

    override suspend fun ProtectedAccess.dig() {
        telejump(LAIR_ENTRY)
        if (!hasLight()) {
            player.darkness = 3
            Cinematic.setMinimapState(player, MinimapState.MinimapHidden)
            ifOpenFullOverlay("interface.darkness_dark")
        }
    }

    private fun ProtectedAccess.hasLight(): Boolean =
        LIGHT_SOURCES.any { inv.count(it) > 0 || player.worn.count(it) > 0 }

    private companion object {
        val MOLE_HILLS =
            setOf("loc.mole_hill".asRSCM().toInt(), "loc.mole_hill_spade".asRSCM().toInt())
        val LAIR_ENTRY = CoordGrid(1759, 5233, 0)

        val LIGHT_SOURCES =
            listOf(
                "obj.lit_candle",
                "obj.lit_black_candle",
                "obj.torch_lit",
                "obj.candle_lantern_lit",
                "obj.candle_lantern_black_lit",
                "obj.oil_lamp_lit",
                "obj.oil_lantern_lit",
                "obj.bullseye_lantern_lit",
                "obj.cave_goblin_mining_helmet_lit",
            )
    }
}

class GiantMoleLair @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.mole_rope_01") { exit() }
        onOpLoc1("loc.mole_rope_02") { exit() }
    }

    private suspend fun ProtectedAccess.exit() {
        telejump(SURFACE_EXIT)
        if (player.darkness != 0) {
            player.darkness = 0
            Cinematic.setMinimapState(player, MinimapState.Normal)
            ifCloseSub("interface.darkness_dark")
        }
    }

    private companion object {
        val SURFACE_EXIT = CoordGrid(2956, 3363, 0)
    }
}
