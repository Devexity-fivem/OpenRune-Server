package org.rsmod.content.bosses.giantmole

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.cinematic.MinimapState
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc5
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.darkness by intVarBit("varbit.darkness_level")

internal fun Player.hasLightSource(): Boolean =
    LIGHT_SOURCES.any { inv.count(it) > 0 || worn.count(it) > 0 }

internal fun Player.clearDarkness(eventBus: org.rsmod.events.EventBus) {
    if (darkness == 0) return
    darkness = 0
    Cinematic.setMinimapState(this, MinimapState.Normal)
    ifCloseSub("interface.darkness_dark", eventBus)
}

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
        if (!player.hasLightSource()) {
            player.darkness = 3
            Cinematic.setMinimapState(player, MinimapState.MinimapHidden)
            ifOpenFullOverlay("interface.darkness_dark")
        }
    }

    private companion object {
        val MOLE_HILLS =
            setOf("loc.mole_hill".asRSCM().toInt(), "loc.mole_hill_spade".asRSCM().toInt())
        val LAIR_ENTRY = CoordGrid(1759, 5190, 0)
    }
}

internal val LIGHT_SOURCES =
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

class GiantMoleLair @Inject constructor(private val playerList: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc5("loc.mole_hill") { lookInside() }
    }

    private fun ProtectedAccess.lookInside() {
        val inside =
            playerList.count {
                it.coords.level == 0 && it.coords.x in 1728..1791 && it.coords.z in 5120..5239
            }
        spam("Inside the mole hill there are $inside players.")
    }
}

/**
 * Biting insects in unlit areas: 15 ticks of darkness warns, 30 ticks swarms and starts
 * dealing 1 typeless damage per tick. Stops when a light source is carried or the player
 * leaves darkness (varbit cleared on exit).
 */
class GiantMoleInsects @Inject constructor(
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val hitProcessor: InstantPlayerHitProcessor,
    private val instances: InstanceManager,
    private val eventBus: EventBus,
) : PluginScript() {
    private val darkTicks = HashMap<Player, Int>()

    override fun ScriptContext.startup() {
        worldQueues.add(1) { tick() }
    }

    private fun tick() {
        for (player in playerList) {
            val inLair = player.inMoleLair()
            if (!inLair) {
                if (darkTicks.remove(player) != null) {
                    player.clearDarkness(eventBus)
                }
                continue
            }
            if (player.hasLightSource() || player.darkness != DARK) {
                if (player.hasLightSource()) {
                    player.clearDarkness(eventBus)
                }
                darkTicks.remove(player)
                continue
            }
            val ticks = (darkTicks[player] ?: 0) + 1
            darkTicks[player] = ticks
            if (ticks == WARNING_TICKS) {
                player.spam("You hear tiny insects skittering over the ground...")
                player.soundSynth("synth.swamp_cave_insects")
            }
            if (ticks >= SWARM_TICKS) {
                if (ticks == SWARM_TICKS) {
                    player.spam("Tiny biting insects swarm all over you!")
                }
                player.takeInstantHit(HitType.Typeless, 1, hitProcessor)
            }
        }
        darkTicks.keys.retainAll(playerList.toSet())
        worldQueues.add(1) { tick() }
    }

    private fun Player.inMoleLair(): Boolean {
        if (coords.level != 0) return false
        if (coords.x in 1728..1791 && coords.z in 5120..5239) return true
        return instances.sessionForPlayer(this)?.key == "giant_mole"
    }

    private companion object {
        private const val WARNING_TICKS = 15
        private const val SWARM_TICKS = 30
        private const val DARK = 3
    }
}
