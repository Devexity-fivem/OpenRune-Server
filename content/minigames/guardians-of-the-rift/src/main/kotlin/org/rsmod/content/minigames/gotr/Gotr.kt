package org.rsmod.content.minigames.gotr

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.content.skills.mining.configs.MiningParams
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeAnim
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeWallAnim
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
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
        onOpLoc1(WORKBENCH) { craftEssence() }
        onOpLoc1(CELL_CRATE) { takeCells(10) }
        onOpLoc2(CELL_CRATE) { takeCells(1) }
        onOpLoc1(WEAK_CELL_CRATE) { takeWeakCell() }
        onOpLoc2(WEAK_CELL_CRATE) { takeChisel() }
        onOpLoc1(CHISEL_SPAWN) { takeChisel() }
        for (portal in Portal.entries) {
            onOpLoc1(portal.loc) { enterPortal(portal) }
        }
        onOpNpc1(GREAT_GUARDIAN) { powerUp() }
        onOpLoc1(CELL_TILE_INACTIVE) { placeBarrier() }
        onOpLoc1(CELL_TILE_BROKEN) { repairTile() }
        for (tier in CellTier.entries) {
            onOpLoc1(tier.tileLoc) { placeBarrier() }
            for (cell in 1..4) {
                onOpLocU(tier.barrierLoc, "obj.gotr_cell_tier$cell") { useCellOnBarrier(tier, cell) }
            }
        }
        onOpLoc1(ESSENCE_PILE_ELEMENTAL) { assembleGuardian(true) }
        onOpLoc1(ESSENCE_PILE_CATALYTIC) { assembleGuardian(false) }
        onOpLoc1(REWARD_RIFT) { searchRewards() }
        onOpNpc3(REWARD_TRADER) { openShop() }
        onPlayerLogin { onLogin(player) }
        onPlayerTimer(GOTR_TIMER) { gameTick() }
    }

    private fun onLogin(player: Player) {
        protectedAccess.launch(player) {
            if (inTemple(player.coords)) {
                ifOpenOverlay(HUD_OVERLAY, HUD_TARGET)
                if (vars[VB_IS_PLAYING] == 1) {
                    player.timerAt(GOTR_TIMER, worldClock.cycle, TICK_RATE)
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
        game.join()
    }

    private fun ProtectedAccess.exitTemple() {
        player.ifCloseOverlay(HUD_OVERLAY, eventBus)
        vars[VB_IS_PLAYING] = 0
        vars[VB_ELEMENTAL_EARNED] = 0
        vars[VB_CATALYTIC_EARNED] = 0
        for (obj in GAME_OBJECTS) {
            invDel(inv, obj, inv.count(obj), strict = false)
        }
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

    private fun ProtectedAccess.craftEssence() {
        val fragments = inv.count(OBJ_FRAGMENT)
        if (fragments == 0) {
            mes("You need guardian fragments to craft essence here.")
            return
        }
        if (inv.freeSpace() < 1 && inv.count(OBJ_ESSENCE) == 0) {
            mes("Your inventory is too full to hold any more.")
            return
        }
        anim(CRAFT_ANIM)
        invDel(inv, OBJ_FRAGMENT, fragments)
        invAdd(inv, OBJ_ESSENCE, fragments)
        statAdvance("stat.crafting", (fragments * CRAFT_XP).toDouble())
        mes("You craft $fragments guardian essence.")
    }

    private fun ProtectedAccess.takeWeakCell() {
        anim(TAKE_ANIM)
        invAdd(inv, "obj.gotr_cell_tier1", 1)
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
        if (essence == 0 && cells == 0 && held == null) {
            mes("You have no guardian essence or uncharged cells to imbue.")
            return
        }
        anim(IMBUE_ANIM)
        if (held != null && portal.cellTier > held.tier.tier) {
            invDel(inv, held.obj, 1)
            invAdd(inv, "obj.gotr_cell_tier${portal.cellTier}", 1)
            mes("The ${portal.display} altar overcharges your cell.")
        } else if (held == null && cells > 0) {
            invDel(inv, OBJ_CELL_UNCHARGED, 1)
            invAdd(inv, "obj.gotr_cell_tier${portal.cellTier}", 1)
        }
        if (essence > 0) {
            invDel(inv, OBJ_ESSENCE, essence)
            invAdd(inv, portal.stoneObj, essence)
            invAdd(inv, portal.runeObj, essence)
            statAdvance("stat.runecrafting", essence * ESSENCE_XP.toDouble())
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
        vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + elemental * ENERGY_PER_STONE)
            .coerceAtMost(ENERGY_CAP_PER_TYPE)
        vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + catalytic * ENERGY_PER_STONE)
            .coerceAtMost(ENERGY_CAP_PER_TYPE)
        game.addCharge(earned)
        statAdvance("stat.runecrafting", total * 5.0)
        player.runEnergy = (player.runEnergy + total * 100).coerceAtMost(10_000)
        if (vars[VB_ELEMENTAL_EARNED] >= ENERGY_CAP_PER_TYPE &&
            vars[VB_CATALYTIC_EARNED] >= ENERGY_CAP_PER_TYPE) {
            mes("You are fully attuned with runic energy and cannot earn anymore.")
        } else {
            mes("The Great Guardian absorbs your guardian stones.")
        }
    }

    private fun ProtectedAccess.placeBarrier() {
        val cell = heldCell() ?: run {
            mes("You need a charged cell to place a barrier.")
            return
        }
        val tile = game.tileAt(player.coords) ?: run {
            mes("There is no cell tile here.")
            return
        }
        if (game.hasBarrier(tile)) {
            mes("A barrier already occupies this cell tile.")
            return
        }
        invDel(inv, cell.obj, 1)
        anim(BUILD_ANIM)
        game.placeBarrier(tile, cell.tier)
        val energy = cell.tier.buildEnergy
        vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + energy).coerceAtMost(ENERGY_CAP_PER_TYPE)
        vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + energy).coerceAtMost(ENERGY_CAP_PER_TYPE)
        if (game.isBattle()) {
            statAdvance("stat.runecrafting", cell.tier.buildXp.toDouble())
        }
        mes("You erect a ${cell.tier.display} barrier.")
    }

    private fun ProtectedAccess.useCellOnBarrier(existing: CellTier, cellTier: Int) {
        val tile = game.tileAt(player.coords) ?: return
        if (!game.hasBarrier(tile)) return
        if (cellTier > existing.tier) {
            invDel(inv, "obj.gotr_cell_tier$cellTier", 1)
            game.strengthen(tile, cellTier)
            anim(BUILD_ANIM)
            val energy = CellTier.of(cellTier).buildEnergy
            vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + energy)
                .coerceAtMost(ENERGY_CAP_PER_TYPE)
            vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + energy)
                .coerceAtMost(ENERGY_CAP_PER_TYPE)
            mes("You strengthen the barrier.")
        } else {
            if (game.rechargeXp >= RECHARGE_XP_CAP && game.rechargeEnergy >= RECHARGE_ENERGY_CAP) {
                mes("You have gained as much as you can from recharging barriers.")
                return
            }
            invDel(inv, "obj.gotr_cell_tier$cellTier", 1)
            game.recharge(tile, cellTier)
            anim(BUILD_ANIM)
            val tier = CellTier.of(cellTier)
            val xp = (tier.buildXp / 2).coerceAtMost(RECHARGE_XP_CAP - game.rechargeXp)
            val energy = tier.buildEnergy.coerceAtMost(RECHARGE_ENERGY_CAP - game.rechargeEnergy)
            game.rechargeXp += xp
            game.rechargeEnergy += energy
            statAdvance("stat.runecrafting", xp.toDouble())
            vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + energy / 2)
                .coerceAtMost(ENERGY_CAP_PER_TYPE)
            vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + energy / 2)
                .coerceAtMost(ENERGY_CAP_PER_TYPE)
            mes("You recharge the barrier.")
        }
    }

    private fun ProtectedAccess.repairTile() {
        if (inv.count(OBJ_FRAGMENT) < FRAGS_PER_REPAIR) {
            mes("You need $FRAGS_PER_REPAIR guardian fragments to repair the cell tile.")
            return
        }
        val tile = game.tileAt(player.coords) ?: return
        invDel(inv, OBJ_FRAGMENT, FRAGS_PER_REPAIR)
        anim(BUILD_ANIM)
        game.repair(tile)
        vars[VB_ELEMENTAL_EARNED] = (vars[VB_ELEMENTAL_EARNED] + 25).coerceAtMost(ENERGY_CAP_PER_TYPE)
        vars[VB_CATALYTIC_EARNED] = (vars[VB_CATALYTIC_EARNED] + 25).coerceAtMost(ENERGY_CAP_PER_TYPE)
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
        val vb = if (elemental) VB_ELEMENTAL_EARNED else VB_CATALYTIC_EARNED
        vars[vb] = (vars[vb] + energy).coerceAtMost(ENERGY_CAP_PER_TYPE)
        if (game.isBattle()) {
            statAdvance("stat.runecrafting", cell.tier.guardianXp.toDouble())
        }
        statAdvance("stat.crafting", 80.0)
        mes("You assemble a rift guardian to defend the Great Guardian.")
    }

    private fun ProtectedAccess.searchRewards() {
        if (game.pendingSearches <= 0) {
            if (inv.count("obj.abyssal_pearl") >= PEARL_SEARCH_COST) {
                invDel(inv, "obj.abyssal_pearl", PEARL_SEARCH_COST)
                game.pendingSearches++
                mes("You trade 25 abyssal pearls for an additional reward search.")
            } else {
                mes("Finish a game to earn a reward search, or trade 25 abyssal pearls.")
                return
            }
        }
        game.pendingSearches--
        anim(TAKE_ANIM)
        val pearls = MIN_PEARL_ROLL + Random.nextInt(MAX_PEARL_ROLL - MIN_PEARL_ROLL + 1)
        invAdd(inv, "obj.abyssal_pearl", pearls)
        mes("You rummage through the Rewards Guardian's offerings... and find $pearls abyssal pearls!")
    }

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
        val result = game.lastWin
        if (result != null) {
            game.lastWin = null
            awardGameEnd(result)
        }
    }

    private fun ProtectedAccess.awardGameEnd(win: Boolean) {
        val total = vars[VB_ELEMENTAL_EARNED] + vars[VB_CATALYTIC_EARNED]
        if (total >= MIN_ENERGY_REWARD) {
            val xp = player.statBase("stat.runecrafting") * XP_PER_RC_LEVEL.toDouble()
            statAdvance("stat.runecrafting", xp)
            vars[VB_FINISHED_GAME] = 1
            vars[VB_COMPLETED] = 1
            game.pendingSearches++
            if (win) {
                mes("The Great Guardian closes the rift! You earned $total energy " +
                    "and a reward search.")
            } else {
                mes("The rift overwhelms the Great Guardian. You earned $total energy " +
                    "and a reward search.")
            }
        } else {
            mes("The game ends. You gathered too little energy to earn a reward.")
        }
        vars[VB_ELEMENTAL_EARNED] = 0
        vars[VB_CATALYTIC_EARNED] = 0
    }

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
        val buildEnergy: Int,
        val guardianEnergy: Int,
        val display: String,
        val elementalNpc: String,
        val catalyticNpc: String,
    ) {
        WEAK(1, "loc.gotr_cell_tile_tier1", "loc.gotr_barrier_tier1", 30, 30, 17, 6, "weak",
            "npc.gotr_guardian_air", "npc.gotr_guardian_mind"),
        MEDIUM(2, "loc.gotr_cell_tile_tier2", "loc.gotr_barrier_tier2", 100, 100, 20, 15, "medium",
            "npc.gotr_guardian_water", "npc.gotr_guardian_chaos"),
        STRONG(3, "loc.gotr_cell_tile_tier3", "loc.gotr_barrier_tier3", 180, 180, 25, 27, "strong",
            "npc.gotr_guardian_earth", "npc.gotr_guardian_death"),
        OVERCHARGED(4, "loc.gotr_cell_tile_tier4", "loc.gotr_barrier_tier4", 250, 250, 30, 45,
            "overcharged", "npc.gotr_guardian_fire", "npc.gotr_guardian_blood");

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
    ) {
        AIR("loc.gotr_portal_air", "Air", true, 1, 1, "obj.airrune"),
        WATER("loc.gotr_portal_water", "Water", true, 2, 5, "obj.waterrune"),
        EARTH("loc.gotr_portal_earth", "Earth", true, 3, 9, "obj.earthrune"),
        FIRE("loc.gotr_portal_fire", "Fire", true, 4, 14, "obj.firerune"),
        MIND("loc.gotr_portal_mind", "Mind", false, 1, 2, "obj.mindrune"),
        BODY("loc.gotr_portal_body", "Body", false, 1, 20, "obj.bodyrune"),
        COSMIC("loc.gotr_portal_cosmic", "Cosmic", false, 2, 27, "obj.cosmicrune"),
        CHAOS("loc.gotr_portal_chaos", "Chaos", false, 2, 35, "obj.chaosrune"),
        NATURE("loc.gotr_portal_nature", "Nature", false, 3, 44, "obj.naturerune"),
        LAW("loc.gotr_portal_law", "Law", false, 3, 54, "obj.lawrune"),
        DEATH("loc.gotr_portal_death", "Death", false, 4, 65, "obj.deathrune"),
        BLOOD("loc.gotr_portal_blood", "Blood", false, 4, 77, "obj.bloodrune");

        val stoneObj: String
            get() = if (elemental) OBJ_STONE_ELEMENTAL else OBJ_STONE_CATALYTIC
    }

    private data class Barrier(val tier: CellTier, var hp: Int)

    private enum class Phase { IDLE, PREP, ACTIVE, OVER }

    private inner class GotrGame {
        private var phase = Phase.IDLE
        private var charge = 0
        private var prepTicks = 0
        private var portalSwitch = 0
        private var spawnTick = 0
        private var drainTick = 0
        private var overTicks = 0
        private var elementalPortal = Portal.AIR
        private var catalyticPortal = Portal.MIND
        var pendingSearches = 0
        var rechargeXp = 0
        var rechargeEnergy = 0
        private val barriers = mutableMapOf<CoordGrid, Barrier>()
        private val abyssals = mutableListOf<Npc>()
        private val guardians = mutableListOf<Npc>()

        fun join() {
            if (phase != Phase.PREP && phase != Phase.ACTIVE) {
                reset()
            }
        }

        fun isBattle() = phase == Phase.ACTIVE

        fun isActive(portal: Portal) = portal == elementalPortal || portal == catalyticPortal

        fun guardianCount() = guardians.size

        fun addCharge(amount: Int) {
            charge += amount
            if (charge >= CHARGE_TARGET) {
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

        fun tileAt(near: CoordGrid): CoordGrid? =
            CELL_TILES.firstOrNull { chebyshev(it, near) <= 2 }

        fun hasBarrier(tile: CoordGrid) = barriers.containsKey(tile)

        fun placeBarrier(tile: CoordGrid, tier: CellTier) {
            barriers[tile] = Barrier(tier, tier.tier * BARRIER_HP_PER_TIER)
        }

        fun strengthen(tile: CoordGrid, newTier: Int) {
            barriers[tile] = Barrier(CellTier.of(newTier), newTier * BARRIER_HP_PER_TIER)
        }

        fun recharge(tile: CoordGrid, cellTier: Int) {
            val barrier = barriers[tile] ?: return
            val max = barrier.tier.tier * BARRIER_HP_PER_TIER
            barrier.hp = (barrier.hp + cellTier * RECHARGE_PER_TIER).coerceAtMost(max)
        }

        fun repair(tile: CoordGrid) {
            barriers.remove(tile)
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
                        elementalPortal = ELEMENTALS.random()
                        catalyticPortal = CATALYTICS.random()
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
                                barriers.remove(blocked)
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
                    if (++drainTick * TICK_RATE >= DRAIN_INTERVAL) {
                        drainTick = 0
                        drain(PASSIVE_DRAIN)
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

        private fun spawnWave() {
            if (abyssals.size >= MAX_ABYSSALS) return
            val type = ABYSSAL_TYPES.random()
            val npc = Npc(type, RIFT_SPAWN.translate(Random.nextInt(-2, 3), Random.nextInt(-2, 3)))
            npc.respawns = false
            npcRepo.add(npc, Int.MAX_VALUE)
            abyssals += npc
        }

        private fun nearestBarrier(coords: CoordGrid): CoordGrid? =
            barriers.keys.minByOrNull { chebyshev(it, coords) }
                ?.takeIf { chebyshev(it, coords) <= BARRIER_BLOCK_RANGE }

        var lastWin: Boolean? = null

        private fun endRound(win: Boolean) {
            phase = Phase.OVER
            lastWin = win
            val guardian = greatGuardian()
            guardian?.anim(if (win) SURGE_ANIM else SLEEP_ANIM, 0, 0)
            for (npc in abyssals + guardians) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
            abyssals.clear()
            guardians.clear()
            barriers.clear()
            rechargeXp = 0
            rechargeEnergy = 0
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
            barriers.clear()
            charge = CHARGE_START
            prepTicks = 0
            portalSwitch = 0
            spawnTick = 0
            drainTick = 0
            overTicks = 0
            elementalPortal = ELEMENTALS.random()
            catalyticPortal = CATALYTICS.random()
            phase = Phase.PREP
        }
    }

    companion object {
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
        )

        private val ELEMENTALS = listOf(Portal.AIR, Portal.WATER, Portal.EARTH, Portal.FIRE)
        private val CATALYTICS = listOf(
            Portal.MIND, Portal.BODY, Portal.COSMIC, Portal.CHAOS,
            Portal.NATURE, Portal.LAW, Portal.DEATH, Portal.BLOOD,
        )

        private val ABYSSAL_TYPES = listOf(
            "npc.gotr_abyssal_leech", "npc.gotr_abyssal_leech",
            "npc.gotr_abyssal_walker", "npc.gotr_abyssal_pyramid",
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
        private const val SPAWN_INTERVAL = 8
        private const val DRAIN_INTERVAL = 8
        private const val PASSIVE_DRAIN = 2
        private const val OVER_RESTART = 40
        private const val CHARGE_START = 150
        private const val CHARGE_TARGET = 500
        private const val ENERGY_PER_STONE = 2
        private const val ENERGY_CAP_PER_TYPE = 1000
        private const val FRAGS_PER_REPAIR = 12
        private const val MAX_CELLS = 10
        private const val BARRIER_HP_PER_TIER = 50
        private const val RECHARGE_PER_TIER = 15
        private const val BARRIER_BLOCK_RANGE = 12
        private const val ABYSSAL_DPS = 5
        private const val ABYSSAL_DRAIN = 2
        private const val GUARDIAN_REACH = 8
        private const val GUARDIAN_DPS = 4
        private const val GUARDIAN_FIGHT_RANGE = 3
        private const val MAX_ABYSSALS = 10
        private const val MAX_GUARDIANS = 10
        private const val CRAFT_XP = 25
        private const val ESSENCE_XP = 10
        private const val MIN_ENERGY_REWARD = 300
        private const val XP_PER_RC_LEVEL = 45
        private const val PEARL_SEARCH_COST = 25
        private const val MIN_PEARL_ROLL = 14
        private const val MAX_PEARL_ROLL = 16
        private const val RECHARGE_XP_CAP = 2500
        private const val RECHARGE_ENERGY_CAP = 450
        private const val MINE_DELAY = 3

        private fun chebyshev(a: CoordGrid, b: CoordGrid): Int =
            max(abs(a.x - b.x), abs(a.z - b.z))
    }
}
