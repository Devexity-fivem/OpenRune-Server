package org.rsmod.content.minigames.tithefarm

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TitheFarm
@Inject
constructor(
    private val locRepo: LocRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldClock: MapClock,
    private val eventBus: EventBus,
) : PluginScript() {

    private val sessions = mutableMapOf<Long, TitheSession>()

    private fun Player.session() = sessions.getOrPut(userId) { TitheSession() }

    override fun ScriptContext.startup() {
        onOpLoc1(FARM_DOOR) { useDoor(it.loc) }
        onOpLoc1(SEED_TABLE) { searchSeedTable() }
        for (sack in SACKS) onOpLoc1(sack) { depositFruit(it.loc) }
        onOpNpc1(FARMER) { talkToGricoller(it.npc) }
        onOpNpc3(FARMER) { openOmnishop(SHOP_ROW) }

        for (seed in Seed.entries) {
            onOpLocU(EMPTY_PATCH, seed.seedObj) { plantSeed(seed, it.loc) }
            onOpLoc1(matureLoc(seed)) { harvestPlant(seed, it.loc) }
            for (stage in 1..3) {
                onOpLoc2(dryLoc(seed, stage)) { waterPlant(it.loc) }
                onOpLoc1(deadLoc(seed, stage)) { clearPlant(it.loc) }
                onOpLocU(dryLoc(seed, stage), FERTILISER) { fertilisePlant(it.loc) }
                onOpLocU(wetLoc(seed, stage), FERTILISER) { fertilisePlant(it.loc) }
                for (can in WATERING_CANS) {
                    onOpLocU(dryLoc(seed, stage), can) { waterPlant(it.loc) }
                }
            }
            onOpLoc1(deadLoc(seed, 4)) { clearPlant(it.loc) }
            onOpHeld1(seed.fruitObj) { eatFruit(seed) }
        }
        for (can in WATERING_CANS + EMPTY_CAN) {
            onOpLocU(WATER_BARREL, can) { refillCan(can) }
        }

        onPlayerTimer(TICK_TIMER) { tick() }
        onPlayerLogin { onLogin(player) }
    }

    private suspend fun ProtectedAccess.useDoor(loc: BoundLocInfo) {
        if (player.coords.x >= DOOR_X + 1) {
            leaveFarm()
            stopAction()
            teleport(loc.coords.translate(-1, 0))
            mes("You leave the Tithe Farm.")
        } else {
            if (player.farmingLvl < MIN_LEVEL) {
                mes("You need a Farming level of $MIN_LEVEL to enter the Tithe Farm.")
                return
            }
            val session = player.session()
            session.inside = true
            vars[VB_UNLOCKED] = 1
            if (inv.count(FERTILISER) == 0) {
                invAdd(inv, FERTILISER, 1)
            }
            ifOpenOverlay(HUD, HUD_TARGET)
            stopAction()
            teleport(loc.coords.translate(1, 0))
            armTick(player, session)
        }
    }

    private suspend fun ProtectedAccess.leaveFarm() {
        val session = player.session()
        for (plot in session.plots.values) {
            locRepo.change(plot.patch, EMPTY_PATCH, Int.MAX_VALUE)
        }
        session.plots.clear()
        session.inside = false
        vars[VB_TYPE] = 0
        for (seed in Seed.entries) {
            invDel(inv, seed.seedObj, inv.count(seed.seedObj))
            invDel(inv, seed.fruitObj, inv.count(seed.fruitObj))
        }
        player.ifCloseOverlay(HUD, eventBus)
    }

    private suspend fun ProtectedAccess.searchSeedTable() {
        openSkillMulti(
            SkillMultiConfig(
                actionType = SkillingActionType.TAKE,
                verb = "take",
                entries = Seed.entries.map { SkillMultiEntry(it.seedObj) },
                maxCountProvider = { _, _ -> SEED_TAKE_LIMIT },
            )
        ) { selection ->
            val seed = Seed.entries.firstOrNull { it.seedObj == selection.entry.internal } ?: return@openSkillMulti
            if (selection.amount > 0) {
                invAdd(inv, seed.seedObj, selection.amount)
            }
        }
    }

    private suspend fun ProtectedAccess.plantSeed(seed: Seed, loc: BoundLocInfo) {
        val session = player.session()
        if (!session.inside) {
            mes("Nothing interesting happens.")
            return
        }
        if (player.farmingLvl < seed.level) {
            mes("You need a Farming level of ${seed.level} to plant those seeds.")
            return
        }
        val curType = vars[VB_TYPE]
        if (curType != 0 && curType != seed.index) {
            mes("You can only grow one type of seed at a time.")
            return
        }
        val barehanded = vars[VB_ELITE_DIARY] != 0
        if (!barehanded && inv.count(DIBBER) == 0) {
            mes("You need a gardening dibber to plant seeds.")
            return
        }
        if (invDel(inv, seed.seedObj, 1).failure) {
            return
        }
        anim(if (barehanded) SEQ_PLANT_NODIB else SEQ_PLANT)
        delay(1)
        vars[VB_TYPE] = seed.index
        locRepo.change(loc, dryLoc(seed, 1), Int.MAX_VALUE)
        session.plots[loc.coords] =
            Plot(
                patch = loc,
                seed = seed,
                stage = 1,
                watered = false,
                fertilised = false,
                nextTick = worldClock.cycle + STAGE_TICKS,
            )
        armTick(player, session)
    }

    private suspend fun ProtectedAccess.waterPlant(loc: BoundLocInfo) {
        val plot = player.session().plots[loc.coords]
        if (plot == null || plot.dead || plot.watered) {
            mes("That plant does not need watering.")
            return
        }
        if (!consumeWaterDose()) {
            mes("You need a watering can with some water in it.")
            return
        }
        anim(SEQ_WATERING)
        spotanim(SPOT_WATERING)
        delay(1)
        plot.watered = true
        locRepo.change(plot.patch, wetLoc(plot.seed, plot.stage), Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.fertilisePlant(loc: BoundLocInfo) {
        val plot = player.session().plots[loc.coords]
        if (plot == null || plot.dead) {
            return
        }
        if (plot.fertilised) {
            mes("This plant has already been fertilised.")
            return
        }
        if (invDel(inv, FERTILISER, 1).failure) {
            return
        }
        anim(SEQ_SPRINKLE)
        spotanim(SPOT_SPRINKLE)
        delay(1)
        plot.fertilised = true
        val remaining = plot.nextTick - worldClock.cycle
        if (remaining > 0) {
            plot.nextTick = worldClock.cycle + remaining / 2
        }
    }

    private suspend fun ProtectedAccess.harvestPlant(seed: Seed, loc: BoundLocInfo) {
        val plot = player.session().plots[loc.coords]
        if (plot == null || plot.seed != seed || plot.stage < 4) {
            mes("That is not your crop.")
            return
        }
        anim(SEQ_PICK)
        delay(1)
        invAdd(inv, seed.fruitObj, 1)
        statAdvance("stat.farming", seed.harvestXp.toDouble())
        locRepo.change(plot.patch, EMPTY_PATCH, Int.MAX_VALUE)
        player.session().plots.remove(loc.coords)
    }

    private suspend fun ProtectedAccess.clearPlant(loc: BoundLocInfo) {
        val plot = player.session().plots[loc.coords]
        if (plot == null) {
            mes("That is not your crop.")
            return
        }
        anim(SEQ_DIG)
        delay(1)
        locRepo.change(plot.patch, EMPTY_PATCH, Int.MAX_VALUE)
        player.session().plots.remove(loc.coords)
    }

    private suspend fun ProtectedAccess.depositFruit(loc: BoundLocInfo) {
        val session = player.session()
        var sack = vars[VB_SCORE]
        val carried = Seed.entries.associateWith { inv.count(it.fruitObj) }
        if (carried.values.sum() == 0) {
            mes("You don't have any fruit to deposit.")
            return
        }
        var deposited = 0
        var sackFilled = false
        for ((seed, count) in carried) {
            for (i in 0 until count) {
                if (sack >= SACK_LIMIT) {
                    break
                }
                if (invDel(inv, seed.fruitObj, 1).failure) {
                    break
                }
                sack++
                session.carry++
                if (session.carry >= 3) {
                    session.carry -= 3
                    addPoints(1)
                }
                val xp = if (sack >= BONUS_FROM) seed.depositXp * 2 else seed.depositXp
                statAdvance("stat.farming", xp.toDouble())
                deposited++
                if (sack == SACK_LIMIT) {
                    statAdvance("stat.farming", (seed.harvestXp * 250).toDouble())
                    addPoints(SACK_BONUS)
                    sack = 0
                    sackFilled = true
                    break
                }
            }
            if (sackFilled) {
                break
            }
        }
        vars[VB_SCORE] = sack
        locRepo.change(loc, if (sack > 0) SACK_FULL else SACK_EMPTY, Int.MAX_VALUE)
        if (deposited > 0) {
            mes("You deposit $deposited fruit into the sack.")
        }
        if (sackFilled) {
            mes("The sack is emptied into the cart ready for transport.")
        }
    }

    private fun ProtectedAccess.addPoints(amount: Int) {
        val current = vars[VB_POINTS]
        if (current >= POINTS_CAP) {
            return
        }
        vars[VB_POINTS] = minOf(POINTS_CAP, current + amount)
    }

    private suspend fun ProtectedAccess.eatFruit(seed: Seed) {
        if (invDel(inv, seed.fruitObj, 1).failure) {
            return
        }
        anim(SEQ_EAT)
        delay(1)
        mes("You eat the ${seed.fruitName} fruit.")
    }

    private fun ProtectedAccess.consumeWaterDose(): Boolean {
        if (inv.count(ZEAH_CAN) > 0 && vars[VB_CAN_CHARGES] > 0) {
            vars[VB_CAN_CHARGES] = vars[VB_CAN_CHARGES] - 1
            return true
        }
        for (dose in WATERING_CAN_MAX downTo 1) {
            val can = "obj.watering_can_$dose"
            if (inv.count(can) > 0) {
                if (invDel(inv, can, 1).success) {
                    invAdd(inv, "obj.watering_can_${dose - 1}", 1)
                    return true
                }
            }
        }
        return false
    }

    private suspend fun ProtectedAccess.refillCan(can: String) {
        if (can == ZEAH_CAN) {
            vars[VB_CAN_CHARGES] = CAN_CHARGES_MAX
            mes("You refill Gricoller's watering can.")
        } else {
            if (invDel(inv, can, 1).failure) {
                return
            }
            invAdd(inv, "obj.watering_can_$WATERING_CAN_MAX", 1)
            mes("You fill the watering can.")
        }
        anim(SEQ_FILL)
        delay(1)
    }

    private suspend fun ProtectedAccess.talkToGricoller(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Welcome to my farm, young'un. Help me grow these" +
                " plants and I'll reward you kindly.")
            when (
                choice3(
                    "What do I do here?",
                    0,
                    "What rewards can I get?",
                    1,
                    "Nothing.",
                    2,
                )
            ) {
                0 -> chatNpc(
                    neutral,
                    "Take seeds from the table, plant them in the patches and" +
                        " keep them watered. Three waterings and they'll be ready" +
                        " to harvest. Deposit the fruit in the sack for points.",
                )
                1 -> access.openOmnishop(SHOP_ROW)
            }
        }
    }

    private fun armTick(player: Player, session: TitheSession) {
        if (!session.armed) {
            session.armed = true
            player.timerAt(TICK_TIMER, worldClock.cycle, TICK_INTERVAL)
        }
    }

    private suspend fun ProtectedAccess.tick() {
        val session = player.session()
        if (session.inside && !insideFarm(player.coords)) {
            leaveFarm()
            mes("You leave the Tithe Farm.")
            session.armed = false
            return
        }
        val now = worldClock.cycle
        for (plot in session.plots.values) {
            if (plot.dead || plot.stage >= 4 || now < plot.nextTick) {
                continue
            }
            if (plot.watered) {
                plot.stage++
                plot.watered = false
                if (plot.stage >= 4) {
                    locRepo.change(plot.patch, matureLoc(plot.seed), Int.MAX_VALUE)
                } else {
                    plot.nextTick = now + stageTicks(plot)
                    locRepo.change(plot.patch, dryLoc(plot.seed, plot.stage), Int.MAX_VALUE)
                }
            } else {
                plot.dead = true
                locRepo.change(plot.patch, deadLoc(plot.seed, plot.stage), Int.MAX_VALUE)
            }
        }
        if (session.inside || session.plots.isNotEmpty()) {
            player.timerAt(TICK_TIMER, worldClock.cycle, TICK_INTERVAL)
        } else {
            session.armed = false
        }
    }

    private fun onLogin(player: Player) {
        val session = sessions[player.userId] ?: return
        for (plot in session.plots.values) {
            locRepo.change(plot.patch, EMPTY_PATCH, Int.MAX_VALUE)
        }
        session.plots.clear()
        protectedAccess.launch(player) {
            if (insideFarm(player.coords)) {
                session.inside = true
                ifOpenOverlay(HUD, HUD_TARGET)
                armTick(player, session)
            } else {
                session.inside = false
                session.armed = false
            }
        }
    }

    private fun insideFarm(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in INSIDE_X && coords.z in INSIDE_Z

    private fun stageTicks(plot: Plot): Int = if (plot.fertilised) FERT_TICKS else STAGE_TICKS

    private class TitheSession {
        val plots = mutableMapOf<CoordGrid, Plot>()
        var carry = 0
        var inside = false
        var armed = false
    }

    private class Plot(
        val patch: BoundLocInfo,
        var seed: Seed,
        var stage: Int,
        var watered: Boolean,
        var fertilised: Boolean,
        var nextTick: Int,
        var dead: Boolean = false,
    )

    private enum class Seed(
        val index: Int,
        val level: Int,
        val key: String,
        val seedObj: String,
        val fruitObj: String,
        val fruitName: String,
        val harvestXp: Int,
        val depositXp: Int,
    ) {
        GOLOVANOVA(
            index = 1,
            level = 34,
            key = "a",
            seedObj = "obj.hosidius_tithe_seed_a",
            fruitObj = "obj.hosidius_tithe_fruit_a",
            fruitName = "Golovanova",
            harvestXp = 6,
            depositXp = 60,
        ),
        BOLOGANO(
            index = 2,
            level = 54,
            key = "b",
            seedObj = "obj.hosidius_tithe_seed_b",
            fruitObj = "obj.hosidius_tithe_fruit_b",
            fruitName = "Bologano",
            harvestXp = 14,
            depositXp = 140,
        ),
        LOGAVANO(
            index = 3,
            level = 74,
            key = "c",
            seedObj = "obj.hosidius_tithe_seed_c",
            fruitObj = "obj.hosidius_tithe_fruit_c",
            fruitName = "Logavano",
            harvestXp = 23,
            depositXp = 230,
        ),
    }

    private companion object {
        const val FARM_DOOR = "loc.hosidius_tithe_farm_door"
        const val SEED_TABLE = "loc.tithe_plant_seed_table"
        const val EMPTY_PATCH = "loc.hosidius_tithe_empty"
        const val SACK_EMPTY = "loc.tithe_sack_of_fruit_empty"
        const val SACK_FULL = "loc.tithe_sack_of_fruit"
        val SACKS = listOf(SACK_EMPTY, SACK_FULL)
        const val WATER_BARREL = "loc.water_barrel1"
        const val FARMER = "npc.hosidius_tithe_farmer"
        const val SHOP_ROW = "dbrow.tithe_shop_data"

        const val TICK_TIMER = "timer.tithe_tick"
        const val HUD = "interface.hosidius_strip_farming"
        const val HUD_TARGET = "component.toplevel_osrs_stretch:overlay_hud"

        const val VB_SCORE = "varbit.hosidius_tithe_score"
        const val VB_POINTS = "varbit.hosidius_tithe_rewardpoints"
        const val VB_TYPE = "varbit.hosidius_tithe_type"
        const val VB_UNLOCKED = "varbit.zeah_tithe_minigame_unlocked"
        const val VB_CAN_CHARGES = "varbit.zeah_wateringcan_charges"
        const val VB_ELITE_DIARY = "varbit.kourend_diary_elite_complete"

        const val FERTILISER = "obj.hosidius_tithe_fertiliser"
        const val DIBBER = "obj.dibber"
        const val ZEAH_CAN = "obj.zeah_wateringcan"
        const val EMPTY_CAN = "obj.watering_can_0"
        const val WATERING_CAN_MAX = 8
        val WATERING_CANS = (1..WATERING_CAN_MAX).map { "obj.watering_can_$it" } + ZEAH_CAN

        const val SEQ_PLANT = "seq.farming_seed_dibbing"
        const val SEQ_PLANT_NODIB = "seq.farming_seed_dibbing_nodib"
        const val SEQ_WATERING = "seq.farming_watering"
        const val SPOT_WATERING = "spotanim.farming_wateringcan_water_spotanim"
        const val SEQ_FILL = "seq.farming_watering_water"
        const val SEQ_SPRINKLE = "seq.farming_ingredient_sprinkle"
        const val SPOT_SPRINKLE = "spotanim.farming_ingredients_sprinkle_spot"
        const val SEQ_PICK = "seq.picking_mid"
        const val SEQ_DIG = "seq.human_dig"
        const val SEQ_EAT = "seq.human_eat"

        const val DOOR_X = 1805
        val INSIDE_X = 1806..1839
        val INSIDE_Z = 3478..3522

        const val MIN_LEVEL = 34
        const val STAGE_TICKS = 100
        const val FERT_TICKS = 50
        const val TICK_INTERVAL = 1
        const val SACK_LIMIT = 100
        const val BONUS_FROM = 75
        const val SACK_BONUS = 2
        const val POINTS_CAP = 16000
        const val CAN_CHARGES_MAX = 1000
        const val SEED_TAKE_LIMIT = 10000

        fun dryLoc(seed: Seed, stage: Int) = "loc.hosidius_tithe_${seed.key}_${stage}_dry"

        fun wetLoc(seed: Seed, stage: Int) = "loc.hosidius_tithe_${seed.key}_${stage}_wet"

        fun deadLoc(seed: Seed, stage: Int) = "loc.hosidius_tithe_${seed.key}_${stage}_dead"

        fun matureLoc(seed: Seed) = "loc.hosidius_tithe_${seed.key}_4"
    }
}
