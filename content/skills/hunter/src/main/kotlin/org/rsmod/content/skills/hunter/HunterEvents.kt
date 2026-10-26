package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.rs.RSDropTable
import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.config.refs.params
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterBirdSnaresRow
import org.rsmod.api.table.hunter.HunterBoxTrapsRow
import org.rsmod.api.table.hunter.HunterButterfliesRow
import org.rsmod.api.table.hunter.HunterDeadfallLogsRow
import org.rsmod.api.table.hunter.HunterDeadfallRow
import org.rsmod.api.table.hunter.HunterImplingsRow
import org.rsmod.api.table.hunter.HunterNetTrapsRow
import org.rsmod.content.skills.hunter.HunterTrapManager.Dir
import org.rsmod.content.skills.hunter.HunterTrapManager.TrapKind
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class HunterEvents
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val traps: HunterTrapManager,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
    private val dropRegistry: DropTableRegistry,
    private val implingSpawns: ImplingSpawnManager,
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

    val butterflies =
        HunterButterfliesRow.all().map { row ->
            ButterflyPrey(
                npcSymbol = row.prey.internalName,
                name = ServerCacheManager.getNpc(row.prey.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.prey.internalName,
                level = row.level,
                xpTenths = row.xp,
                jarred = row.jarred.internalName,
                releaseAnim = RSCM.getReverseMapping(RSCMType.SPOTANIM, row.releaseAnim),
            )
        }

    val implings =
        HunterImplingsRow.all().map { row ->
            ImplingPrey(
                npcSymbol = row.npc.internalName,
                npcMazeSymbol = row.npcMaze?.internalName,
                name = ServerCacheManager.getNpc(row.npc.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.npc.internalName,
                level = row.level,
                xpPuroTenths = row.xpPuro,
                xpGielinorTenths = row.xpGielinor,
                jarred = row.jarred.internalName,
                respawn = row.respawn,
            )
        }

    private val implingByNpcSymbol: Map<String, ImplingPrey> = buildMap {
        for (prey in implings) {
            put(prey.npcSymbol, prey)
            prey.npcMazeSymbol?.let { put(it, prey) }
        }
        val crystal = implings.firstOrNull { it.jarred == "obj.ii_captured_impling_12" }
        if (crystal != null) {
            for (npc in NamedEnums.hunter_crystal_implings.values.filterNotNull()) {
                put(npc.internalName, crystal)
            }
        }
    }

    var logPreserveChance: Double = 0.75

    /**
     * Catch success chance per attempt: 12% at level 1 scaling linearly to 98% at
     * 99, plus 8 percentage points when catching barehanded or with a magic
     * butterfly net (Mod Ash, 8 July 2021).
     */
    var implingCatchChance: (Int, Boolean) -> Double = { level, boosted ->
        val base = 12.0 + (98.0 - 12.0) * (level - 1) / 98.0
        (base + if (boosted) 8.0 else 0.0).coerceAtMost(100.0) / 100.0
    }

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

        for (butterfly in butterflies) {
            onOpNpc1(butterfly.npcSymbol) { catchButterfly(it.npc, butterfly) }
            onOpHeld4(butterfly.jarred) { releaseButterfly(butterfly) }
        }

        for ((npcSymbol, prey) in implingByNpcSymbol) {
            onOpNpc1(npcSymbol) { catchImpling(it.npc, prey) }
        }
        for (prey in implings) {
            onOpHeld3(prey.jarred) { lootImpling(prey) }
            onOpHeld5(prey.jarred) { destroyImpling(it, prey) }
        }
        for (symbol in IMPLING_PRECURSORS) {
            onEvent<NpcStateEvents.Spawn>(symbol.asRSCM(RSCMType.NPC).toLong()) {
                implingSpawns.schedule(npc)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (implingSpawns.isPrecursor(npc)) {
                implingSpawns.schedule(npc)
            }
        }

        onEvent<GameLifecycle.LateCycle> {
            traps.tick()
            implingSpawns.tick()
        }
        onPlayerLogout { traps.clear(player) }
    }

    fun ProtectedAccess.catchButterfly(npc: Npc, butterfly: ButterflyPrey) {
        if (player.hunterLvl < butterfly.level) {
            mes("You need a Hunter level of at least ${butterfly.level} to catch a ${butterfly.name}.")
            return
        }
        if (BUTTERFLY_NET !in player.worn && MAGIC_NET !in player.worn) {
            mes("You need a butterfly net equipped to catch butterflies.")
            return
        }
        if (!inv.contains(BUTTERFLY_JAR)) {
            mes("You need an empty butterfly jar to catch butterflies.")
            return
        }
        anim(SMALL_NET_SEQ)
        if (Random.nextDouble() < traps.catchChance(player.hunterLvl, butterfly.level)) {
            npcRepo.del(npc, BUTTERFLY_RESPAWN_TICKS)
            invDel(inv, BUTTERFLY_JAR, 1)
            player.invAddOrDrop(objRepo, butterfly.jarred)
            statAdvance(
                STAT_HUNTER,
                butterfly.xpTenths / 10.0 * xpMods.get(player, STAT_HUNTER),
            )
            mes("You catch a ${butterfly.name}.")
        } else {
            val dx = Random.nextInt(-FLEE_RANGE, FLEE_RANGE + 1)
            val dz = Random.nextInt(-FLEE_RANGE, FLEE_RANGE + 1)
            if (dx != 0 || dz != 0) {
                npc.teleport(collision, npc.coords.translate(dx, dz))
            }
            mes("The ${butterfly.name} slips through your net.")
        }
    }

    fun ProtectedAccess.releaseButterfly(butterfly: ButterflyPrey) {
        invDel(inv, butterfly.jarred, 1)
        player.invAddOrDrop(objRepo, BUTTERFLY_JAR)
        spotanim(butterfly.releaseAnim)
        mes("You release the ${butterfly.name}.")
    }

    fun ProtectedAccess.catchImpling(npc: Npc, prey: ImplingPrey) {
        val inPuro = npc.type.internalName.endsWith("_maze")
        val normalNet = BUTTERFLY_NET in player.worn
        val magicNet = MAGIC_NET in player.worn
        val netted = normalNet || magicNet
        val required = if (netted) prey.level else prey.level + IMPLING_BAREHANDED_BONUS
        if (player.hunterLvl < required) {
            val how = if (netted) "" else " barehanded"
            mes("You need a Hunter level of at least $required to catch an impling$how.")
            return
        }
        val requiresJar = netted || inPuro
        if (requiresJar && !inv.contains(IMPLING_JAR)) {
            mes("You need an empty impling jar to catch an impling.")
            return
        }
        val jarred = requiresJar || inv.contains(IMPLING_JAR)
        anim(IMPLING_CATCH_SEQ)
        val boosted = magicNet || !netted
        if (Random.nextDouble() < implingCatchChance(player.hunterLvl, boosted)) {
            npc.anim(IMPLING_CAUGHT_SEQ)
            npcRepo.del(npc, prey.respawn)
            if (jarred) {
                invDel(inv, IMPLING_JAR, 1)
                player.invAddOrDrop(objRepo, prey.jarred)
                mes("You manage to catch the impling.")
            } else {
                giveImplingLoot(prey)
                mes("You manage to catch the impling and acquire some loot.")
            }
            val xpTenths = if (inPuro) prey.xpPuroTenths else prey.xpGielinorTenths
            statAdvance(STAT_HUNTER, xpTenths / 10.0 * xpMods.get(player, STAT_HUNTER))
        } else {
            npc.anim(IMPLING_DODGE_SEQ)
            mes("The impling evades your attempt.")
        }
    }

    fun ProtectedAccess.lootImpling(prey: ImplingPrey) {
        val table = dropRegistry.forObj(prey.jarred) ?: return
        invDel(inv, prey.jarred, 1)
        giveImplingLoot(prey, table)
        mes("You rummage through the impling jar.")
    }

    suspend fun ProtectedAccess.destroyImpling(event: HeldObjEvents.Op5, prey: ImplingPrey) {
        val header =
            event.type.paramOrNull(params.destroy_note_title)
                ?: "Are you sure you want to destroy this jar?"
        val text =
            event.type.paramOrNull(params.destroy_note_desc)
                ?: "If you destroy the jar, the impling inside will escape."
        if (!confirmDestroy(prey.jarred, 1, header, text)) {
            return
        }
        invDel(inv, prey.jarred, 1)
    }

    private fun ProtectedAccess.giveImplingLoot(
        prey: ImplingPrey,
        table: RSDropTable<Player, DropRollItem>? = dropRegistry.forObj(prey.jarred),
    ) {
        if (table == null) return
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> giveImplingDrop(result.result)
            is RollResult.ListOf -> result.results.forEach { giveImplingDrop(it) }
        }
    }

    private fun ProtectedAccess.giveImplingDrop(drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        player.invAddOrDrop(objRepo, obj, drop.rollCount(random))
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

    class ButterflyPrey(
        val npcSymbol: String,
        val name: String,
        val level: Int,
        val xpTenths: Int,
        val jarred: String,
        val releaseAnim: String,
    )

    class ImplingPrey(
        val npcSymbol: String,
        val npcMazeSymbol: String?,
        val name: String,
        val level: Int,
        val xpPuroTenths: Int,
        val xpGielinorTenths: Int,
        val jarred: String,
        val respawn: Int,
    )

    private companion object {
        const val DEADFALL_BOULDER = "loc.hunting_deadfall_boulder"
        const val KNIFE = "obj.knife"
        const val FLETCHING_KNIFE = "obj.fletching_knife"
        const val ROPE = "obj.rope"
        const val SMALL_NET = "obj.net"
        const val BUTTERFLY_NET = "obj.hunting_butterfly_net"
        const val MAGIC_NET = "obj.ii_magic_butterfly_net"
        const val BUTTERFLY_JAR = "obj.butterfly_jar"
        const val IMPLING_JAR = "obj.ii_impling_jar"
        const val IMPLING_BAREHANDED_BONUS = 10
        const val IMPLING_CATCH_SEQ = "seq.hunting_impling_catch"
        const val IMPLING_DODGE_SEQ = "seq.ii_impling_dodge"
        const val IMPLING_CAUGHT_SEQ = "seq.ii_impling_caught"
        val IMPLING_PRECURSORS =
            setOf(
                "npc.ii_common_impling_precursor",
                "npc.ii_uncommon_impling_precursor",
                "npc.ii_rare_impling_precursor",
                "npc.ii_rare_impling_precursor_maze",
                "npc.ii_impling_type_12_precursor",
            )
        const val SMALL_NET_SEQ = "seq.human_smallnet"
        const val BUTTERFLY_RESPAWN_TICKS = 100
        const val FLEE_RANGE = 4
        const val RAW_BIRD_MEAT = "obj.spit_raw_bird_meat"
        const val STAT_HUNTER = "stat.hunter"
        const val LAY_SEQ = "seq.hunting_baiting_small_traps"
    }
}
