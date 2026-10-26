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
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterBirdSnaresRow
import org.rsmod.api.table.hunter.HunterBoxTrapsRow
import org.rsmod.api.table.hunter.HunterDeadfallLogsRow
import org.rsmod.api.table.hunter.HunterDeadfallRow
import org.rsmod.api.table.hunter.HunterNetTrapsRow
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

    private val deadfallPrey =
        HunterDeadfallRow.all().map { row ->
            HunterTrapManager.TrapPrey(
                name = ServerCacheManager.getNpc(row.prey.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.prey.internalName,
                npcSymbol = row.prey.internalName,
                npcId = row.prey.id,
                level = row.level,
                xpTenths = row.xp,
                fullLoc = row.fullLoc.internalName,
                trappingLocs = Dir.entries.associateWith { row.trapping.internalName },
                mirroredTrappingLocs = Dir.entries.associateWith { row.trappingM.internalName },
                fixedDrops =
                    listOfNotNull("obj.bones", row.part.internalName, row.meat?.internalName),
            )
        }

    val netPrey =
        HunterNetTrapsRow.all().map { row ->
            HunterTrapManager.TrapPrey(
                name = ServerCacheManager.getNpc(row.prey.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.prey.internalName,
                npcSymbol = row.prey.internalName,
                npcId = row.prey.id,
                level = row.level,
                xpTenths = row.xp,
                fullLoc = row.fullLoc.internalName,
                trappingLocs = Dir.entries.associateWith { row.catching.internalName },
                caughtItem = row.caught.internalName,
                treeLoc = row.tree.internalName,
                armedLoc = row.armed.internalName,
                failingLoc = row.failing.internalName,
                failedLoc = row.failed.internalName,
                bait = row.bait.internalName,
            )
        }

    // Ordered lowest-tier first so the cheapest valid log is consumed.
    private val deadfallLogs = HunterDeadfallLogsRow.all().map { it.log.internalName }

    var logPreserveChance: Double = 0.75

    init {
        traps.registerPrey(TrapKind.SNARE, birds)
        traps.registerPrey(TrapKind.BOX, boxPrey)
        traps.registerPrey(TrapKind.DEADFALL, deadfallPrey)
        traps.registerPrey(TrapKind.NET, netPrey)
    }

    override fun ScriptContext.startup() {
        for (kind in TrapKind.entries) {
            val item = kind.item ?: continue
            onOpHeld1(item) { layTrap(kind) }
        }

        for (kind in TrapKind.entries) {
            val armed = kind.armedLoc ?: continue
            onLocOp(armed, "Dismantle") { dismantleTrap(it.coords) }
            val broken = kind.brokenLoc ?: continue
            onLocOp(broken, "Dismantle") { dismantleTrap(it.coords) }
            onLocOp(broken, "Reset") { loc ->
                traps.trapAt(loc.coords)?.let(traps::reset)
            }
        }

        onLocOp(DEADFALL_BOULDER, "Set-trap") { setDeadfallTrap(it) }

        for (prey in netPrey) {
            val tree = prey.treeLoc ?: continue
            onLocOp(tree, "Set-trap") { setNetTrap(it, prey) }
            prey.armedLoc?.let { armed ->
                onLocOp(armed, "Dismantle") { loc -> dismantleTrap(loc.coords) }
                prey.bait?.let { bait -> onOpLocU(armed, bait) { baitTrap(it.loc.coords, bait) } }
            }
            prey.failedLoc?.let { failed ->
                onLocOp(failed, "Dismantle") { loc -> dismantleTrap(loc.coords) }
                onLocOp(failed, "Reset") { loc -> traps.trapAt(loc.coords)?.let(traps::reset) }
            }
        }

        for (prey in birds + boxPrey + deadfallPrey + netPrey) {
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
        if (!traps.canSet(player, kind)) {
            mes("You cannot set up more traps at your Hunter level.")
            return
        }
        if (traps.trapAt(player.coords) != null || !inv.contains(kind.item ?: return)) {
            return
        }
        anim(LAY_SEQ)
        invDel(inv, kind.item, 1)
        traps.layTrap(player, player.coords, kind)
    }

    fun ProtectedAccess.setDeadfallTrap(bound: BoundLocInfo) {
        if (!traps.canSet(player, TrapKind.DEADFALL)) {
            mes("You cannot set up more traps at your Hunter level.")
            return
        }
        if (traps.trapAt(bound.coords) != null) {
            return
        }
        if (!inv.contains(KNIFE) && !inv.contains(FLETCHING_KNIFE)) {
            mes("You need a knife to set up a deadfall trap.")
            return
        }
        val log = deadfallLogs.firstOrNull { inv.contains(it) }
        if (log == null) {
            mes("You need a log to prop up the boulder.")
            return
        }
        anim(LAY_SEQ)
        if (Random.nextDouble() >= logPreserveChance) {
            invDel(inv, log, 1)
        }
        traps.setDeadfall(player, bound.coords, bound.angle, bound.shape)
    }

    fun ProtectedAccess.setNetTrap(bound: BoundLocInfo, prey: HunterTrapManager.TrapPrey) {
        if (!traps.canSet(player, TrapKind.NET)) {
            mes("You cannot set up more traps at your Hunter level.")
            return
        }
        if (traps.trapAt(bound.coords) != null) {
            return
        }
        if (player.hunterLvl < prey.level) {
            mes("You need a Hunter level of at least ${prey.level} to catch a ${prey.name}.")
            return
        }
        if (!inv.contains(ROPE) || !inv.contains(SMALL_NET)) {
            mes("You need a rope and a small fishing net to set this trap.")
            return
        }
        anim(LAY_SEQ)
        invDel(inv, ROPE, 1)
        invDel(inv, SMALL_NET, 1)
        traps.setNetTrap(player, bound.coords, bound.angle, bound.shape, prey)
    }

    fun ProtectedAccess.baitTrap(coords: CoordGrid, bait: String) {
        val trap = traps.trapAt(coords) ?: return
        if (trap.baited) return
        invDel(inv, bait, 1)
        trap.baited = true
        mes("You bait the trap.")
    }

    fun ProtectedAccess.dismantleTrap(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        if (traps.dismantle(trap)) {
            trap.kind.item?.let { player.invAddOrDrop(objRepo, it) }
            trap.kind.materials.forEach { player.invAddOrDrop(objRepo, it) }
            mes("You dismantle the ${trap.kind.display}.")
        }
    }

    fun ProtectedAccess.checkTrap(coords: CoordGrid) {
        val trap = traps.trapAt(coords) ?: return
        val prey = traps.check(trap) ?: return
        trap.kind.item?.let { player.invAddOrDrop(objRepo, it) }
        trap.kind.materials.forEach { player.invAddOrDrop(objRepo, it) }
        prey.caughtItem?.let { player.invAddOrDrop(objRepo, it) }
        prey.fixedDrops.forEach { player.invAddOrDrop(objRepo, it) }
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
        const val DEADFALL_BOULDER = "loc.hunting_deadfall_boulder"
        const val KNIFE = "obj.knife"
        const val FLETCHING_KNIFE = "obj.fletching_knife"
        const val ROPE = "obj.rope"
        const val SMALL_NET = "obj.net"
        const val RAW_BIRD_MEAT = "obj.spit_raw_bird_meat"
        const val STAT_HUNTER = "stat.hunter"
        const val LAY_SEQ = "seq.hunting_baiting_small_traps"
    }
}
