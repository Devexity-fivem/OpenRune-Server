package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterBirdSnaresRow
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HunterEvents
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val traps: HunterTrapManager,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val birds =
        HunterBirdSnaresRow.all().map { row ->
            HunterTrapManager.HunterBird(
                name = ServerCacheManager.getNpc(row.bird.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.bird.internalName,
                npcSymbol = row.bird.internalName,
                npcId = row.bird.id,
                level = row.level,
                xpTenths = row.xp,
                fullLoc = row.fullLoc.internalName,
                trappingLoc = row.trappingLoc.internalName,
                feather = row.feather.internalName,
                featherMin = row.featherMin,
                featherMax = row.featherMax,
            )
        }

    init {
        traps.registerBirds(birds)
    }

    override fun ScriptContext.startup() {
        onOpHeld1(BIRD_SNARE) { laySnare() }

        onLocOp(HunterTrapManager.SET_TRAP, "Dismantle") { dismantleSnare(it.coords) }
        onLocOp(HunterTrapManager.BROKEN_TRAP, "Dismantle") { dismantleSnare(it.coords) }
        onLocOp(HunterTrapManager.BROKEN_TRAP, "Reset") { loc ->
            traps.trapAt(loc.coords)?.let(traps::reset)
        }
        for (bird in birds) {
            onLocOp(bird.fullLoc, "Check") { loc -> checkTrap(loc.coords) }
            onLocOp(bird.fullLoc, "Dismantle") { loc -> dismantleSnare(loc.coords) }
            onLocOp(bird.fullLoc, "Reset") { loc ->
                traps.trapAt(loc.coords)?.let(traps::reset)
            }
        }

        onEvent<GameLifecycle.LateCycle> { traps.tick() }
        onPlayerLogout { traps.clear(player) }
    }

    fun ProtectedAccess.laySnare() {
        if (traps.activeTraps(player) >= traps.maxTraps(player.hunterLvl)) {
            mes("You cannot set up more traps at your Hunter level.")
            return
        }
        if (traps.trapAt(player.coords) != null || !inv.contains(BIRD_SNARE)) {
            return
        }
        anim(LAY_SEQ)
        invDel(inv, BIRD_SNARE, 1)
        traps.layTrap(player, player.coords)
    }

    fun ProtectedAccess.dismantleSnare(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        if (traps.dismantle(trap)) {
            player.invAddOrDrop(objRepo, BIRD_SNARE)
            mes("You dismantle the bird snare.")
        }
    }

    fun ProtectedAccess.checkTrap(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        val bird = traps.check(trap) ?: return
        player.invAddOrDrop(objRepo, BIRD_SNARE)
        player.invAddOrDrop(objRepo, "obj.bones")
        player.invAddOrDrop(objRepo, RAW_BIRD_MEAT)
        val feathers = Random.nextInt(bird.featherMin, bird.featherMax + 1)
        player.invAddOrDrop(objRepo, bird.feather, feathers)
        statAdvance(STAT_HUNTER, bird.xpTenths / 10.0 * xpMods.get(player, STAT_HUNTER))
        mes("You've caught a ${bird.name}!")
    }

    private fun ScriptContext.onLocOp(
        loc: String,
        opName: String,
        action: suspend ProtectedAccess.(BoundLocInfo) -> Unit,
    ) {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)) ?: return
        val index =
            (0 until 5).firstOrNull {
                (type.actions.getOpOrNull(it) ?: "").equals(opName, ignoreCase = true)
            } ?: return
        when (index) {
            0 -> onOpLoc1(loc) { action(it.loc) }
            1 -> onOpLoc2(loc) { action(it.loc) }
            2 -> onOpLoc3(loc) { action(it.loc) }
            3 -> onOpLoc4(loc) { action(it.loc) }
            4 -> onOpLoc5(loc) { action(it.loc) }
        }
    }

    private companion object {
        const val BIRD_SNARE = "obj.hunting_ojibway_bird_snare"
        const val RAW_BIRD_MEAT = "obj.spit_raw_bird_meat"
        const val STAT_HUNTER = "stat.hunter"
        const val LAY_SEQ = "seq.hunting_baiting_small_traps"
    }
}
