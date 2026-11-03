package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.table.GotrRewardTableRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.mining.configs.MiningParams
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeAnim
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeWallAnim
import org.rsmod.content.skills.runecrafting.action.RunecraftAction.getBonusMultiplier
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Gotr
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldClock: MapClock,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val eventBus: EventBus,
) : PluginScript() {

    private val game = GotrGame()

    override fun ScriptContext.startup() {
        for (barrier in BARRIERS) {
            onOpLoc1(barrier) { passBarrier() }
            onOpLoc3(barrier) { passBarrier() }
        }
        for (node in Rock.entries) {
            onOpLoc1(node.loc) { mine(node, it.loc) }
        }
        onOpLoc1(WORKBENCH) { craftEssence(it.loc) }
        onOpLoc1(CELL_CRATE) { takeCells(10) }
        onOpLoc2(CELL_CRATE) { takeCells(1) }
        onOpLoc1(WEAK_CELL_CRATE) { takeWeakCell() }
        onOpLoc2(WEAK_CELL_CRATE) { takeChisel() }
        onOpLoc1(CHISEL_SPAWN) { takeChisel() }
        for (portal in Portal.entries) {
            onOpLoc1(portal.loc) { enterPortal(portal) }
        }
        onOpNpc1(GREAT_GUARDIAN) { powerUp() }
        onOpLoc1(CELL_TILE_INACTIVE) { useCellTile(it.loc) }
        onOpLoc1(CELL_TILE_BROKEN) { repairTile(it.loc) }
        for (tier in CellTier.entries) {
            onOpLoc1(tier.tileLoc) { useCellTile(it.loc) }
            for (cell in 1..4) {
                onOpLocU(tier.barrierLoc, "obj.gotr_cell_tier$cell") {
                    useCellOnBarrier(cell, it.loc)
                }
            }
        }
        onOpLoc1(ESSENCE_PILE_ELEMENTAL) { assembleGuardian(true) }
        onOpLoc1(ESSENCE_PILE_CATALYTIC) { assembleGuardian(false) }
        onOpLoc1(REWARD_RIFT) { searchRewards(1) }
        onOpLoc2(REWARD_RIFT) { checkRewards() }
        onOpLoc3(REWARD_RIFT) { searchRewards(5) }
        onOpHeld1(INTRICATE_POUCH) { openIntricatePouch() }
        onOpNpc3(REWARD_TRADER) { openShop() }
        onPlayerLogin { onLogin(player) }
        onPlayerLogout { game.leave(player) }
        onPlayerTimer(GOTR_TIMER) { gameTick() }
    }

    private fun onLogin(player: Player) {
        protectedAccess.launch(player) {
            if (inTemple(player.coords)) {
                ifOpenOverlay(HUD_OVERLAY, HUD_TARGET)
                if (vars[VB_IS_PLAYING] == 1) {
                    player.timerAt(GOTR_TIMER, worldClock.cycle, TICK_RATE)
                    game.join(player)
                }
            } else {
                exitTemple()
            }
        }
    }

    private fun ProtectedAccess.enterTemple() {
        ifOpenOverlay(HUD_OVERLAY, HUD_TARGET)
        vars[VB_IS_PLAYING] = 1
        player.timerAt(GOTR_TIMER, worldClock.cycle, TICK_RATE)
        game.join(player)
    }

    private fun ProtectedAccess.exitTemple() {
        player.ifCloseOverlay(HUD_OVERLAY, eventBus)
        game.leave(player)
        vars[VB_IS_PLAYING] = 0
        vars[VB_ELEMENTAL_EARNED] = 0
        vars[VB_CATALYTIC_EARNED] = 0
    }

    private fun ProtectedAccess.passBarrier() {
        if (player.coords.z >= ARENA_SPLIT_Z) {
            stopAction()
            exitTemple()
            teleport(LOBBY_SPAWN)
            mes("You return to the temple entrance.")
            return
        }
        if (player.statBase("stat.runecrafting") < MIN_RC_LEVEL) {
            mes("You need a Runecraft level of $MIN_RC_LEVEL to enter.")
            return
        }
        stopAction()
        teleport(ARENA_SPAWN)
        enterTemple()
        mes("You enter the Temple of the Eye. Help the Great Guardian close the rift!")
    }

    private fun ProtectedAccess.mine(node: Rock, loc: BoundLocInfo) {
        val pickaxe = Mining.findPickaxe(player) ?: run {
            mes("You need a pickaxe to mine these remains.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("Your inventory is too full to hold any more.")
            resetAnim()
            return
        }
        if (!inTemple(player.coords) || chebyshev(player.coords, loc.coords) > 2) {
            resetAnim()
            return
        }
        if (skillAnimDelay <= worldClock.cycle) {
            skillAnimDelay = worldClock.cycle + 4
            anim(mineAnim(pickaxe))
        }
        if (actionDelay < worldClock.cycle) {
            actionDelay = worldClock.cycle + Mining.pickaxeActionDelay(pickaxe, random)
        } else if (actionDelay == worldClock.cycle) {
            actionDelay = worldClock.cycle + Mining.pickaxeActionDelay(pickaxe, random)
            val amount = 1 + if (Random.nextInt(400) < player.statBase("stat.mining")) 1 else 0
            invAdd(inv, node.product, amount)
            statAdvance("stat.mining", node.xp.toDouble())
        }
        opLoc1(loc)
    }

    private fun ProtectedAccess.mineAnim(pickaxe: InvObj): String {
        val type = getInvObj(pickaxe)
        val seq =
            if (type.hasParam(MiningParams.skill_wall_anim)) {
                type.pickaxeWallAnim
            } else {
                type.pickaxeAnim
            }
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    private fun ProtectedAccess.craftEssence(loc: BoundLocInfo) {
        if (inv.count(OBJ_FRAGMENT) == 0) {
            mes("You need guardian fragments to craft essence here.")
            return
        }
        if (actionDelay > worldClock.cycle) {
            opLoc1(loc)
            return
        }
        val crafted = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, OBJ_FRAGMENT.asRSCM(RSCMType.OBJ), 1)
            add(inventory, OBJ_ESSENCE.asRSCM(RSCMType.OBJ), 1)
        }
        if (crafted.failure) {
            mes("Your inventory is too full to hold any more.")
            return
        }
        anim(CRAFT_ANIM)
        val xp = (player.statBase("stat.crafting") / 10.0).coerceIn(1.0, 5.0)
        statAdvance("stat.crafting", xp)
        actionDelay = worldClock.cycle + 1
        opLoc1(loc)
    }

    private fun ProtectedAccess.takeWeakCell() {
        if (heldCell() != null) {
            mes("You can only carry one charged cell at a time.")
            return
        }
        val taken = player.invTransaction(inv) {
            val inventory = select(inv)
            add(inventory, "obj.gotr_cell_tier1".asRSCM(RSCMType.OBJ), 1)
        }
        if (taken.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(TAKE_ANIM)
        mes("You take a weak cell.")
    }

    private fun ProtectedAccess.takeChisel() {
        anim(TAKE_ANIM)
        invAdd(inv, "obj.chisel", 1)
        mes("You take a chisel.")
    }

    private fun ProtectedAccess.takeCells(count: Int) {
        val room = MAX_CELLS - inv.count(OBJ_CELL_UNCHARGED)
        val take = minOf(room, count)
        if (take <= 0) {
            mes("You can't carry any more uncharged cells.")
            return
        }
        anim(TAKE_ANIM)
        invAdd(inv, OBJ_CELL_UNCHARGED, take)
        mes("You take $take uncharged cell${if (take == 1) "" else "s"}.")
    }

    private fun ProtectedAccess.enterPortal(portal: Portal) {
        if (!game.isBattle()) {
            mes("The portal guardians are dormant until the rift reopens.")
            return
        }
        if (!game.isActive(portal)) {
            mes("The ${portal.display} guardian is not currently active.")
            return
        }
        if (player.statBase("stat.runecrafting") < portal.rcReq) {
            mes("You need a Runecraft level of ${portal.rcReq} to use the ${portal.display} altar.")
            return
        }
        val essence = inv.count(OBJ_ESSENCE)
        val cells = inv.count(OBJ_CELL_UNCHARGED)
        val held = heldCell()
        val chargeSource = when {
            held != null && portal.cellTier > held.tier.tier -> held.obj
            held == null && cells > 0 -> OBJ_CELL_UNCHARGED
            else -> null
        }
        if (essence == 0 && chargeSource == null) {
            mes("You have no supplies that can be imbued at this altar.")
            return
        }
        val rune = if (essence > 0) {
            RunecraftingRunesRow.all().first { row ->
                row.output.internalName == portal.runeObj &&
                    row.input.any { it.internalName == EssencePouch.PURE_ESSENCE }
            }
        } else {
            null
        }
        val runes = if (rune != null) {
            essence * getBonusMultiplier(portal.runeObj, player.stat("stat.runecrafting")).toInt()
        } else {
            0
        }
        val talisman = (0 until essence).any { random.of(0, TALISMAN_RATE - 1) == 0 }
        val imbued = player.invTransaction(inv) {
            val inventory = select(inv)
            if (chargeSource != null) {
                delete(inventory, chargeSource.asRSCM(RSCMType.OBJ), 1)
                add(inventory, "obj.gotr_cell_tier${portal.cellTier}".asRSCM(RSCMType.OBJ), 1)
            }
            if (essence > 0) {
                delete(inventory, OBJ_ESSENCE.asRSCM(RSCMType.OBJ), essence)
                add(inventory, portal.stoneObj.asRSCM(RSCMType.OBJ), essence)
                add(inventory, portal.runeObj.asRSCM(RSCMType.OBJ), runes)
            }
            if (talisman) {
                add(inventory, portal.talismanObj.asRSCM(RSCMType.OBJ), 1)
            }
        }
        if (imbued.failure) {
            mes("You don't have enough inventory space to imbue your supplies.")
            return
        }
        anim(IMBUE_ANIM)
        if (chargeSource != null) {
            statAdvance("stat.runecrafting", CELL_XP.toDouble())
        }
        if (rune != null) {
            statAdvance("stat.runecrafting", essence * rune.xp / 10.0)
        }
        mes("You imbue your supplies at the ${portal.display} altar.")
    }

    private fun ProtectedAccess.powerUp() {
        val elemental = inv.count(OBJ_STONE_ELEMENTAL)
        val catalytic = inv.count(OBJ_STONE_CATALYTIC)
        val total = elemental + catalytic
        if (total == 0) {
            mes("You have no guardian stones to offer the Great Guardian.")
            return
        }
        if (!game.isBattle()) {
            mes("The Great Guardian is already fully charged.")
            return
        }
        anim(IMBUE_ANIM)
        greatGuardian()?.anim(SIPHON_ANIM, 0, 0)
        invDel(inv, OBJ_STONE_ELEMENTAL, elemental)
        invDel(inv, OBJ_STONE_CATALYTIC, catalytic)
        val earned = total * ENERGY_PER_STONE
        earnEnergy(elemental * ENERGY_PER_STONE, catalytic * ENERGY_PER_STONE)
        game.addCharge(this, earned)
        statAdvance("stat.runecrafting", total * 5.0)
        player.runEnergy = (player.runEnergy + total * 100).coerceAtMost(10_000)
        if (vars[VB_ELEMENTAL_EARNED] + vars[VB_CATALYTIC_EARNED] >= ENERGY_CAP_TOTAL) {
            mes("You are fully attuned with runic energy and cannot earn anymore.")
        } else {
            mes("The Great Guardian absorbs your guardian stones.")
        }
    }

    private fun ProtectedAccess.earnEnergy(elemental: Int, catalytic: Int) {
        if (elemental <= 0 && catalytic <= 0) return
        var room =
            (ENERGY_CAP_TOTAL - vars[VB_ELEMENTAL_EARNED] - vars[VB_CATALYTIC_EARNED])
                .coerceAtLeast(0)
        val e = minOf(elemental, room)
        room -= e
        val c = minOf(catalytic, room)
        vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + e).coerceAtMost(ENERGY_CAP_PER_TYPE)
        vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + c).coerceAtMost(ENERGY_CAP_PER_TYPE)
    }

    private fun ProtectedAccess.useCellTile(srcLoc: BoundLocInfo) {
        val tile = game.tileAt(srcLoc.coords) ?: run {
            mes("There is no cell tile here.")
            return
        }
        if (game.isBroken(tile)) {
            repairTile(srcLoc)
            return
        }
        if (game.hasBarrier(tile)) {
            val cell = heldCell() ?: run {
                mes("You need a charged cell to recharge or strengthen a barrier.")
                return
            }
            useCellOnBarrier(cell.tier.tier, srcLoc)
        } else {
            placeBarrier(srcLoc)
        }
    }

    private fun ProtectedAccess.placeBarrier(srcLoc: BoundLocInfo) {
        val cell = heldCell() ?: run {
            mes("You need a charged cell to place a barrier.")
            return
        }
        val tile = game.tileAt(srcLoc.coords) ?: run {
            mes("There is no cell tile here.")
            return
        }
        if (game.hasBarrier(tile)) {
            mes("A barrier already occupies this cell tile.")
            return
        }
        invDel(inv, cell.obj, 1)
        anim(BUILD_ANIM)
        game.placeBarrier(tile, cell.tier, srcLoc)
        earnEnergy(BUILD_ENERGY, BUILD_ENERGY)
        statAdvance("stat.runecrafting", cell.tier.buildXp.toDouble())
        mes("You erect a barrier on the cell tile.")
    }

    private fun ProtectedAccess.useCellOnBarrier(cellTier: Int, srcLoc: BoundLocInfo) {
        val tile = game.tileAt(srcLoc.coords) ?: return
        val existing = game.barrierTier(tile) ?: return
        val cell = CellTier.of(cellTier)
        if (inv.count("obj.gotr_cell_tier$cellTier") == 0) return
        if (cellTier > existing.tier) {
            invDel(inv, "obj.gotr_cell_tier$cellTier", 1)
            game.strengthen(tile, srcLoc)
            anim(BUILD_ANIM)
            earnEnergy(cell.strengthenEnergy, cell.strengthenEnergy)
            statAdvance("stat.runecrafting", cell.buildXp.toDouble())
            mes("You strengthen the barrier.")
        } else {
            val fullHp = game.isFullHealth(tile)
            if (fullHp && vars[VP_RECHARGE_ENERGY] >= RECHARGE_ENERGY_CAP) {
                mes("You have gained as much as you can from recharging barriers.")
                return
            }
            invDel(inv, "obj.gotr_cell_tier$cellTier", 1)
            game.recharge(tile, cell.heal)
            anim(BUILD_ANIM)
            val energy = if (fullHp) {
                (cell.rechargeEnergy).coerceAtMost(RECHARGE_ENERGY_CAP - vars[VP_RECHARGE_ENERGY])
            } else {
                cell.rechargeEnergy
            }
            if (fullHp) {
                vars[VP_RECHARGE_ENERGY] += energy
            } else {
                val xp = cell.buildXp.coerceAtMost(RECHARGE_XP_CAP - vars[VP_RECHARGE_XP])
                vars[VP_RECHARGE_XP] += xp
                statAdvance("stat.runecrafting", xp.toDouble())
            }
            earnEnergy(energy, energy)
            mes("You recharge the barrier.")
        }
    }

    private fun ProtectedAccess.repairTile(srcLoc: BoundLocInfo) {
        val tile = game.tileAt(srcLoc.coords) ?: return
        if (!game.isBroken(tile)) {
            mes("The cell tile is not broken.")
            return
        }
        if (inv.count(OBJ_FRAGMENT) < FRAGS_PER_REPAIR) {
            mes("You need $FRAGS_PER_REPAIR guardian fragments to repair the cell tile.")
            return
        }
        invDel(inv, OBJ_FRAGMENT, FRAGS_PER_REPAIR)
        anim(BUILD_ANIM)
        game.repair(tile)
        earnEnergy(25, 25)
        mes("You repair the cell tile.")
    }

    private fun ProtectedAccess.assembleGuardian(elemental: Boolean) {
        if (inv.count("obj.chisel") == 0) {
            mes("You need a chisel to assemble a guardian.")
            return
        }
        val cell = heldCell() ?: run {
            mes("You need a charged cell to assemble a guardian.")
            return
        }
        if (game.guardianCount() >= MAX_GUARDIANS) {
            mes("There are already too many active guardians.")
            return
        }
        invDel(inv, cell.obj, 1)
        anim(GUARDIAN_ANIM)
        game.spawnRiftGuardian(player.coords, elemental, cell.tier)
        val energy = cell.tier.guardianEnergy
        if (elemental) earnEnergy(energy, 0) else earnEnergy(0, energy)
        if (game.isBattle()) {
            statAdvance("stat.runecrafting", cell.tier.guardianXp.toDouble())
        }
        statAdvance("stat.crafting", 80.0)
        mes("You assemble a rift guardian to defend the Great Guardian.")
    }

    private fun ProtectedAccess.searchRewards(searches: Int) {
        if (actionDelay > worldClock.cycle) return
        repeat(searches) {
            if (!rollRewardSearch()) return
            actionDelay = worldClock.cycle + SEARCH_TICKS
        }
    }

    private fun ProtectedAccess.rollRewardSearch(): Boolean {
        val usePoints = vars[VP_ELEMENTAL_POINTS] > 0 && vars[VP_CATALYTIC_POINTS] > 0
        if (!usePoints && inv.count("obj.abyssal_pearl") < PEARL_SEARCH_COST) {
            mes("You need one elemental and one catalytic reward point, or 25 abyssal pearls.")
            return false
        }
        val rewards = rollRewards()
        val received = player.invTransaction(inv) {
            val inventory = select(inv)
            if (!usePoints) {
                delete(inventory, "obj.abyssal_pearl".asRSCM(RSCMType.OBJ), PEARL_SEARCH_COST)
            }
            for (reward in rewards) {
                add(inventory, reward.obj.asRSCM(RSCMType.OBJ), reward.count)
            }
        }
        if (received.failure) {
            mes("You don't have enough inventory space.")
            return false
        }
        if (usePoints) {
            vars[VP_ELEMENTAL_POINTS] -= 1
            vars[VP_CATALYTIC_POINTS] -= 1
        }
        vars[VP_SEARCHES] += 1
        anim(TAKE_ANIM)
        for (reward in rewards) {
            commitReward(reward)
        }
        return true
    }

    private fun ProtectedAccess.rollRewards(): List<Reward> {
        val rewards = mutableListOf<Reward>()
        val rows = GotrRewardTableRow.all()
        for (row in rows) {
            val independent = row.section == "rare" || row.section == "tertiary"
            if (independent && rollsUnique(row)) {
                rewards += Reward(row.obj, 1, row.uniqueVarbit)
            }
        }
        val ownsAllPouches = ownsAllPouches()
        val mainRows = rows.filter { it.section == "main" && (it.key != "pouch" || !ownsAllPouches) }
        pickWeighted(mainRows)?.let { row ->
            when (row.key) {
                "pouch" -> pouchReward()?.let { rewards += it }
                "talisman" -> talismanReward(rows)?.let { rewards += it }
                else -> rewards += Reward(row.obj, scaledQuantity(row), row.uniqueVarbit)
            }
        }
        return rewards
    }

    private fun ProtectedAccess.rollsUnique(row: GotrRewardTableRow): Boolean {
        if (Random.nextInt(row.denominator) != 0) {
            return false
        }
        return row.uniqueVarbit.isEmpty() || vars[row.uniqueVarbit] == 0
    }

    private fun ProtectedAccess.talismanReward(rows: List<GotrRewardTableRow>): Reward? {
        val table = rows.filter { it.section == "talisman" }
        val row = pickWeighted(table) ?: return null
        if (row.obj.isEmpty()) {
            return null
        }
        val obj = if (row.quest.isNotEmpty() && !QuestRequirements.hasCompleted(player, row.quest)) {
            row.fallbackObj
        } else {
            row.obj
        }
        return Reward(obj, 1)
    }

    private fun pickWeighted(rows: List<GotrRewardTableRow>): GotrRewardTableRow? {
        val total = rows.sumOf { it.weight }
        if (total <= 0) {
            return null
        }
        var roll = Random.nextInt(total)
        for (row in rows) {
            roll -= row.weight
            if (roll < 0) {
                return row
            }
        }
        return null
    }

    private fun ProtectedAccess.pouchReward(): Reward? {
        val tier = EssencePouch.Tier.entries.firstOrNull { tier ->
            tier != EssencePouch.Tier.Colossal && !ownsItem(tier.intactItem)
        } ?: return null
        return Reward(tier.intactItem, 1, pouchTier = tier, pouchEssence = POUCH_ESSENCE[tier.ordinal])
    }

    private fun ProtectedAccess.ownsAllPouches(): Boolean {
        if (ownsItem("obj.rcu_pouch_colossal") || ownsItem("obj.rcu_pouch_colossal_degrade")) {
            return true
        }
        return EssencePouch.Tier.entries
            .filter { it != EssencePouch.Tier.Colossal }
            .all { tier -> tier.items.any { ownsItem(it) } }
    }

    private fun ProtectedAccess.ownsItem(obj: String): Boolean =
        inv.count(obj) > 0 || player.worn.count(obj) > 0 || bank.count(obj) > 0

    private fun ProtectedAccess.scaledQuantity(row: GotrRewardTableRow): Int {
        val rolled = row.qtyMin + Random.nextInt(row.qtyMax - row.qtyMin + 1)
        val required = row.reqLevel
        val level = player.statBase("stat.runecrafting")
        if (required <= 0 || level >= required) {
            return rolled
        }
        // Jagex lowers rune quantities below the craft level; exact curve is
        // unpublished, scaled proportionally with the published range minimum.
        return max(row.qtyMin, rolled * level / required)
    }

    private fun ProtectedAccess.commitReward(reward: Reward) {
        if (reward.uniqueVarbit.isNotEmpty()) {
            vars[reward.uniqueVarbit] = 1
        }
        if (reward.obj == PET_OBJ) {
            vars[PET_UNLOCKED_VB] = 1
        }
        reward.pouchTier?.let { tier ->
            EssencePouch.setStoredEssenceType(player, tier, EssencePouch.PURE_ESSENCE)
            EssencePouch.depositUpTo(player, tier, reward.pouchEssence)
        }
        val name = ServerCacheManager.getItem(reward.obj.asRSCM(RSCMType.OBJ))?.name ?: reward.obj
        mes("You rummage through the Rewards Guardian's offerings... and find " +
            "${reward.count} $name!")
    }

    private fun ProtectedAccess.checkRewards() {
        val elemental = vars[VP_ELEMENTAL_POINTS]
        val catalytic = vars[VP_CATALYTIC_POINTS]
        val searches = vars[VP_SEARCHES]
        mes("You have $elemental elemental and $catalytic catalytic points remaining. " +
            "You have searched the Rewards Guardian $searches times.")
    }

    private fun ProtectedAccess.openIntricatePouch() {
        if (inv.count(INTRICATE_POUCH) == 0) return
        val rewards = mutableListOf<Reward>()
        val rows = GotrRewardTableRow.all()
        for (row in rows) {
            if (row.section == "pouch_preroll" && rollsUnique(row)) {
                rewards += Reward(row.obj, 1, row.uniqueVarbit)
            }
            if (row.section == "pouch_tertiary" && Random.nextInt(row.denominator) == 0) {
                rewards += Reward(row.obj, 1)
            }
        }
        pickWeighted(rows.filter { it.section == "pouch_main" })?.let { row ->
            rewards += Reward(row.obj, row.qtyMin + Random.nextInt(row.qtyMax - row.qtyMin + 1))
        }
        val result = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, INTRICATE_POUCH.asRSCM(RSCMType.OBJ), 1)
            for (reward in rewards) {
                add(inventory, reward.obj.asRSCM(RSCMType.OBJ), reward.count)
            }
        }
        if (result.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        for (reward in rewards) {
            if (reward.uniqueVarbit.isNotEmpty()) {
                vars[reward.uniqueVarbit] = 1
            }
        }
        mes("You open the intricate pouch.")
    }

    private data class Reward(
        val obj: String,
        val count: Int,
        val uniqueVarbit: String = "",
        val pouchTier: EssencePouch.Tier? = null,
        val pouchEssence: Int = 0,
    )

    private fun ProtectedAccess.openShop() {
        openOmnishop("dbrow.gotr_reward_shop_all")
    }

    private fun ProtectedAccess.heldCell(): HeldCell? {
        for (i in 4 downTo 1) {
            val obj = "obj.gotr_cell_tier$i"
            if (inv.count(obj) > 0) return HeldCell(obj, CellTier.of(i))
        }
        return null
    }

    private fun greatGuardian(): Npc? =
        npcRepo.findAll(GUARDIAN_TILE).firstOrNull { it.visType.id == GREAT_GUARDIAN_ID }

    private suspend fun ProtectedAccess.gameTick() {
        if (!inTemple(player.coords)) {
            if (vars[VB_IS_PLAYING] == 1) {
                exitTemple()
                mes("You have left the Temple of the Eye.")
            }
            return
        }
        player.timerAt(GOTR_TIMER, worldClock.cycle, TICK_RATE)
        game.tick(this)
        val result = game.takeResult(player)
        if (result != null) {
            awardGameEnd(result)
        }
    }

    private fun ProtectedAccess.awardGameEnd(win: Boolean) {
        val elemental = vars[VB_ELEMENTAL_EARNED]
        val catalytic = vars[VB_CATALYTIC_EARNED]
        val total = elemental + catalytic
        if (total >= MIN_ENERGY_REWARD) {
            val xp = player.statBase("stat.runecrafting") * XP_PER_RC_LEVEL.toDouble()
            statAdvance("stat.runecrafting", xp)
            vars[VB_FINISHED_GAME] = 1
            vars[VB_COMPLETED] = 1
            val elementalPoints = points(elemental)
            val catalyticPoints = points(catalytic)
            vars[VP_ELEMENTAL_POINTS] = (vars[VP_ELEMENTAL_POINTS].toLong() + elementalPoints)
                .coerceAtMost(MAX_REWARD_POINTS.toLong()).toInt()
            vars[VP_CATALYTIC_POINTS] = (vars[VP_CATALYTIC_POINTS].toLong() + catalyticPoints)
                .coerceAtMost(MAX_REWARD_POINTS.toLong()).toInt()
            val searches = minOf(vars[VP_ELEMENTAL_POINTS], vars[VP_CATALYTIC_POINTS])
            val searchText = if (searches == 1) "a reward search" else "$searches reward searches"
            if (win) {
                mes("The Great Guardian closes the rift! You earned $total energy and " +
                    "$searchText.")
            } else {
                mes("The rift overwhelms the Great Guardian. You earned $total energy and " +
                    "$searchText.")
            }
        } else {
            mes("The game ends. You gathered too little energy to earn a reward.")
        }
        vars[VB_ELEMENTAL_EARNED] = 0
        vars[VB_CATALYTIC_EARNED] = 0
    }

    private fun points(energy: Int): Int =
        energy / POINTS_PER_ENERGY +
            if (Random.nextInt(POINTS_PER_ENERGY) < energy % POINTS_PER_ENERGY) 1 else 0

    private fun inTemple(coords: CoordGrid): Boolean =
        coords.x in ARENA_MIN_X..ARENA_MAX_X &&
            coords.z in ARENA_SPLIT_Z + 1..ARENA_MAX_Z &&
            coords.level == 0

    private class HeldCell(val obj: String, val tier: CellTier)

    private enum class Rock(val loc: String, val product: String, val xp: Int) {
        PARTS("loc.gotr_essence_tier_1", "obj.gotr_guardian_fragment", 5),
        PARTS_B("loc.gotr_essence_tier_1b", "obj.gotr_guardian_fragment", 5),
        REMAINS("loc.gotr_essence_tier_1_large", "obj.gotr_guardian_fragment", 10),
        REMAINS_B("loc.gotr_essence_tier_1_large_b", "obj.gotr_guardian_fragment", 10),
        LARGE("loc.gotr_essence_tier_2", "obj.gotr_guardian_fragment", 20),
        HUGE("loc.gotr_essence_tier_3", "obj.gotr_guardian_essence", 25),
        FALLEN("loc.gotr_fallen_guardian", "obj.gotr_guardian_fragment", 5),
    }

    private enum class CellTier(
        val tier: Int,
        val tileLoc: String,
        val barrierLoc: String,
        val buildXp: Int,
        val guardianXp: Int,
        val maxHp: Int,
        val placeHp: Int,
        val heal: Int,
        val strengthenEnergy: Int,
        val rechargeEnergy: Int,
        val guardianEnergy: Int,
        val display: String,
        val elementalNpc: String,
        val catalyticNpc: String,
    ) {
        WEAK(1, "loc.gotr_cell_tile_tier1", "loc.gotr_barrier_tier1", 30, 30, 50, 17, 10, 0, 2, 6,
            "weak", "npc.gotr_guardian_air", "npc.gotr_guardian_mind"),
        MEDIUM(2, "loc.gotr_cell_tile_tier2", "loc.gotr_barrier_tier2", 100, 100, 100, 20, 25, 7,
            5, 15, "medium", "npc.gotr_guardian_water", "npc.gotr_guardian_chaos"),
        STRONG(3, "loc.gotr_cell_tile_tier3", "loc.gotr_barrier_tier3", 180, 180, 200, 24, 50, 13,
            9, 27, "strong", "npc.gotr_guardian_earth", "npc.gotr_guardian_death"),
        OVERCHARGED(4, "loc.gotr_cell_tile_tier4", "loc.gotr_barrier_tier4", 250, 250, 400, 30,
            100, 22, 15, 45, "overcharged", "npc.gotr_guardian_fire", "npc.gotr_guardian_blood");

        fun npc(elemental: Boolean) = if (elemental) elementalNpc else catalyticNpc

        companion object {
            fun of(tier: Int) = entries.first { it.tier == tier }
        }
    }

    private enum class Portal(
        val loc: String,
        val display: String,
        val elemental: Boolean,
        val cellTier: Int,
        val rcReq: Int,
        val runeObj: String,
        val talismanObj: String,
    ) {
        AIR("loc.gotr_portal_air", "Air", true, 1, 1, "obj.airrune",
            "obj.gotr_portal_talisman_air"),
        WATER("loc.gotr_portal_water", "Water", true, 2, 5, "obj.waterrune",
            "obj.gotr_portal_talisman_water"),
        EARTH("loc.gotr_portal_earth", "Earth", true, 3, 9, "obj.earthrune",
            "obj.gotr_portal_talisman_earth"),
        FIRE("loc.gotr_portal_fire", "Fire", true, 4, 14, "obj.firerune",
            "obj.gotr_portal_talisman_fire"),
        MIND("loc.gotr_portal_mind", "Mind", false, 1, 2, "obj.mindrune",
            "obj.gotr_portal_talisman_mind"),
        BODY("loc.gotr_portal_body", "Body", false, 1, 20, "obj.bodyrune",
            "obj.gotr_portal_talisman_body"),
        COSMIC("loc.gotr_portal_cosmic", "Cosmic", false, 2, 27, "obj.cosmicrune",
            "obj.gotr_portal_talisman_cosmic"),
        CHAOS("loc.gotr_portal_chaos", "Chaos", false, 2, 35, "obj.chaosrune",
            "obj.gotr_portal_talisman_chaos"),
        NATURE("loc.gotr_portal_nature", "Nature", false, 3, 44, "obj.naturerune",
            "obj.gotr_portal_talisman_nature"),
        LAW("loc.gotr_portal_law", "Law", false, 3, 54, "obj.lawrune",
            "obj.gotr_portal_talisman_law"),
        DEATH("loc.gotr_portal_death", "Death", false, 4, 65, "obj.deathrune",
            "obj.gotr_portal_talisman_death"),
        BLOOD("loc.gotr_portal_blood", "Blood", false, 4, 77, "obj.bloodrune",
            "obj.gotr_portal_talisman_blood");

        val stoneObj: String
            get() = if (elemental) OBJ_STONE_ELEMENTAL else OBJ_STONE_CATALYTIC
    }

    private data class Barrier(val tier: CellTier, var hp: Int, var loc: LocInfo? = null)

    private enum class Phase { IDLE, PREP, ACTIVE, OVER }

    private inner class GotrGame {
        private var phase = Phase.IDLE
        private var charge = 0
        private var prepTicks = 0
        private var portalSwitch = 0
        private var spawnTick = 0
        private var overTicks = 0
        private var lastTick = 0
        private var rotations = 0
        private var surged = false
        private var elementalPortal = Portal.AIR
        private var catalyticPortal = Portal.MIND
        private val players = mutableSetOf<Player>()
        private val barriers = mutableMapOf<CoordGrid, Barrier>()
        private val brokenTiles = mutableSetOf<CoordGrid>()
        private val abyssals = mutableListOf<Npc>()
        private val guardians = mutableListOf<Npc>()

        private val chargeTarget: Int
            get() = chargeTargetFor(players.size)

        fun join(player: Player) {
            players += player
            if (phase != Phase.PREP && phase != Phase.ACTIVE) {
                reset()
            }
        }

        fun leave(player: Player) {
            players -= player
            pendingResults.remove(player)
            wipeItems(player)
        }

        private fun wipeItems(player: Player) {
            for (obj in GAME_OBJECTS) {
                val count = player.inv.count(obj)
                if (count > 0) {
                    player.invDel(player.inv, obj, count, strict = false)
                }
            }
        }

        fun takeResult(player: Player): Boolean? = pendingResults.remove(player)

        fun isBattle() = phase == Phase.ACTIVE

        fun isActive(portal: Portal) = portal == elementalPortal || portal == catalyticPortal

        fun guardianCount() = guardians.size

        fun addCharge(access: ProtectedAccess, amount: Int) {
            charge += amount
            if (!surged && phase == Phase.ACTIVE && charge >= chargeTarget * SURGE_PCT / 100) {
                surge()
                access.mes(
                    "The temple rumbles as the rift glows more intensely! " +
                        "The barriers take the strain!"
                )
            }
            if (charge >= chargeTarget) {
                endRound(win = true)
            }
        }

        fun drain(amount: Int) {
            charge -= amount
            if (charge <= 0 && phase == Phase.ACTIVE) {
                charge = 0
                endRound(win = false)
            }
        }

        fun tileAt(coords: CoordGrid): CoordGrid? =
            CELL_TILES.firstOrNull { it == coords }

        fun hasBarrier(tile: CoordGrid) = barriers.containsKey(tile)

        fun isBroken(tile: CoordGrid) = tile in brokenTiles

        fun barrierTier(tile: CoordGrid) = barriers[tile]?.tier

        fun isFullHealth(tile: CoordGrid) = barriers[tile]?.let { it.hp >= it.tier.maxHp } == true

        fun placeBarrier(tile: CoordGrid, cell: CellTier, srcLoc: BoundLocInfo) {
            val spawned = locRepo.add(
                tile, CellTier.WEAK.barrierLoc, Int.MAX_VALUE, srcLoc.angle, LocShape.CentrepieceStraight
            )
            barriers[tile] = Barrier(CellTier.WEAK, cell.placeHp, spawned)
        }

        fun strengthen(tile: CoordGrid, srcLoc: BoundLocInfo) {
            val barrier = barriers[tile] ?: return
            val next = CellTier.of(barrier.tier.tier + 1)
            removeBarrierLoc(tile)
            val spawned = locRepo.add(
                tile, next.barrierLoc, Int.MAX_VALUE, srcLoc.angle, LocShape.CentrepieceStraight
            )
            barriers[tile] = Barrier(next, barrier.hp.coerceAtMost(next.maxHp), spawned)
        }

        fun removeBarrierLoc(tile: CoordGrid) {
            barriers[tile]?.loc?.let { locRepo.del(it, Int.MAX_VALUE) }
            barriers[tile]?.loc = null
        }

        fun recharge(tile: CoordGrid, heal: Int) {
            val barrier = barriers[tile] ?: return
            barrier.hp = (barrier.hp + heal).coerceAtMost(barrier.tier.maxHp)
        }

        fun repair(tile: CoordGrid) {
            brokenTiles.remove(tile)
        }

        private fun breakBarrier(tile: CoordGrid) {
            removeBarrierLoc(tile)
            barriers.remove(tile)
            brokenTiles += tile
        }

        fun spawnRiftGuardian(at: CoordGrid, elemental: Boolean, tier: CellTier): Npc {
            val npc = Npc(tier.npc(elemental), at.translate(0, -2))
            npc.respawns = false
            npcRepo.add(npc, Int.MAX_VALUE)
            npc.anim(SPAWN_ANIM, 0, 0)
            guardians += npc
            return npc
        }

        fun tick(access: ProtectedAccess) {
            if (worldClock.cycle - lastTick < TICK_RATE) return
            lastTick = worldClock.cycle
            val player = access.player
            when (phase) {
                Phase.PREP -> {
                    if (++prepTicks * TICK_RATE >= PREP_TICKS) {
                        phase = Phase.ACTIVE
                        greatGuardian()?.anim(WAKEUP_ANIM, 0, 0)
                        access.mes("Abyssal creatures begin pouring through the rift!")
                    }
                }
                Phase.ACTIVE -> {
                    if (++portalSwitch * TICK_RATE >= PORTAL_ROTATION) {
                        portalSwitch = 0
                        rotatePortals()
                        access.mes(
                            "The portal guardians shift: ${elementalPortal.display} and " +
                                "${catalyticPortal.display} are now active."
                        )
                    }
                    if (++spawnTick * TICK_RATE >= SPAWN_INTERVAL) {
                        spawnTick = 0
                        spawnWave()
                    }
                    for (abyssal in abyssals.toList()) {
                        if (abyssal.hitpoints <= 0) {
                            abyssal.anim(abyssalDeathAnim(abyssal), 0, 0)
                            abyssals -= abyssal
                            npcRepo.del(abyssal, Int.MAX_VALUE)
                            continue
                        }
                        val blocked = nearestBarrier(abyssal.coords)
                        if (blocked != null) {
                            val barrier = barriers.getValue(blocked)
                            abyssal.anim(abyssalAttackAnim(abyssal, blocked), 0, 0)
                            barrier.hp -= ABYSSAL_DPS
                            if (barrier.hp <= 0) {
                                breakBarrier(blocked)
                                access.mes("A barrier has been destroyed!")
                            }
                            continue
                        }
                        if (chebyshev(abyssal.coords, GUARDIAN_TILE) <= GUARDIAN_REACH) {
                            abyssal.anim(abyssalAttackAnim(abyssal, GUARDIAN_TILE), 0, 0)
                            greatGuardian()?.anim(SIPHON_ANIM, 0, 0)
                            drain(ABYSSAL_DRAIN)
                        } else {
                            abyssal.walk(GUARDIAN_TILE)
                        }
                    }
                    for (guardian in guardians.toList()) {
                        if (guardian.hitpoints <= 0) {
                            guardians -= guardian
                            continue
                        }
                        val target = abyssals.minByOrNull { chebyshev(it.coords, guardian.coords) }
                        if (target == null) continue
                        if (chebyshev(guardian.coords, target.coords) <= GUARDIAN_FIGHT_RANGE) {
                            target.hitpoints -= GUARDIAN_DPS
                        } else {
                            guardian.walk(target.coords)
                        }
                    }
                    if (charge == 0 && phase == Phase.ACTIVE) {
                        endRound(win = false)
                    }
                }
                Phase.OVER -> {
                    if (++overTicks * TICK_RATE >= OVER_RESTART) {
                        overTicks = 0
                        reset()
                    }
                }
                Phase.IDLE -> {}
            }
        }

        private fun rotatePortals() {
            if (++rotations <= MEDIUM_GUARANTEE_ROTATIONS) {
                elementalPortal = Portal.WATER
                catalyticPortal = MEDIUM_CATALYTICS.random()
            } else {
                elementalPortal = ELEMENTALS.random()
                catalyticPortal = CATALYTICS.random()
            }
        }

        private fun surge() {
            surged = true
            for ((tile, barrier) in barriers.entries.toList()) {
                barrier.hp -= SURGE_DAMAGE
                if (barrier.hp <= 0) {
                    breakBarrier(tile)
                }
            }
        }

        private fun spawnWave() {
            if (abyssals.size >= MAX_ABYSSALS) return
            val type = if (surged) ABYSSAL_TYPES_SURGED.random() else ABYSSAL_TYPES.random()
            val npc = Npc(type, RIFT_SPAWN.translate(Random.nextInt(-2, 3), Random.nextInt(-2, 3)))
            npc.respawns = false
            npcRepo.add(npc, Int.MAX_VALUE)
            abyssals += npc
        }

        private fun nearestBarrier(coords: CoordGrid): CoordGrid? =
            barriers.keys.minByOrNull { chebyshev(it, coords) }
                ?.takeIf { chebyshev(it, coords) <= BARRIER_BLOCK_RANGE }

        private val pendingResults = mutableMapOf<Player, Boolean>()

        private fun endRound(win: Boolean) {
            if (phase == Phase.OVER) return
            phase = Phase.OVER
            for (participant in players) {
                pendingResults[participant] = win
            }
            val guardian = greatGuardian()
            guardian?.anim(if (win) SURGE_ANIM else SLEEP_ANIM, 0, 0)
            for (npc in abyssals + guardians) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
            abyssals.clear()
            guardians.clear()
            clearBarriers()
        }

        private fun clearBarriers() {
            for (tile in barriers.keys.toList()) {
                removeBarrierLoc(tile)
            }
            barriers.clear()
            brokenTiles.clear()
        }

        private fun abyssalAttackAnim(npc: Npc, target: CoordGrid): String {
            val name = ABYSSAL_ANIMS[npc.visType.id] ?: return "seq.gotr_abyssal_walker_attack"
            return if (name.second != null && chebyshev(npc.coords, target) > 1) {
                name.second!!
            } else {
                name.first
            }
        }

        private fun abyssalDeathAnim(npc: Npc): String =
            ABYSSAL_DEATHS[npc.visType.id] ?: "seq.gotr_abyssal_walker_death"

        fun reset() {
            for (npc in abyssals + guardians) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
            abyssals.clear()
            guardians.clear()
            clearBarriers()
            for (participant in players) {
                VarPlayerIntMapSetter.set(participant, VP_RECHARGE_XP, 0)
                VarPlayerIntMapSetter.set(participant, VP_RECHARGE_ENERGY, 0)
                wipeItems(participant)
            }
            charge = chargeTarget * CHARGE_START_PCT / 100
            prepTicks = 0
            portalSwitch = 0
            spawnTick = 0
            overTicks = 0
            lastTick = 0
            rotations = 0
            surged = false
            rotatePortals()
            phase = Phase.PREP
        }
    }

    companion object {
        internal fun chargeTargetFor(playerCount: Int): Int {
            val count = playerCount.coerceAtLeast(1)
            return minOf(count, FULL_SCALING_PLAYERS) * CHARGE_PER_PLAYER +
                (count - FULL_SCALING_PLAYERS).coerceAtLeast(0) * CHARGE_PER_EXTRA_PLAYER
        }

        private val BARRIERS = listOf("loc.gotr_barrier", "loc.gotr_barrier_closed")
        private const val WORKBENCH = "loc.gotr_workbench"
        private const val CELL_CRATE = "loc.gotr_uncharged_cells"
        private const val CELL_TILE_INACTIVE = "loc.gotr_cell_tile_inactive"
        private const val CELL_TILE_BROKEN = "loc.gotr_cell_tile_broken"
        private const val ESSENCE_PILE_ELEMENTAL = "loc.gotr_guardian_spawn_east"
        private const val ESSENCE_PILE_CATALYTIC = "loc.gotr_guardian_spawn_west"
        private const val WEAK_CELL_CRATE = "loc.gotr_weak_cells"
        private const val CHISEL_SPAWN = "loc.chisel"
        private const val REWARD_RIFT = "loc.gotr_reward_rift"
        private const val GREAT_GUARDIAN = "npc.gotr_great_guardian"
        private const val REWARD_TRADER = "npc.gotr_reward_trader"

        private const val OBJ_FRAGMENT = "obj.gotr_guardian_fragment"
        private const val OBJ_ESSENCE = "obj.gotr_guardian_essence"
        private const val OBJ_CELL_UNCHARGED = "obj.gotr_cell_uncharged"
        private const val OBJ_STONE_ELEMENTAL = "obj.gotr_guardian_stone_elemental"
        private const val OBJ_STONE_CATALYTIC = "obj.gotr_guardian_stone_catalytic"

        private const val VB_IS_PLAYING = "varbit.gotr_is_playing"
        private const val VB_ELEMENTAL_EARNED = "varbit.gotr_elemental_earned_this_game"
        private const val VB_CATALYTIC_EARNED = "varbit.gotr_catalytic_earned_this_game"
        private const val VB_FINISHED_GAME = "varbit.gotr_player_has_finished_game"
        private const val VB_COMPLETED = "varbit.collection_minigames_gotr_completed"
        private const val VP_ELEMENTAL_POINTS = "varp.gotr_elemental_reward_points"
        private const val VP_CATALYTIC_POINTS = "varp.gotr_catalytic_reward_points"
        private const val VP_RECHARGE_XP = "varp.gotr_recharge_xp"
        private const val VP_RECHARGE_ENERGY = "varp.gotr_recharge_energy"

        private const val HUD_OVERLAY = "interface.gotr_overlay"
        private const val HUD_TARGET = "component.toplevel_osrs_stretch:overlay_hud"

        private const val GOTR_TIMER = "timer.gotr_tick"

        private const val CRAFT_ANIM = "seq.gotr_chisel_essence"
        private const val GUARDIAN_ANIM = "seq.gotr_chisel_guardian"
        private const val IMBUE_ANIM = "seq.human_pickuptable"
        private const val BUILD_ANIM = "seq.human_pickuptable"
        private const val TAKE_ANIM = "seq.human_pickuptable"
        private const val SIPHON_ANIM = "seq.portal_guardian_siphon"
        private const val SURGE_ANIM = "seq.portal_guardian_surge"
        private const val WAKEUP_ANIM = "seq.portal_guardian_wakeup"
        private const val SLEEP_ANIM = "seq.portal_guardian_sleep"
        private const val SPAWN_ANIM = "seq.portal_guardian_spawn"

        private val PICKAXES = listOf(
            "bronze_pickaxe", "iron_pickaxe", "steel_pickaxe", "mithril_pickaxe",
            "adamant_pickaxe", "rune_pickaxe", "dragon_pickaxe",
        )

        private val GAME_OBJECTS = listOf(
            OBJ_FRAGMENT, OBJ_ESSENCE, OBJ_CELL_UNCHARGED,
            OBJ_STONE_ELEMENTAL, OBJ_STONE_CATALYTIC,
            "obj.gotr_cell_tier1", "obj.gotr_cell_tier2", "obj.gotr_cell_tier3", "obj.gotr_cell_tier4",
        ) + Portal.entries.map { it.talismanObj }

        private val ELEMENTALS = listOf(Portal.AIR, Portal.WATER, Portal.EARTH, Portal.FIRE)
        private val CATALYTICS = listOf(
            Portal.MIND, Portal.BODY, Portal.COSMIC, Portal.CHAOS,
            Portal.NATURE, Portal.LAW, Portal.DEATH, Portal.BLOOD,
        )
        private val MEDIUM_CATALYTICS = listOf(Portal.COSMIC, Portal.CHAOS)

        private val ABYSSAL_TYPES = listOf(
            "npc.gotr_abyssal_leech", "npc.gotr_abyssal_leech", "npc.gotr_abyssal_leech",
            "npc.gotr_abyssal_pyramid", "npc.gotr_abyssal_pyramid", "npc.gotr_abyssal_walker",
        )
        private val ABYSSAL_TYPES_SURGED = listOf(
            "npc.gotr_abyssal_leech",
            "npc.gotr_abyssal_pyramid", "npc.gotr_abyssal_pyramid",
            "npc.gotr_abyssal_walker", "npc.gotr_abyssal_walker", "npc.gotr_abyssal_walker",
        )

        private const val ABYSSAL_LEECH_ID = 11407
        private const val ABYSSAL_WALKER_ID = 11406
        private const val ABYSSAL_PYRAMID_ID = 11405
        private const val GREAT_GUARDIAN_ID = 11403

        private val ABYSSAL_ANIMS = mapOf(
            ABYSSAL_LEECH_ID to ("seq.gotr_abyssal_leech_attack" to null),
            ABYSSAL_WALKER_ID to ("seq.gotr_abyssal_walker_attack" to null),
            ABYSSAL_PYRAMID_ID to
                ("seq.gotr_abyssal_pyramid_attack" to "seq.gotr_abyssal_pyramid_ranged_attack"),
        )

        private val ABYSSAL_DEATHS = mapOf(
            ABYSSAL_LEECH_ID to "seq.gotr_abyssal_leech_death",
            ABYSSAL_WALKER_ID to "seq.gotr_abyssal_walker_death",
            ABYSSAL_PYRAMID_ID to "seq.gotr_abyssal_pyramid_death",
        )

        private val ARENA_SPAWN = CoordGrid(0, 56, 148, 31, 12)
        private val LOBBY_SPAWN = CoordGrid(0, 56, 148, 31, 8)
        private const val ARENA_SPLIT_Z = 9483
        private val GUARDIAN_TILE = CoordGrid(0, 56, 148, 28, 24)
        private val RIFT_SPAWN = CoordGrid(0, 56, 148, 31, 42)

        private val CELL_TILES = listOf(
            CoordGrid(0, 56, 148, 24, 31),
            CoordGrid(0, 56, 148, 38, 31),
            CoordGrid(0, 56, 148, 25, 35),
            CoordGrid(0, 56, 148, 37, 35),
            CoordGrid(0, 56, 148, 27, 37),
            CoordGrid(0, 56, 148, 35, 37),
            CoordGrid(0, 56, 148, 31, 38),
        )

        private const val ARENA_MIN_X = 3598
        private const val ARENA_MAX_X = 3632
        private const val ARENA_MAX_Z = 9525
        private const val MIN_RC_LEVEL = 27
        private const val TICK_RATE = 4
        private const val PREP_TICKS = 200
        private const val PORTAL_ROTATION = 50
        private const val MEDIUM_GUARANTEE_ROTATIONS = 2
        private const val SPAWN_INTERVAL = 8
        private const val OVER_RESTART = 40
        private const val CHARGE_PER_PLAYER = 500
        private const val CHARGE_PER_EXTRA_PLAYER = 400
        private const val FULL_SCALING_PLAYERS = 20
        private const val CHARGE_START_PCT = 10
        private const val SURGE_PCT = 60
        private const val SURGE_DAMAGE = 50
        private const val ENERGY_PER_STONE = 2
        private const val BUILD_ENERGY = 2
        private const val ENERGY_CAP_PER_TYPE = 1000
        private const val ENERGY_CAP_TOTAL = 1200
        private const val FRAGS_PER_REPAIR = 12
        private const val MAX_CELLS = 10
        private const val BARRIER_BLOCK_RANGE = 12
        private const val ABYSSAL_DPS = 5
        private const val ABYSSAL_DRAIN = 2
        private const val GUARDIAN_REACH = 8
        private const val GUARDIAN_DPS = 4
        private const val GUARDIAN_FIGHT_RANGE = 3
        private const val MAX_ABYSSALS = 10
        private const val MAX_GUARDIANS = 10
        private const val CELL_XP = 10
        private const val TALISMAN_RATE = 250
        private const val MIN_ENERGY_REWARD = 300
        private const val XP_PER_RC_LEVEL = 45
        private const val POINTS_PER_ENERGY = 100
        private const val PEARL_SEARCH_COST = 25
        private const val SEARCH_TICKS = 3
        private const val MAX_REWARD_POINTS = 65_535
        private const val INTRICATE_POUCH = "obj.gotr_intricate_pouch"
        private const val PET_OBJ = "obj.abyssalpet"
        private const val PET_UNLOCKED_VB = "varbit.skillpet_runecrafting_gotr_unlocked"
        private const val VP_SEARCHES = "varp.gotr_searches"
        private val POUCH_ESSENCE = intArrayOf(2, 4, 6, 8)
        private const val RECHARGE_XP_CAP = 2500
        private const val RECHARGE_ENERGY_CAP = 450
        private const val MINE_DELAY = 3

        private fun chebyshev(a: CoordGrid, b: CoordGrid): Int =
            max(abs(a.x - b.x), abs(a.z - b.z))
    }
}
