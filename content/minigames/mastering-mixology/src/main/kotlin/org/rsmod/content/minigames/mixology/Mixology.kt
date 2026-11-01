package org.rsmod.content.minigames.mixology

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Mixology
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldClock: MapClock,
    private val eventBus: EventBus,
) : PluginScript() {

    private val sessions = mutableMapOf<Long, MixSession>()

    private fun Player.session() = sessions.getOrPut(userId) { MixSession() }

    override fun ScriptContext.startup() {
        onOpLoc1(STAIRS_ENTRY) { enterLab() }
        onOpLoc1(STAIRS_EXIT) { leaveLab() }
        onOpLoc1(MILL) { operateMill() }
        onOpLoc1(HOPPER) { depositPaste() }
        onOpLoc1(CONVEYOR) { fulfilOrders() }
        for ((paste, lever) in LEVERS) {
            onOpLoc1(lever) { pullLever(paste) }
        }
        for (herb in HERB_UNF.keys + HERB_PASTE.keys) {
            onOpLocU(MILL, "obj.$herb") { loadMill(it.objType) }
        }
        onOpLoc1(VESSEL_EMPTY) { mes("The vessel is empty.") }
        for (machine in Machine.entries) {
            onOpLoc1("loc.mm_lab_machine_${machine.key}_empty") { useMachine(machine) }
        }
        for (recipe in Recipe.entries) {
            onOpLoc1("loc.mm_lab_vessel_ready_${recipe.variant}") { takeVessel(recipe) }
            for (machine in Machine.entries) {
                onOpLoc1("loc.mm_lab_machine_${machine.key}_${machine.variantOf(recipe)}") {
                    speedMachine(machine)
                }
                onOpLoc3("loc.mm_lab_machine_${machine.key}_${machine.variantOf(recipe)}") {
                    checkMachine(machine)
                }
                onOpLocU(
                    "loc.mm_lab_machine_${machine.key}_empty",
                    "obj.mm_potion_${recipe.code}_unfinished",
                ) {
                    loadMachine(machine, recipe)
                }
            }

            onOpHeldU(DIGWEED, "obj.mm_potion_${recipe.code}_unfinished") {
                improvePotion(recipe, "obj.mm_potion_${recipe.code}_unfinished")
            }
            onOpHeldU(DIGWEED, "obj.mm_potion_${recipe.code}_finished") {
                improvePotion(recipe, "obj.mm_potion_${recipe.code}_finished")
            }
        }
        for (paste in Paste.entries) {
            onOpLocU(HOPPER, paste.obj) { depositPaste() }
        }
        onOpLoc1(HERB_READY) { collectDigweed(it.loc.coords) }
        for (supervisor in SUPERVISORS) {
            onOpNpc1(supervisor) { talkSupervisor() }
        }
        onOpNpc3(SUPERVISOR_2OP) { openOmnishop(SHOP_ROW) }

        onPlayerTimer(TICK) { tick() }
        onPlayerLogin { onLogin(player) }
    }

    private suspend fun ProtectedAccess.enterLab() {
        if (player.statBase("stat.herblore") < MIN_LEVEL) {
            mes("You need a Herblore level of $MIN_LEVEL to take part in mixology.")
            return
        }
        stopAction()
        teleport(INSIDE_SPAWN)
        ifOpenOverlay(HUD, HUD_TARGET)
        if (vars[VB_ORDER_1_TYPE] == 0) {
            rollOrders()
        }
        arm()
    }

    private suspend fun ProtectedAccess.leaveLab() {
        stopAction()
        teleport(OUTSIDE_SPAWN)
        exitLab()
    }

    private fun ProtectedAccess.exitLab() {
        player.ifCloseOverlay(HUD, eventBus)
        witherDigweed()
    }

    private fun ProtectedAccess.witherDigweed() {
        if (invDel(inv, DIGWEED, inv.count(DIGWEED), strict = false).success) {
            mes("Your digweed has withered away.")
        }
    }

    // ---- Orders ---------------------------------------------------------------

    private fun ProtectedAccess.rollOrders() {
        val level = player.statBase("stat.herblore")
        val eligible = Recipe.entries.filter { it.level <= level }
        repeat(3) { i ->
            vars[orderTypeVb(i)] = weightedPick(eligible).index
            vars[orderModVb(i)] = (0..3).random()
        }
    }

    private fun weightedPick(pool: List<Recipe>): Recipe {
        val total = pool.sumOf { it.weight }
        var roll = (1..total).random()
        for (recipe in pool) {
            roll -= recipe.weight
            if (roll <= 0) return recipe
        }
        return pool.last()
    }

    // ---- Refiner / mill --------------------------------------------------------

    private suspend fun ProtectedAccess.loadMill(obj: ItemServerType?) {
        val session = player.session()
        val internal = obj?.internalName?.removePrefix("obj.") ?: return
        val herb = herbFor(internal) ?: return
        val count = inv.count("obj.$internal")
        if (invDel(inv, "obj.$internal", count, strict = false).failure) {
            return
        }
        session.millQueue.addAll(List(count) { herb })
        anim(SEQ_MILL_HERB)
        mes("You add ${count}x ${obj.name} to the refiner.")
        arm()
    }

    private suspend fun ProtectedAccess.operateMill() {
        val session = player.session()
        if (session.millQueue.isEmpty()) {
            mes("The refiner needs herbs or unfinished potions to process.")
            return
        }
        processMill(session)
        anim(SEQ_MILL)
        session.millBoostUntil = worldClock.cycle + 2
        arm()
    }

    private fun ProtectedAccess.processMill(session: MixSession) {
        val herb = session.millQueue.removeFirstOrNull() ?: return
        val pasteObj = herbPasteObj(herb) ?: return
        val qty = herbPasteQty(herb)
        invAdd(inv, pasteObj, qty)
        statAdvance("stat.herblore", REFINE_XP.toDouble())
    }

    private fun herbFor(internal: String): String? =
        if (HERB_PASTE.containsKey(internal)) internal else HERB_UNF[internal]

    private fun herbPasteObj(herb: String): String? =
        enumIntObj(ENUM_HERB_PASTE, objId(herb))?.internalName

    private fun herbPasteQty(herb: String): Int = enumIntInt(ENUM_HERB_QTY, objId(herb)) ?: 0

    // ---- Hopper ---------------------------------------------------------------

    private suspend fun ProtectedAccess.depositPaste() {
        var moved = 0
        for (paste in Paste.entries) {
            val held = inv.count(paste.obj)
            if (held == 0) continue
            val room = HOPPER_CAP - vars[paste.availableVb]
            val add = minOf(held, room)
            if (add <= 0) {
                mes("The hopper can't hold any more ${paste.key} paste.")
                continue
            }
            if (invDel(inv, paste.obj, add).success) {
                vars[paste.availableVb] = vars[paste.availableVb] + add
                moved += add
            }
        }
        if (moved > 0) {
            anim(SEQ_HOPPER)
            mes("You deposit your pastes into the hopper.")
        } else {
            mes("You don't have any paste to deposit.")
        }
    }

    // ---- Mixer levers ----------------------------------------------------------

    private suspend fun ProtectedAccess.pullLever(paste: Paste) {
        if (vars[paste.availableVb] < PASTE_PER_LETTER) {
            mes("The hopper doesn't contain enough ${paste.key} paste.")
            return
        }
        vars[paste.availableVb] = vars[paste.availableVb] - PASTE_PER_LETTER
        val slots = SLOT_VBS.map { vars[it] }
        val firstEmpty = slots.indexOf(0)
        if (firstEmpty >= 0) {
            vars[SLOT_VBS[firstEmpty]] = paste.slot
        } else {
            Paste.ofSlot(slots[0])?.let { vars[it.availableVb] = vars[it.availableVb] + PASTE_PER_LETTER }
            vars[SLOT_VBS[0]] = slots[1]
            vars[SLOT_VBS[1]] = slots[2]
            vars[SLOT_VBS[2]] = paste.slot
        }
        vars[VB_VESSEL_READY] = mixerRecipe()?.vesselIndex ?: 0
        anim(SEQ_LEVER)
    }

    private fun ProtectedAccess.mixerRecipe(): Recipe? {
        val slots = SLOT_VBS.map { vars[it] }
        if (0 in slots) return null
        val counts = IntArray(4)
        for (s in slots) counts[s]++
        return Recipe.entries.firstOrNull { r ->
            r.letters.groupingBy { it }.eachCount().let { need ->
                need.getOrDefault('m', 0) == counts[1] &&
                    need.getOrDefault('a', 0) == counts[2] &&
                    need.getOrDefault('l', 0) == counts[3]
            }
        }
    }

    private suspend fun ProtectedAccess.takeVessel(recipe: Recipe) {
        if (vars[VB_VESSEL_READY] != recipe.vesselIndex) return
        if (player.stat("stat.herblore") < recipe.level) {
            mes("You need level ${recipe.level} Herblore to make this potion.")
            return
        }
        vars[VB_VESSEL_READY] = 0
        for (vb in SLOT_VBS) vars[vb] = 0
        invAdd(inv, recipe.unfObj, 1)
        mes("You take the ${recipe.potionName} base from the mixing vessel.")
    }

    // ---- Machines --------------------------------------------------------------

    private suspend fun ProtectedAccess.useMachine(machine: Machine) {
        val type = firstUnfinished() ?: run {
            mes("You don't have a potion base to put into the ${machine.key}.")
            return
        }
        val recipe = Recipe.of(type) ?: return
        loadMachine(machine, recipe)
    }

    private suspend fun ProtectedAccess.loadMachine(machine: Machine, recipe: Recipe) {
        if (invDel(inv, recipe.unfObj, 1).success) {
            startMachine(machine, recipe)
        }
    }

    private fun ProtectedAccess.firstUnfinished(): Int? =
        inv.objs.firstNotNullOfOrNull { obj ->
            obj?.id?.let { enumIntInt(ENUM_UNF_TYPE, it) }?.takeIf { it > 0 }
        }

    private fun ProtectedAccess.startMachine(machine: Machine, recipe: Recipe) {
        val session = player.session()
        vars[machine.potionVb] = recipe.vesselIndex
        vars[machine.progressVb] = 0
        session.machines[machine] = MachineRun(clicks = 0, windowOpen = false, pumps = 0)
        anim("seq.human_machinery_alchemy01_${machine.key}01_interact01")
        mes("You place the potion base into the ${machine.key}.")
        arm()
    }

    private suspend fun ProtectedAccess.speedMachine(machine: Machine) {
        val type = vars[machine.potionVb]
        if (type == 0) return
        if (vars[machine.progressVb] >= PROGRESS_MAX) {
            finishMachine(machine, type)
            return
        }
        val session = player.session()
        val run = session.machines[machine] ?: return
        anim("seq.human_machinery_alchemy01_${machine.key}01_interact01")
        when (machine) {
            Machine.RETORT -> {
                vars[machine.progressVb] = (vars[machine.progressVb] + RETORT_CLICK).coerceAtMost(PROGRESS_MAX)
                statAdvance("stat.herblore", RETORT_CLICK_XP.toDouble())
            }
            Machine.AGITATOR -> {
                if (run.windowOpen) {
                    vars[machine.progressVb] = (vars[machine.progressVb] + SKILL_BOOST).coerceAtMost(PROGRESS_MAX)
                    statAdvance("stat.herblore", SKILL_XP.toDouble())
                    run.clicks++
                    run.windowOpen = false
                    vars[VB_HIT_AGITATOR] = 0
                } else {
                    vars[machine.progressVb] = (vars[machine.progressVb] - SKILL_MISS).coerceAtLeast(0)
                }
            }
            Machine.ALEMBIC -> {
                if (run.pumps >= PUMP_TARGET && !run.clicked) {
                    vars[machine.progressVb] = (vars[machine.progressVb] + SKILL_BOOST).coerceAtMost(PROGRESS_MAX)
                    statAdvance("stat.herblore", SKILL_XP.toDouble())
                    run.clicked = true
                    vars[VB_HIT_ALEMBIC] = 1
                } else if (!run.clicked) {
                    vars[machine.progressVb] = (vars[machine.progressVb] - SKILL_MISS).coerceAtLeast(0)
                }
            }
        }
        arm()
    }

    private suspend fun ProtectedAccess.checkMachine(machine: Machine) {
        val type = vars[machine.potionVb]
        if (type == 0) {
            mes("The ${machine.key} is empty.")
            return
        }
        val pct = vars[machine.progressVb] * 100 / PROGRESS_MAX
        mes("The ${machine.key} is $pct% through processing.")
    }

    private fun ProtectedAccess.tickMachines(session: MixSession) {
        val level = player.statBase("stat.herblore")
        for (machine in Machine.entries) {
            val type = vars[machine.potionVb]
            if (type == 0) continue
            val run = session.machines.getOrPut(machine) { MachineRun(0, false, 0) }
            var progress = vars[machine.progressVb] + machine.rate(level)
            if (machine == Machine.AGITATOR) {
                if (!run.windowOpen && run.clicks < AGITATOR_WINDOWS && progress >= AGITATOR_WINDOW_AT * (run.clicks + 1)) {
                    run.windowOpen = true
                    run.windowExpires = worldClock.cycle + AGITATOR_WINDOW_TICKS
                    vars[VB_HIT_AGITATOR] = 1
                }
                if (run.windowOpen && worldClock.cycle > run.windowExpires) {
                    run.windowOpen = false
                    vars[VB_HIT_AGITATOR] = 0
                }
            }
            if (machine == Machine.ALEMBIC && !run.clicked) {
                run.pumpCounter++
                if (run.pumpCounter >= PUMP_TICKS) {
                    run.pumpCounter = 0
                    run.pumps++
                }
            }
            vars[machine.progressVb] = progress.coerceAtMost(PROGRESS_MAX)
        }
    }

    private fun ProtectedAccess.finishMachine(machine: Machine, type: Int) {
        val recipe = Recipe.ofVesselIndex(type) ?: return
        val session = player.session()
        session.machines.remove(machine)
        vars[machine.potionVb] = 0
        vars[machine.progressVb] = 0
        session.machineUsed[recipe] = machine
        invAdd(inv, recipe.finObj, 1)
        mes("You collect the finished ${recipe.potionName} from the ${machine.key}.")
    }

    // ---- Conveyor ---------------------------------------------------------------

    private suspend fun ProtectedAccess.fulfilOrders() {
        val session = player.session()
        val orders = (0..2).map { vars[orderTypeVb(it)] to vars[orderModVb(it)] }
        var deposited = 0
        var xp = 0
        var resin = IntArray(4)
        for ((type, mod) in orders) {
            if (deposited >= 3) break
            val recipe = Recipe.of(type) ?: continue
            if (inv.count(recipe.finObj) == 0) continue
            if (mod > 0 && session.machineUsed[recipe] != Machine.ofModifier(mod)) continue
            if (invDel(inv, recipe.finObj, 1).failure) continue
            val improved = session.improved[recipe] ?: 0
            if (improved > 0) session.improved[recipe] = improved - 1
            val mult = if (improved > 0) 2 else 1
            xp += recipe.xp * mult
            resin[1] += recipe.mox * mult
            resin[2] += recipe.aga * mult
            resin[3] += recipe.lye * mult
            deposited++
        }
        if (deposited == 0) {
            val type = firstFinished() ?: run {
                mes("You don't have any potions to deposit.")
                return
            }
            val recipe = Recipe.of(type) ?: return
            if (invDel(inv, recipe.finObj, 1).failure) return
            val improved = (session.improved[recipe] ?: 0) > 0
            if (improved) session.improved[recipe] = 0
            val mult = if (improved) 2 else 1
            xp += recipe.xp / 3 * mult
            val slot = recipe.fallbackResin()
            resin[slot] += OFFORDER_RESIN * mult
            deposited = 1
        }
        val bonus = when (deposited) {
            2 -> 1.2
            3 -> 1.4
            else -> 1.0
        }
        if (deposited > 1) {
            xp = (xp * bonus).toInt()
        }
        if (xp > 0) statAdvance("stat.herblore", xp.toDouble())
        addResin(Paste.MOX, (resin[1] * bonus).toInt())
        addResin(Paste.AGA, (resin[2] * bonus).toInt())
        addResin(Paste.LYE, (resin[3] * bonus).toInt())
        vars[VP_TOTAL_ORDERS] = vars[VP_TOTAL_ORDERS] + deposited
        vars[VB_SINCE_HERB] = vars[VB_SINCE_HERB] + deposited
        mes("You fulfil $deposited order${if (deposited == 1) "" else "s"}; the order list refreshes.")
        rollOrders()
        maybeSpawnDigweed()
    }

    private fun ProtectedAccess.firstFinished(): Int? =
        inv.objs.firstNotNullOfOrNull { obj ->
            obj?.id?.let { enumIntInt(ENUM_FIN_TYPE, it) }?.takeIf { it > 0 }
        }

    private fun ProtectedAccess.addResin(paste: Paste, amount: Int) {
        if (amount <= 0) return
        vars[paste.pointsVp] = vars[paste.pointsVp] + amount
    }

    private fun ProtectedAccess.maybeSpawnDigweed() {
        val session = player.session()
        if (vars[VB_SINCE_HERB] < session.nextHerbAt) return
        if (inv.count(DIGWEED) > 0) return
        val free = (1..4).filter { vars["varbit.mm_herb_ready_$it"] == 0 }
        if (free.isEmpty()) return
        vars["varbit.mm_herb_ready_${free.random()}"] = 1
        session.nextHerbAt = vars[VB_SINCE_HERB] + (HERB_GAP_MIN..HERB_GAP_MAX).random()
        mes("A digweed plant has matured somewhere in the lab!")
    }

    private suspend fun ProtectedAccess.collectDigweed(coords: CoordGrid) {
        for (spot in 1..4) {
            if (vars["varbit.mm_herb_ready_$spot"] == 1) {
                vars["varbit.mm_herb_ready_$spot"] = 0
                invAdd(inv, DIGWEED, 1)
                anim("seq.picking_low")
                mes("You collect the digweed.")
                return
            }
        }
    }

    private suspend fun ProtectedAccess.improvePotion(recipe: Recipe, item: String) {
        val session = player.session()
        if ((session.improved[recipe] ?: 0) >= inv.count(item)) {
            mes("This potion has already been improved with digweed.")
            return
        }
        if (invDel(inv, DIGWEED, 1).failure) return
        session.improved[recipe] = (session.improved[recipe] ?: 0) + 1
        mes("You add the digweed to the potion, improving it.")
    }

    // ---- Supervisor --------------------------------------------------------------

    private suspend fun ProtectedAccess.talkSupervisor() {
        vars[VB_MET_SUPERVISOR] = 1
        vars[VB_INTRO_DONE] = 1
        mes("Supervisor Lalo greets you and explains the order board.")
    }

    // ---- Tick / login -----------------------------------------------------------

    private fun ProtectedAccess.arm() {
        val session = player.session()
        if (!session.armed) {
            session.armed = true
            player.timerAt(TICK, worldClock.cycle, 1)
        }
    }

    private suspend fun ProtectedAccess.tick() {
        val session = player.session()
        if (!insideLab(player.coords)) {
            exitLab()
        }
        if (session.millQueue.isNotEmpty()) {
            val every = if (worldClock.cycle <= session.millBoostUntil) 1 else 2
            if (worldClock.cycle % every == 0) {
                processMill(session)
            }
        }
        tickMachines(session)
        if (session.millQueue.isNotEmpty() || session.machines.isNotEmpty()) {
            player.timerAt(TICK, worldClock.cycle, 1)
        } else {
            session.armed = false
        }
    }

    private fun onLogin(player: Player) {
        val session = player.session()
        protectedAccess.launch(player) {
            if (insideLab(player.coords)) {
                ifOpenOverlay(HUD, HUD_TARGET)
                if (session.millQueue.isNotEmpty() || Machine.entries.any { vars[it.potionVb] > 0 }) {
                    arm()
                }
            } else {
                exitLab()
                session.armed = false
            }
        }
    }

    private fun insideLab(coords: CoordGrid): Boolean =
        coords.x in LAB_X && coords.z in LAB_Z

    // ---- Enum helpers ------------------------------------------------------------

    private fun objId(internal: String): Int =
        ServerCacheManager.getItem("obj.$internal".asRSCM(RSCMType.OBJ))?.id ?: -1

    private fun enumIntInt(enumId: Int, key: Int): Int? =
        (ServerCacheManager.getEnum(enumId)?.values?.get(key) as? Number)?.toInt()

    private fun enumIntObj(enumId: Int, key: Int): ItemServerType? =
        (ServerCacheManager.getEnum(enumId)?.values?.get(key) as? Number)
            ?.toInt()
            ?.let { ServerCacheManager.getItem(it) }

    private fun orderTypeVb(i: Int) = "varbit.mm_lab_order_${i + 1}_type"

    private fun orderModVb(i: Int) = "varbit.mm_lab_order_${i + 1}_modifier"

    private fun pasteKey(paste: Paste) = paste.key

    // ---- Model ---------------------------------------------------------------------

    private class MixSession {
        val millQueue = ArrayDeque<String>()
        var millBoostUntil = 0
        val machines = mutableMapOf<Machine, MachineRun>()
        val machineUsed = mutableMapOf<Recipe, Machine>()
        val improved = mutableMapOf<Recipe, Int>()
        var nextHerbAt = 3
        var armed = false
    }

    private class MachineRun(
        var clicks: Int,
        var windowOpen: Boolean,
        var pumps: Int,
        var clicked: Boolean = false,
        var pumpCounter: Int = 0,
        var windowExpires: Int = 0,
    )

    private enum class Paste(val slot: Int, val key: String) {
        MOX(1, "mox"),
        AGA(2, "aga"),
        LYE(3, "lye"),
        ;

        val obj get() = "obj.mm_${key}_paste"
        val availableVb get() = "varbit.mm_available_$key"
        val pointsVp get() = "varp.mixology_${key}_points"

        companion object {
            fun ofSlot(slot: Int): Paste? = entries.firstOrNull { it.slot == slot }
        }
    }

    private enum class Machine(val key: String) {
        AGITATOR("agitator"),
        ALEMBIC("alembic"),
        RETORT("retort"),
        ;

        val potionVb get() = "varbit.mm_lab_${key}_potion"
        val progressVb get() = "varbit.mm_${key}_progress"

        fun variantOf(recipe: Recipe): String =
            when (recipe) {
                Recipe.MML -> if (this == RETORT) "mml" else "mm1"
                Recipe.AAL -> if (this == RETORT) "aal" else "aa1"
                else -> recipe.code
            }

        fun rate(level: Int): Int {
            val scaled = 1 + (level - MIN_LEVEL).coerceAtLeast(0) / 25
            return if (this == RETORT) scaled + 1 else scaled
        }

        companion object {
            fun ofModifier(mod: Int): Machine? = when (mod) {
                1 -> AGITATOR
                2 -> RETORT
                3 -> ALEMBIC
                else -> null
            }
        }
    }

    private enum class Recipe(
        val index: Int,
        val code: String,
        val letters: String,
        val level: Int,
        val xp: Int,
        val mox: Int,
        val aga: Int,
        val lye: Int,
        val weight: Int,
        val potionName: String,
    ) {
        MMM(1, "mmm", "mmm", 60, 190, 20, 0, 0, 10, "Mammoth-might mix"),
        MMA(2, "mma", "mma", 63, 215, 20, 10, 0, 8, "Mystic mana amalgam"),
        MML(3, "mml", "mml", 66, 240, 20, 0, 10, 8, "Marley's moonlight"),
        AAA(4, "aaa", "aaa", 60, 190, 0, 20, 0, 10, "Alco-augmentator"),
        AAM(5, "aam", "aam", 69, 265, 10, 20, 0, 8, "Azure aura mix"),
        AAL(6, "aal", "aal", 72, 290, 0, 20, 10, 8, "Aqualux amalgam"),
        LLL(7, "lll", "lll", 60, 190, 0, 0, 20, 10, "Liplack liquor"),
        LLM(8, "llm", "llm", 75, 315, 10, 0, 20, 8, "Megalite liquid"),
        LLA(9, "lla", "lla", 78, 340, 0, 10, 20, 8, "Anti-leech lotion"),
        MAL(10, "mal", "mal", 81, 365, 20, 20, 20, 6, "Mixalot"),
        ;

        val variant: String
            get() = when (this) {
                MML -> "mm1"
                AAL -> "aa1"
                else -> code
            }

        val vesselIndex: Int
            get() = when (this) {
                AAL -> 5
                AAM -> 6
                LLA -> 8
                LLM -> 9
                else -> index
            }

        val unfObj get() = "obj.mm_potion_${code}_unfinished"
        val finObj get() = "obj.mm_potion_${code}_finished"

        fun pastes(): List<Pair<Paste, Int>> {
            val counts = letters.groupingBy { it }.eachCount()
            return Paste.entries.mapNotNull { p ->
                val n = counts.getOrDefault(p.key[0], 0)
                if (n > 0) p to n else null
            }
        }

        fun fallbackResin(): Int = when {
            lye > 0 -> 3
            aga > 0 -> 2
            else -> 1
        }

        companion object {
            fun of(index: Int): Recipe? = entries.firstOrNull { it.index == index }

            fun ofVesselIndex(vesselIndex: Int): Recipe? =
                entries.firstOrNull { it.vesselIndex == vesselIndex }
        }
    }

    private companion object {
        const val STAIRS_ENTRY = "loc.mm_lab_stairs_entry"
        const val STAIRS_EXIT = "loc.mm_lab_stairs"
        const val MILL = "loc.mm_lab_mill"
        const val HOPPER = "loc.mm_lab_hopper"
        const val CONVEYOR = "loc.mm_lab_conveyor"
        const val VESSEL_EMPTY = "loc.mm_lab_vessel_empty"
        const val HERB_READY = "loc.mm_herb_ready"
        const val SUPERVISOR_1OP = "npc.mm_lab_supervisor_1op"
        const val SUPERVISOR_2OP = "npc.mm_lab_supervisor_2op"
        val SUPERVISORS = listOf("npc.mm_lab_supervisor", SUPERVISOR_1OP, SUPERVISOR_2OP)
        const val DIGWEED = "obj.mm_lab_special_herb"
        const val SHOP_ROW = "dbrow.mixology_rewards"
        const val HUD = "interface.mm_overlay"
        const val HUD_TARGET = "component.toplevel_osrs_stretch:overlay_hud"
        const val TICK = "timer.mm_lab_tick"

        const val VB_ORDER_1_TYPE = "varbit.mm_lab_order_1_type"
        const val VB_VESSEL_READY = "varbit.mm_lab_vessel_ready"
        const val VB_SINCE_HERB = "varbit.mm_orders_since_herb"
        const val VB_HIT_AGITATOR = "varbit.mm_lab_hit_skillshot_agitator"
        const val VB_HIT_ALEMBIC = "varbit.mm_lab_hit_skillshot_alembic"
        const val VB_MET_SUPERVISOR = "varbit.mm_met_supervisor"
        const val VB_INTRO_DONE = "varbit.mm_introduction_complete"
        const val VP_TOTAL_ORDERS = "varp.total_mixology_orders"
        val SLOT_VBS =
            listOf(
                "varbit.mm_lab_mixer_slot_0",
                "varbit.mm_lab_mixer_slot_1",
                "varbit.mm_lab_mixer_slot_2",
            )
        val LEVERS =
            mapOf(
                Paste.MOX to "loc.mm_lab_switch_mox",
                Paste.AGA to "loc.mm_lab_switch_aga",
                Paste.LYE to "loc.mm_lab_switch_lye",
            )

        const val MIN_LEVEL = 60
        const val HOPPER_CAP = 3000
        const val PASTE_PER_LETTER = 10
        const val REFINE_XP = 6
        const val PROGRESS_MAX = 60
        const val RETORT_CLICK = 2
        const val RETORT_CLICK_XP = 2
        const val SKILL_BOOST = 20
        const val SKILL_XP = 14
        const val SKILL_MISS = 4
        const val AGITATOR_WINDOW_AT = 20
        const val AGITATOR_WINDOW_TICKS = 4
        const val AGITATOR_WINDOWS = 1
        const val PUMP_TICKS = 9
        const val PUMP_TARGET = 4
        const val OFFORDER_RESIN = 10
        const val HERB_GAP_MIN = 4
        const val HERB_GAP_MAX = 8

        const val SEQ_LEVER = "seq.human_alchemy01_lever01_interact01"
        const val SEQ_HOPPER = "seq.human_alchemy01_hopper01_interact01"
        const val SEQ_MILL = "seq.human_alchemy01_mill01_interact01"
        const val SEQ_MILL_HERB = SEQ_MILL

        const val ENUM_HERB_PASTE = 5538
        const val ENUM_HERB_QTY = 5539
        const val ENUM_UNF_FIN = 5541
        const val ENUM_TYPE_UNF = 5542
        const val ENUM_UNF_TYPE = 5543
        const val ENUM_FIN_TYPE = 5544

        // Lab footprint in Aldarin (verified live: alchemy lab beneath the society)
        val LAB_X = 1375..1400
        val LAB_Z = 9305..9340
        val INSIDE_SPAWN = CoordGrid(1389, 9317, 0)
        val OUTSIDE_SPAWN = CoordGrid(1387, 2918, 0)

        fun baseRate(level: Int): Int = 1

        fun retortRate(level: Int): Int = 1

        private val HERB_PASTE = linkedMapOf(
            "guam_leaf" to 1, "marentill" to 1, "tarromin" to 1, "harralander" to 1,
            "ranarr_weed" to 3, "toadflax" to 3, "avantoe" to 3, "kwuarm" to 3,
            "snapdragon" to 3,
            "irit_leaf" to 2, "huasca" to 2, "cadantine" to 2, "lantadyme" to 2,
            "dwarf_weed" to 2, "torstol" to 2,
        )

        private val HERB_UNF = linkedMapOf(
            "guamvial" to "guam_leaf", "marrentillvial" to "marentill",
            "tarrominvial" to "tarromin", "harralandervial" to "harralander",
            "ranarrvial" to "ranarr_weed", "toadflaxvial" to "toadflax",
            "avantoevial" to "avantoe", "kwuarmvial" to "kwuarm",
            "snapdragonvial" to "snapdragon", "iritvial" to "irit_leaf",
            "huascavial" to "huasca", "cadantinevial" to "cadantine",
            "lantadymevial" to "lantadyme", "dwarfweedvial" to "dwarf_weed",
            "torstolvial" to "torstol",
        )
    }
}
