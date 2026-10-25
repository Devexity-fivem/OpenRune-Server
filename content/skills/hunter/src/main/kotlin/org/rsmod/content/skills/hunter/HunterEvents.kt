package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.protect.ProtectedAccess
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
import org.rsmod.api.table.hunter.HunterBoxTrapsRow
import org.rsmod.content.skills.hunter.HunterTrapManager.Dir
import org.rsmod.content.skills.hunter.HunterTrapManager.TrapKind
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
            HunterTrapManager.TrapPrey(
                name = ServerCacheManager.getNpc(row.bird.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.bird.internalName,
                npcSymbol = row.bird.internalName,
                npcId = row.bird.id,
                level = row.level,
                xpTenths = row.xp,
                fullLoc = row.fullLoc.internalName,
                trappingLocs = Dir.entries.associateWith { row.trappingLoc.internalName },
                feather = row.feather.internalName,
                featherMin = row.featherMin,
                featherMax = row.featherMax,
            )
        }

    private val boxPrey =
        HunterBoxTrapsRow.all().map { row ->
            HunterTrapManager.TrapPrey(
                name = ServerCacheManager.getNpc(row.prey.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.prey.internalName,
                npcSymbol = row.prey.internalName,
                npcId = row.prey.id,
                level = row.level,
                xpTenths = row.xp,
                fullLoc = row.fullLoc.internalName,
                trappingLocs =
                    mapOf(
                        Dir.NORTH to row.trappingN.internalName,
                        Dir.SOUTH to row.trappingS.internalName,
                        Dir.EAST to row.trappingE.internalName,
                        Dir.WEST to row.trappingW.internalName,
                    ),
                caughtItem = row.caught.internalName,
            )
        }

    init {
        traps.registerPrey(TrapKind.SNARE, birds)
        traps.registerPrey(TrapKind.BOX, boxPrey)
    }

    override fun ScriptContext.startup() {
        onOpHeld1(TrapKind.SNARE.item) { layTrap(TrapKind.SNARE) }
        onOpHeld1(TrapKind.BOX.item) { layTrap(TrapKind.BOX) }

        for (kind in TrapKind.entries) {
            onLocOp(kind.armedLoc, "Dismantle") { dismantleTrap(it.coords) }
            onLocOp(kind.brokenLoc, "Dismantle") { dismantleTrap(it.coords) }
            onLocOp(kind.brokenLoc, "Reset") { loc ->
                traps.trapAt(loc.coords)?.let(traps::reset)
            }
        }
        for (prey in birds + boxPrey) {
            onLocOp(prey.fullLoc, "Check") { loc -> checkTrap(loc.coords) }
            onLocOp(prey.fullLoc, "Dismantle") { loc -> dismantleTrap(loc.coords) }
            onLocOp(prey.fullLoc, "Reset") { loc ->
                traps.trapAt(loc.coords)?.let(traps::reset)
            }
        }

        onEvent<GameLifecycle.LateCycle> { traps.tick() }
        onPlayerLogout { traps.clear(player) }
    }

    fun ProtectedAccess.layTrap(kind: TrapKind) {
        if (traps.activeTraps(player) >= traps.trapLimit(player, kind)) {
            mes("You cannot set up more traps at your Hunter level.")
            return
        }
        if (traps.trapAt(player.coords) != null || !inv.contains(kind.item)) {
            return
        }
        anim(LAY_SEQ)
        invDel(inv, kind.item, 1)
        traps.layTrap(player, player.coords, kind)
    }

    fun ProtectedAccess.dismantleTrap(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        if (traps.dismantle(trap)) {
            player.invAddOrDrop(objRepo, trap.kind.item)
            mes("You dismantle the ${trap.kind.display}.")
        }
    }

    fun ProtectedAccess.checkTrap(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        val prey = traps.check(trap) ?: return
        player.invAddOrDrop(objRepo, trap.kind.item)
        prey.caughtItem?.let { player.invAddOrDrop(objRepo, it) }
        if (prey.feather != null) {
            player.invAddOrDrop(objRepo, "obj.bones")
            player.invAddOrDrop(objRepo, RAW_BIRD_MEAT)
            val feathers = Random.nextInt(prey.featherMin, prey.featherMax + 1)
            player.invAddOrDrop(objRepo, prey.feather, feathers)
        }
        statAdvance(STAT_HUNTER, prey.xpTenths / 10.0 * xpMods.get(player, STAT_HUNTER))
        mes("You've caught a ${prey.name}!")
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
        const val RAW_BIRD_MEAT = "obj.spit_raw_bird_meat"
        const val STAT_HUNTER = "stat.hunter"
        const val LAY_SEQ = "seq.hunting_baiting_small_traps"
    }
}
