package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
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
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.worn.WornUnequipOp
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
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.hunter.HunterBirdSnaresRow
import org.rsmod.api.table.hunter.HunterBoxTrapsRow
import org.rsmod.api.table.hunter.HunterButterfliesRow
import org.rsmod.api.table.hunter.HunterDeadfallLogsRow
import org.rsmod.api.table.hunter.HunterDeadfallRow
import org.rsmod.api.table.hunter.HunterFalconryRow
import org.rsmod.api.table.hunter.HunterImplingsRow
import org.rsmod.api.table.hunter.HunterNetTrapsRow
import org.rsmod.content.skills.hunter.HunterTrapManager.Dir
import org.rsmod.content.skills.hunter.HunterTrapManager.TrapKind
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
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
    private val falconry: FalconryManager,
    private val puro: PuroPuroManager,
    private val eventBus: EventBus,
    private val unequipOp: WornUnequipOp,
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

    val falconryPrey =
        HunterFalconryRow.all().map { row ->
            FalconryPrey(
                npcSymbol = row.npc.internalName,
                falconSymbol = row.falcon.internalName,
                falconType =
                    checkNotNull(
                        ServerCacheManager.getNpc(row.falcon.internalName.asRSCM(RSCMType.NPC))
                    ) { "Missing npc type: ${row.falcon.internalName}" },
                name = ServerCacheManager.getNpc(row.npc.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.npc.internalName,
                level = row.level,
                xpTenths = row.xp,
                fur = row.fur.internalName,
                meat = row.meat?.internalName,
                respawn = row.respawn,
            )
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

        for (prey in falconryPrey) {
            onOpNpc1(prey.npcSymbol) { sendFalcon(it.npc, prey) }
            onOpNpc1(prey.falconSymbol) { retrieveFalcon(it.npc, prey) }
        }
        for (falconer in FALCONER_NPCS) {
            onOpNpc1(falconer) { startDialogue(it.npc) { matthiasDialogue() } }
            onOpNpc3(falconer) { rentFalcon() }
        }
        onPlayerCoordsChanged {
            if (!FalconryManager.insideArea(player.coords)) {
                if (player.removeGlove()) {
                    player.mes(
                        "As you leave the falconry area, Matthias takes back the falconer's " +
                            "glove and your gyr falcon flies back to him."
                    )
                }
                if (falconry.recallOwned(player)) {
                    player.mes(
                        "Your falcon has left its prey. You see it heading back toward the falconer."
                    )
                }
            }
        }

        onEvent<GameLifecycle.LateCycle> {
            traps.tick()
            implingSpawns.tick()
            falconry.tick()
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
        val owner = puro.ownerOf(npc)
        if (owner != null && owner != player) {
            mes("You may only catch implings that you freed yourself.")
            return
        }
        val inPuro = npc.type.internalName.endsWith("_maze")
        val netted = BUTTERFLY_NET in player.worn || MAGIC_NET in player.worn
        val required = if (netted) prey.level else prey.level + IMPLING_BAREHANDED_BONUS
        if (player.hunterLvl < required) {
            val how = if (netted) "" else " barehanded"
            mes("You need a Hunter level of at least $required to catch an impling$how.")
            return
        }
        if ((netted || inPuro) && !inv.contains(IMPLING_JAR)) {
            mes("You need an empty impling jar to catch an impling.")
            return
        }
        val jarred = netted || inPuro
        anim(IMPLING_CATCH_SEQ)
        val boosted = MAGIC_NET in player.worn || !netted
        if (Random.nextDouble() < implingCatchChance(player.hunterLvl, boosted)) {
            npc.anim(IMPLING_CAUGHT_SEQ)
            npcRepo.del(npc, if (owner != null) Int.MAX_VALUE else prey.respawn)
            puro.forget(npc)
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
            puro.bumpTracker(player, implings.indexOf(prey), inPuro)
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

    fun ProtectedAccess.sendFalcon(npc: Npc, prey: FalconryPrey) {
        if (player.hunterLvl < prey.level) {
            mes("You need a Hunter level of at least ${prey.level} to catch a ${prey.name}.")
            return
        }
        if (FALCON_GLOVES_WITH !in player.worn) {
            if (FALCON_GLOVES_EMPTY in player.worn) {
                mes("Your falcon is no longer on your glove. Speak to Matthias to get it back.")
            } else {
                mes("You need a gyr falcon to catch kebbits here.")
            }
            return
        }
        anim(FALCON_LAUNCH_SEQ)
        player.setGlove(FALCON_GLOVES_EMPTY)
        if (Random.nextDouble() < traps.catchChance(player.hunterLvl, prey.level)) {
            npcRepo.del(npc, prey.respawn)
            falconry.land(Npc(prey.falconType, npc.coords), player)
        } else {
            player.setGlove(FALCON_GLOVES_WITH)
        }
    }

    private fun Player.setGlove(symbol: String) {
        val obj = InvObj(symbol)
        worn[Wearpos.RightHand.slot] = obj
        eventBus.publish(HeldEquipEvents.WearposChange(this, Wearpos.RightHand, getInvObj(obj)))
        rebuildAppearance()
    }

    /** Matthias takes the rented glove back on leaving; it is not returned to inventory. */
    private fun Player.removeGlove(): Boolean {
        val obj = worn[Wearpos.RightHand.slot] ?: return false
        if (!obj.isType(FALCON_GLOVES_WITH) && !obj.isType(FALCON_GLOVES_EMPTY)) {
            return false
        }
        worn[Wearpos.RightHand.slot] = null
        WornUnequipOp.notifyWornUnequip(this, Wearpos.RightHand, getInvObj(obj), eventBus)
        return true
    }

    fun ProtectedAccess.retrieveFalcon(npc: Npc, prey: FalconryPrey) {
        val owner = falconry.ownerOf(npc)
        if (owner != null && owner != player) {
            mes("That is not your falcon.")
            return
        }
        falconry.remove(npc)
        if (FALCON_GLOVES_EMPTY in player.worn) {
            player.setGlove(FALCON_GLOVES_WITH)
        }
        player.invAddOrDrop(objRepo, "obj.bones")
        player.invAddOrDrop(objRepo, prey.fur)
        prey.meat?.let { player.invAddOrDrop(objRepo, it) }
        statAdvance(STAT_HUNTER, prey.xpTenths / 10.0 * xpMods.get(player, STAT_HUNTER))
        mes("You retrieve your falcon along with its catch.")
    }

    /** The falcon hops back onto the empty glove; free while inside the falconry. */
    fun ProtectedAccess.reattachFalcon() {
        falconry.recallOwned(player)
        player.setGlove(FALCON_GLOVES_WITH)
        mes("The gyr falcon hops back onto your glove.")
    }

    fun ProtectedAccess.rentFalcon() {
        if (FALCON_GLOVES_WITH in player.worn) {
            mes("You already have a gyr falcon.")
            return
        }
        if (FALCON_GLOVES_EMPTY in player.worn) {
            reattachFalcon()
            return
        }
        val slots = listOf(Wearpos.RightHand.slot, Wearpos.LeftHand.slot, Wearpos.Hands.slot)
        val occupied = slots.filter { player.worn[it] != null }
        if (player.inv.freeSpace() < occupied.size) {
            mes(
                "You need to free your hands, weapon and shield slots before Matthias " +
                    "will lend you a falcon."
            )
            return
        }
        if (vars[VAR_FALCONRY_INVESTED] != 1) {
            if (inv.count(COINS) < FALCON_COST) {
                mes("You need $FALCON_COST coins to rent a falcon from Matthias.")
                return
            }
            invDel(inv, COINS, FALCON_COST)
        }
        for (slot in occupied) {
            unequipOp.unequip(player, slot, player.worn, player.inv)
        }
        falconry.recallOwned(player)
        player.setGlove(FALCON_GLOVES_WITH)
        mes("Matthias hands you a falconer's glove with a gyr falcon sitting on it.")
    }

    fun ProtectedAccess.investFalconry() {
        if (vars[VAR_FALCONRY_INVESTED] == 1) {
            return
        }
        if (inv.count(COINS) < FALCONRY_INVEST_COST) {
            mes("You need $FALCONRY_INVEST_COST coins to invest in Matthias' operation.")
            return
        }
        invDel(inv, COINS, FALCONRY_INVEST_COST)
        vars[VAR_FALCONRY_INVESTED] = 1
        mes("You invest in Matthias' falconry operation. His falcons are now free to borrow.")
    }

    private suspend fun Dialogue.matthiasDialogue() {
        when {
            FALCON_GLOVES_WITH in player.worn ->
                chatNpc(neutral, "You already have one of my falcons. Look after it!")
            FALCON_GLOVES_EMPTY in player.worn -> {
                chatPlayer(quiz, "My falcon seems to have flown back to you.")
                chatNpc(happy, "Ah, so she did. Here, take her again.")
                access.reattachFalcon()
            }
            access.vars[VAR_FALCONRY_INVESTED] == 1 -> {
                chatNpc(happy, "Hello again! Want to take one of my falcons out?")
                if (choice2("Yes please.", true, "No thanks.", false)) {
                    access.rentFalcon()
                }
            }
            else -> {
                chatNpc(
                    happy,
                    "Hello! Would you like to rent one of my gyr falcons? " +
                        "It's 500 coins a hunt.",
                )
                when (
                    choice3(
                        "Yes, that sounds fine.",
                        1,
                        "Can I pay in advance?",
                        2,
                        "No thanks.",
                        3,
                    )
                ) {
                    1 -> access.rentFalcon()
                    2 -> {
                        chatPlayer(quiz, "Can I pay in advance?")
                        chatNpc(
                            neutral,
                            "A one-off investment of 500,000 coins and you'll " +
                                "never have to pay me again.",
                        )
                        if (choice2("Invest 500,000 coins.", true, "No thanks.", false)) {
                            access.investFalconry()
                        }
                    }
                }
            }
        }
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

    class FalconryPrey(
        val npcSymbol: String,
        val falconSymbol: String,
        val falconType: NpcServerType,
        val name: String,
        val level: Int,
        val xpTenths: Int,
        val fur: String,
        val meat: String?,
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
        const val FALCON_GLOVES_WITH = "obj.falcon_on_gloves"
        const val FALCON_GLOVES_EMPTY = "obj.falcon_gloves"
        const val FALCON_LAUNCH_SEQ = "seq.human_falconer_attack"
        const val COINS = "obj.coins"
        const val FALCON_COST = 500
        const val FALCONRY_INVEST_COST = 500_000
        const val VAR_FALCONRY_INVESTED = "varbit.hunter_falconry_invested"
        val FALCONER_NPCS = setOf("npc.hunting_npc_falconer", "npc.hunting_npc_falconer_nobird")
    }
}
