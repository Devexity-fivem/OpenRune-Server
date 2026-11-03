package org.rsmod.content.minigames.valetotems

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ValeTotemsSitesRow
import org.rsmod.api.table.ValeTotemsWoodsRow
import org.rsmod.api.table.fletching.FletchingRecipesRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ValeTotems
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val shops: Shops,
    private val shopOperations: ShopOperationMap,
    private val worldClock: MapClock,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        shopOperations.costOf(RESEARCH_CURRENCY) { type -> SHOP_PRICES[type.id] ?: type.cost }

        onOpLoc1(LOC_BASE_NONE) { build(it.loc) }

        for (wood in WOODS) {
            onOpLoc1("loc.ent_totems_base_${wood.key}") { carve(Section.BASE, wood, it.loc) }
            onOpLoc1("loc.ent_totems_low_${wood.key}") { carve(Section.LOW, wood, it.loc) }
            onOpLoc1("loc.ent_totems_mid_${wood.key}") { carve(Section.MID, wood, it.loc) }
            onOpLoc1("loc.ent_totems_top_${wood.key}") { carve(Section.TOP, wood, it.loc) }
            onOpLoc1("loc.ent_totems_carved_base_${wood.key}") { decorateMenu(wood, it.loc) }
            onOpLocU("loc.ent_totems_carved_base_${wood.key}") {
                decorateUse(wood, it.loc, it.objType)
            }
        }

        for (pile in OFFERING_LOCS) {
            onOpLoc1(pile) { claim(it.loc) }
            onOpLoc2(pile) { inspectOfferings(it.loc) }
        }

        for (scenery in ADMIRE_LOCS) {
            onOpLoc1(scenery) { admire(it.loc) }
        }

        onOpHeld1(LOOT_OBJ) { rummage(single = true) }
        onOpHeld2(LOOT_OBJ) { rummage(single = false) }
        onOpHeld3(LOOT_OBJ) { inspectLoot() }

        onOpNpc1("npc.ent_totems_intro_ranulph_1op") { talkToRanulph() }
        onOpNpc1("npc.ent_totems_intro_ranulph_2op") { talkToRanulph() }
        onOpNpc3("npc.ent_totems_intro_ranulph_2op") { openExchange() }

        onPlayerTimer(VISIT_TIMER) { visitCycle() }
        onPlayerQueueWithArgs<TotemTask>(ACTION_QUEUE) { processAction(it.args) }
        onPlayerQueueWithArgs<List<Int>>(REWARD_QUEUE) {
            it.args.forEach { site -> grantOfferings(site) }
        }

        onPlayerLogin {
            ensureWorldSpawned()
            player.ensureSpirits()
            player.timerAt(VISIT_TIMER, worldClock.cycle, VISIT_INTERVAL)
        }

        onPlayerCoordsChanged { checkTrailStep(player) }
    }

    private suspend fun ProtectedAccess.build(loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        if (vars["varbit.ent_totems_intro"] < INTRO_DONE) {
            mes("You should speak to Ranulph in Auburnvale before building a totem.")
            return
        }
        if (player.points(site) >= OFFERINGS_SOFT_CAP) {
            mes(
                "You should take some of the offerings already here before building a new totem."
            )
            return
        }
        if (!hasKnife()) {
            mes("You need a knife to work on the totem.")
            return
        }
        val usable =
            WOODS.filter { player.fletchingLvl >= it.level && inv.count(it.log) > 0 }
        if (usable.isEmpty()) {
            mes(
                "You need a knife and some suitable logs to build a totem " +
                    "(Fletching level ${WOODS.minOf { it.level }} required)."
            )
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                actionType = SkillingActionType.CARVE,
                verb = "build",
                entries = usable.map { w -> SkillMultiEntry(w.log, listOf(Material(w.log))) },
                titleOverride = "Which totem would you like to build?",
            )
        ) { selection ->
            val wood =
                usable.firstOrNull { it.log == selection.entry.internal } ?: return@openSkillMulti
            if (inv.count(wood.log) == 0 || !hasKnife()) return@openSkillMulti
            if (player.base(site) != 0 || player.points(site) >= OFFERINGS_SOFT_CAP) {
                return@openSkillMulti
            }
            anim(CARVE_ANIM)
            weakQueue(ACTION_QUEUE, ACTION_QUEUE_CYCLES, TotemTask.build(site, wood.id))
        }
    }

    private fun ProtectedAccess.applyBuild(site: Int, woodId: Int) {
        val wood = WOODS.firstOrNull { it.id == woodId } ?: return
        if (player.base(site) != 0 || player.points(site) >= OFFERINGS_SOFT_CAP) return
        if (!hasKnife() || inv.count(wood.log) == 0 || player.fletchingLvl < wood.level) return
        invDel(inv, wood.log, 1)
        player.setBase(site, wood.id)
        player.setBaseCarved(site, 0)
        player.setSection(site, Section.LOW, wood.id)
        player.setSection(site, Section.MID, wood.id)
        player.setSection(site, Section.TOP, wood.id)
        player.setDecorations(site, 0)
        player.setDecay(site, wood.durability)
        anim(CARVE_ANIM)
        grantActionXp(wood)
        if (vars["varbit.poh_house_location"] != 0) {
            statAdvance(
                "stat.construction",
                player.constructionLvl.toDouble() * xpMods.get(player, "stat.construction"),
            )
        }
        player.runEnergy = (player.runEnergy + BUILD_ENERGY_RESTORE).coerceAtMost(10_000)
        UpdateRun.energy(player, player.runEnergy)
        spam("You build a ${wood.name.lowercase()} frame on the totem site.")
        player.timerAt(VISIT_TIMER, worldClock.cycle, VISIT_INTERVAL)
    }

    private suspend fun ProtectedAccess.carve(section: Section, wood: Wood, loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        if (player.base(site) != wood.id) return
        if (!hasKnife()) {
            mes("You need a knife to carve the totem.")
            return
        }
        player.ensureSpirits(site)
        openSkillMulti(
            SkillMultiConfig(
                actionType = SkillingActionType.CARVE,
                verb = "carve",
                entries =
                    ANIMALS.map { a ->
                        val icon =
                            if (a.index in player.activeTrio(site)) {
                                "obj.ent_totems_animal_icon_${a.suffix}"
                            } else {
                                "obj.ent_totems_animal_icon_${a.suffix}_disabled"
                            }
                        SkillMultiEntry(icon)
                    },
                maxCountProvider = { _, _ -> 1 },
                titleOverride = "Which spirit would you like to carve?",
            )
        ) { selection ->
            val animal =
                ANIMALS.firstOrNull {
                    selection.entry.internal ==
                        "obj.ent_totems_animal_icon_${it.suffix}" ||
                        selection.entry.internal ==
                            "obj.ent_totems_animal_icon_${it.suffix}_disabled"
                } ?: return@openSkillMulti
            val target = if (section == Section.BASE) player.nextUncarved(site) else section
            if (target == null || !hasKnife()) return@openSkillMulti
            anim(CARVE_ANIM)
            weakQueue(
                ACTION_QUEUE,
                ACTION_QUEUE_CYCLES,
                TotemTask.carve(site, wood.id, target, animal.index),
            )
        }
    }

    private fun ProtectedAccess.applyCarve(
        site: Int,
        woodId: Int,
        section: Section,
        animalIndex: Int,
    ) {
        if (player.base(site) != woodId || !hasKnife()) return
        if (player.sectionValue(site, section) >= CARVED_BASE) return
        val animal = ANIMALS.firstOrNull { it.index == animalIndex } ?: return
        player.setSection(site, section, CARVED_BASE + animalIndex)
        anim(CARVE_ANIM)
        WOODS.firstOrNull { it.id == woodId }?.let { grantActionXp(it) }
        spam("You carve the spirit of the ${animal.spirit} into the totem.")
        if (player.allSectionsCarved(site)) {
            player.setBaseCarved(site, 1)
            if (vars["varbit.ent_totems_carve_chat"] == 0) {
                vars["varbit.ent_totems_carve_chat"] = 1
                mes("Your totem is fully carved - you can now decorate its base.")
            }
        }
    }

    private suspend fun ProtectedAccess.decorateMenu(wood: Wood, loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        if (player.decorations(site) >= MAX_DECORATIONS) {
            mes("The totem is already fully decorated.")
            return
        }
        val held =
            decorationObjs(wood.id).filter { inv.count(it) > 0 && canFletchDecoration(it) }
        if (held.isEmpty()) {
            mes("You don't have any suitable decorations for this totem.")
            return
        }
        if (held.size == 1) {
            startDecorate(site, wood, held.first())
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                actionType = SkillingActionType.CARVE,
                verb = "attach",
                entries = held.map { SkillMultiEntry(it, listOf(Material(it))) },
                titleOverride = "Which decoration would you like to add?",
            )
        ) { selection ->
            startDecorate(site, wood, selection.entry.internal)
        }
    }

    private fun ProtectedAccess.decorateUse(wood: Wood, loc: BoundLocInfo, obj: ItemServerType) {
        val site = siteOf(loc.coords) ?: return
        if (DECORATION_WOOD_IDS[obj.id] != wood.id) {
            mes("That item isn't a suitable decoration for this totem.")
            return
        }
        val internal = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        val level = DECORATION_LEVELS[internal] ?: 0
        if (player.fletchingLvl < level) {
            mes("You need a Fletching level of $level to attach that.")
            return
        }
        startDecorate(site, wood, internal)
    }

    private fun ProtectedAccess.canFletchDecoration(internal: String): Boolean =
        player.fletchingLvl >= (DECORATION_LEVELS[internal] ?: 0)

    private fun ProtectedAccess.startDecorate(site: Int, wood: Wood, obj: String) {
        if (player.decorations(site) >= MAX_DECORATIONS || inv.count(obj) == 0) return
        anim(CARVE_ANIM)
        weakQueue(ACTION_QUEUE, ACTION_QUEUE_CYCLES, TotemTask.decorate(site, wood.id, obj))
    }

    private fun ProtectedAccess.applyDecorate(site: Int, woodId: Int, obj: String) {
        val wood = WOODS.firstOrNull { it.id == woodId } ?: return
        if (player.base(site) != woodId || player.baseCarved(site) == 0) return
        if (player.decorations(site) >= MAX_DECORATIONS || inv.count(obj) == 0) return
        if (DECORATION_WOOD_IDS[obj.asRSCM(RSCMType.OBJ)] != woodId) return
        if (!canFletchDecoration(obj)) return
        invDel(inv, obj, 1)
        player.setDecorations(site, player.decorations(site) + 1)
        anim(CARVE_ANIM)
        val wrong = wrongCarves(site)
        val xp = wood.decorXp10 / 10.0 * (1.0 - WRONG_CARVE_PENALTY * wrong)
        statAdvance("stat.fletching", xp * xpMods.get(player, "stat.fletching"))
        spam("You attach a decoration to the totem.")
        if (wrong > 0 && vars["varbit.ent_totems_decorate_chat"] == 0) {
            vars["varbit.ent_totems_decorate_chat"] = 1
            mes("The spirits seem displeased with the animals carved on this totem.")
        }
        if (player.decorations(site) < MAX_DECORATIONS) {
            val next = nextDecoration(wood, obj) ?: return
            anim(CARVE_ANIM)
            weakQueue(ACTION_QUEUE, ACTION_QUEUE_CYCLES, TotemTask.decorate(site, woodId, next))
        }
    }

    // Once a decoration type is selected it is used until exhausted, then the next
    // compatible item in inventory order is attached.
    private fun ProtectedAccess.nextDecoration(wood: Wood, current: String): String? {
        if (inv.count(current) > 0 && canFletchDecoration(current)) return current
        for (held in inv.objs) {
            if (held == null || DECORATION_WOOD_IDS[held.id] != wood.id) continue
            val internal = RSCM.getReverseMapping(RSCMType.OBJ, held.id)
            if (canFletchDecoration(internal)) return internal
        }
        return null
    }

    private fun ProtectedAccess.processAction(task: TotemTask) {
        when (task.action) {
            TotemAction.BUILD -> applyBuild(task.site, task.woodId)
            TotemAction.CARVE -> applyCarve(task.site, task.woodId, task.section, task.animalIndex)
            TotemAction.DECORATE -> applyDecorate(task.site, task.woodId, task.obj)
        }
    }

    private fun ProtectedAccess.claim(loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        val amount = player.points(site)
        if (amount <= 0) return
        if (inv.count(LOOT_OBJ) == 0 && inv.freeSpace() == 0) {
            mes("You don't have enough space in your inventory.")
            return
        }
        invAdd(inv, LOOT_OBJ, amount)
        player.setPoints(site, 0)
        spam("You collect $amount vale offerings from the pile.")
    }

    private fun ProtectedAccess.inspectOfferings(loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        val amount = player.points(site)
        mes("There ${if (amount == 1) "is" else "are"} $amount vale offerings in the pile.")
    }

    private fun ProtectedAccess.rummage(single: Boolean) {
        val held = inv.count(LOOT_OBJ)
        if (held < RUMMAGE_COST) {
            mes("You need at least $RUMMAGE_COST vale offerings to rummage through them.")
            return
        }
        if (single || held < RUMMAGE_HASTY_COST) {
            invDel(inv, LOOT_OBJ, RUMMAGE_COST)
            grantResearchPoints(1)
            rollRummageReward()
        } else {
            invDel(inv, LOOT_OBJ, RUMMAGE_HASTY_COST)
            grantResearchPoints(RUMMAGE_HASTY_COST / RUMMAGE_COST)
            repeat(RUMMAGE_HASTY_COST / RUMMAGE_COST) { rollRummageReward() }
        }
    }

    private fun ProtectedAccess.grantResearchPoints(amount: Int) {
        vars["varp.ent_totems_research_points"] =
            vars["varp.ent_totems_research_points"] + amount
    }

    private fun ProtectedAccess.rollRummageReward() {
        val roll = Random.nextInt(1000)
        when {
            roll < 5 -> reward("obj.bowstring_spool", 1)
            roll < 8 -> reward("obj.fletching_knife", 1)
            roll < 10 -> reward("obj.greenman_mask", 1)
            else -> {
                val tableRoll = Random.nextInt(RUMMAGE_TOTAL)
                var acc = 0
                for (drop in RUMMAGE_TABLE) {
                    acc += drop.weight
                    if (tableRoll < acc) {
                        applyRummageDrop(drop)
                        return
                    }
                }
            }
        }
    }

    private fun ProtectedAccess.applyRummageDrop(drop: RummageDrop) {
        when (drop.obj) {
            SEEDS -> rollSeedReward()
            in NEST_OBJS -> reward(drop.obj, nestQuantity())
            else -> reward(drop.obj, Random.nextInt(drop.lo, drop.hi + 1))
        }
    }

    // One nest drops below 80 Fletching; two at 80 and above.
    private fun ProtectedAccess.nestQuantity(): Int = if (player.fletchingLvl >= 80) 2 else 1

    private fun ProtectedAccess.rollSeedReward() {
        val tier =
            when {
                player.fletchingLvl >= 90 -> SEEDS_90
                player.fletchingLvl >= 80 -> SEEDS_80
                player.fletchingLvl >= 65 -> SEEDS_65
                player.fletchingLvl >= 50 -> SEEDS_50
                else -> SEEDS_0
            }
        val roll = Random.nextInt(tier.sumOf { it.weight })
        var acc = 0
        for (drop in tier) {
            acc += drop.weight
            if (roll < acc) {
                reward(drop.obj, Random.nextInt(drop.lo, drop.hi + 1))
                return
            }
        }
    }

    private fun ProtectedAccess.reward(obj: String, count: Int) {
        invAddOrDrop(objRepo, obj, count)
        val name = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj
        spam("You find ${if (count > 1) "$count x" else "a"} $name while rummaging!")
    }

    private fun ProtectedAccess.inspectLoot() {
        val held = inv.count(LOOT_OBJ)
        mes(
            "You are carrying $held vale offerings. " +
                "That's enough for ${held / RUMMAGE_COST} rummages."
        )
    }

    private fun ProtectedAccess.talkToRanulph() {
        if (vars["varbit.ent_totems_intro"] < INTRO_DONE) {
            vars["varbit.ent_totems_intro"] = INTRO_DONE
            mes(
                "Ranulph explains how the ents visit the totem sites and leave " +
                    "offerings for those who honour the spirits."
            )
        } else {
            mes("Ranulph welcomes you back to the Auburnvale research site.")
        }
    }

    private fun ProtectedAccess.openExchange() {
        shops.open(
            player = player,
            title = "Vale Research Exchange",
            shopInv = "inv.deadmanlose_dummy",
            buyPercentage = SHOP_BUY_PERCENT,
            sellPercentage = SHOP_SELL_PERCENT,
            changePercentage = 0.0,
            currency = RESEARCH_CURRENCY,
        )
    }

    private fun ProtectedAccess.admire(loc: BoundLocInfo) {
        val entNearby =
            npcRepo.findAll(ZoneKey.from(loc.coords), zoneRadius = 2).any { npc ->
                ENT_NPCS.any { npc.isType(it) }
            }
        if (entNearby) {
            player.runEnergy = (player.runEnergy + ADMIRE_ENERGY_RESTORE).coerceAtMost(10_000)
            UpdateRun.energy(player, player.runEnergy)
        }
        spam("You admire the beautiful scenery and feel rested.")
    }

    private suspend fun ProtectedAccess.visitCycle() {
        for (site in SITES) {
            player.rollTrio(site)
        }
        val admired = mutableListOf<Int>()
        for (site in SITES) {
            if (player.base(site) == 0 || !player.allSectionsCarved(site)) continue
            player.setDecay(site, player.decay(site) - 1)
            admired += site
        }
        if (admired.isNotEmpty()) {
            player.queue(REWARD_QUEUE, ADMIRE_TICKS, admired.toList())
        }
        spawnTrail()
        player.timerAt(VISIT_TIMER, worldClock.cycle, VISIT_INTERVAL)
    }

    // The ent admires a totem for 14 ticks before leaving offerings; decorations
    // attached during that window still count towards the visit's payout.
    private fun ProtectedAccess.grantOfferings(site: Int) {
        val wood = WOODS.firstOrNull { it.id == player.base(site) } ?: return
        if (player.base(site) == 0 || !player.allSectionsCarved(site)) return
        val correct = player.correctCarves(site)
        val decors = player.decorations(site)
        var amount =
            ceil(wood.visitUnit10 * (1 + decors) / 10.0) *
                (1.0 - WRONG_CARVE_PENALTY * (SECTIONS.size - correct))
        if (vars["varbit.ent_totems_site_${site}_trail_buff_active"] != 0) {
            amount *= 2
            vars["varbit.ent_totems_site_${site}_trail_buff_active"] = 0
            player.syncBuffsVisible()
        }
        val gained = amount.roundToInt()
        if (gained > 0) {
            player.setPoints(site, (player.points(site) + gained).coerceAtMost(POINTS_MAX))
        }
        if (player.decay(site) <= 0) {
            collapse(site)
            spam("Your totem has fallen apart.")
        }
    }

    private fun ProtectedAccess.collapse(site: Int) {
        player.setBase(site, 0)
        player.setBaseCarved(site, 0)
        player.setSection(site, Section.LOW, 0)
        player.setSection(site, Section.MID, 0)
        player.setSection(site, Section.TOP, 0)
        player.setDecorations(site, 0)
        player.setDecay(site, 0)
        if (vars["varbit.ent_totems_broken_chat"] == 0) {
            vars["varbit.ent_totems_broken_chat"] = 1
            mes(
                "Ranulph tells you that totems lose a leaf of durability " +
                    "each time an ent visits."
            )
        }
    }

    private fun Player.rollTrio(site: Int) {
        val trio = (0 until 5).shuffled().take(3)
        trio.forEachIndexed { i, idx ->
            setVar("varbit.ent_totems_site_${site}_animal_${i + 1}", idx + 1)
        }
        for (animal in ANIMALS) {
            setVar(
                "varbit.ent_totems_site_${site}_multianimal_${animal.suffix}_1",
                if (animal.index in trio) 1 else 0,
            )
        }
    }

    private fun Player.ensureSpirits(site: Int) {
        if (vars["varbit.ent_totems_site_${site}_all_multianimals"] == 0) {
            rollTrio(site)
        }
    }

    private fun Player.ensureSpirits() {
        for (site in SITES) {
            ensureSpirits(site)
        }
    }

    private fun Player.activeTrio(site: Int): Set<Int> {
        val mask = vars["varbit.ent_totems_site_${site}_all_multianimals"]
        return (0 until 5).filter { mask and (1 shl it) != 0 }.toSet()
    }

    private fun Player.correctCarves(site: Int): Int {
        val trio = activeTrio(site)
        return SECTIONS.count {
            val v = sectionValue(site, it)
            v >= CARVED_BASE && (v - CARVED_BASE) in trio
        }
    }

    private fun ProtectedAccess.wrongCarves(site: Int): Int {
        val trio = player.activeTrio(site)
        return SECTIONS.count {
            val v = player.sectionValue(site, it)
            v >= CARVED_BASE && (v - CARVED_BASE) !in trio
        }
    }

    private fun Player.nextUncarved(site: Int): Section? =
        SECTIONS.firstOrNull { sectionValue(site, it) < CARVED_BASE }

    private fun Player.allSectionsCarved(site: Int): Boolean =
        SECTIONS.all { sectionValue(site, it) >= CARVED_BASE }

    private fun ProtectedAccess.hasKnife(): Boolean =
        inv.count("obj.knife") > 0 || inv.count("obj.fletching_knife") > 0

    private fun decorationObjs(woodId: Int): List<String> =
        DECORATION_WOOD_IDS
            .filterValues { it == woodId }
            .keys
            .map { RSCM.getReverseMapping(RSCMType.OBJ, it) }

    private fun Player.syncBuffsVisible() {
        val anyBuff =
            SITES.any { vars["varbit.ent_totems_site_${it}_trail_buff_active"] != 0 } ||
                vars["varbit.ent_totems_trail_xp_buff_actions"] > 0
        setVar("varbit.ent_totems_buffs_visible", if (anyBuff) 1 else 0)
    }

    private fun ProtectedAccess.grantActionXp(wood: Wood) {
        var xp = wood.actionXp10 / 10.0
        val buffActions = vars["varbit.ent_totems_trail_xp_buff_actions"]
        if (buffActions > 0) {
            xp *= TRAIL_XP_BONUS
            vars["varbit.ent_totems_trail_xp_buff_actions"] = buffActions - 1
            player.syncBuffsVisible()
        }
        statAdvance("stat.fletching", xp * xpMods.get(player, "stat.fletching"))
    }

    private fun ProtectedAccess.spawnTrail() {
        val expired =
            activeTrails.entries.filter { (_, trail) ->
                worldClock.cycle - trail.spawnCycle > TRAIL_LIFETIME
            }
        for ((site, trail) in expired) {
            trail.locs.forEach { locRepo.del(it, Int.MAX_VALUE) }
            activeTrails.remove(site)
        }
        if (activeTrails.isNotEmpty() || Random.nextInt(TRAIL_CHANCE) != 0) return
        val site = SITES.random()
        val origin = SITE_SPIRITS.getValue(site)
        val first = origin.translate(Random.nextInt(-4, 5), Random.nextInt(-4, 5))
        val second = first.translate(Random.nextInt(1, 3), Random.nextInt(1, 3))
        val locA =
            locRepo.add(
                first,
                LOC_TRAIL_0,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        val locB =
            locRepo.add(
                second,
                LOC_TRAIL_1,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        activeTrails[site] = Trail(site, first, second, listOf(locA, locB), worldClock.cycle)
    }

    private fun checkTrailStep(player: Player) {
        val (site, trail) =
            activeTrails.entries
                .firstOrNull { (_, t) -> player.coords == t.part0 || player.coords == t.part1 }
                ?: return
        val stepped = player.attr.getOrDefault(TRAIL_STEPPED_ATTR, mutableSetOf())
        stepped += if (player.coords == trail.part0) 0 else 1
        player.attr[TRAIL_STEPPED_ATTR] = stepped
        if (stepped.containsAll(setOf(0, 1))) {
            stepped.clear()
            player.setVar("varbit.ent_totems_site_${site}_trail_buff_active", 1)
            player.setVar("varbit.ent_totems_trail_xp_buff_actions", TRAIL_XP_ACTIONS)
            player.setVar("varbit.ent_totems_buffs_visible", 1)
            trail.locs.forEach { locRepo.del(it, Int.MAX_VALUE) }
            activeTrails.remove(site)
            player.spam("You follow the ent's trail - your next totem offerings will be doubled!")
        }
    }

    private var worldSpawned = false

    private fun ensureWorldSpawned() {
        if (worldSpawned) return
        worldSpawned = true
        spawnAmbient(RANULPH, "npc.ent_totems_intro_ranulph")
        spawnAmbient(ISADORA, "npc.ent_totems_intro_child")
        for (site in SITES) {
            val spiritTile = SITE_SPIRITS.getValue(site)
            spawnAmbient(spiritTile, "npc.ent_totems_ent_destination_$site")
            spawnAmbient(spiritTile, "npc.ent_totems_site_icon")
            for (animal in ANIMALS) {
                spawnAmbient(
                    spiritTile.translate(animal.index - 2, 1),
                    "npc.ent_totems_site_${site}_animal_${animal.suffix}_1",
                )
            }
        }
    }

    private fun spawnAmbient(coords: CoordGrid, internal: String) {
        val npc = Npc(internal, coords)
        npc.mode = NpcMode.Wander
        npc.moveRestrict = MoveRestrict.Normal
        npcRepo.add(npc, Int.MAX_VALUE)
    }

    private fun siteOf(coords: CoordGrid): Int? = SITE_TILES[coords.x to coords.z]

    private fun Player.base(site: Int): Int = vars["varbit.ent_totems_site_${site}_base"]

    private fun Player.setVar(internal: String, value: Int) {
        VarPlayerIntMapSetter.set(this, internal, value)
    }

    private fun Player.setBase(site: Int, v: Int) {
        setVar("varbit.ent_totems_site_${site}_base", v)
    }

    private fun Player.baseCarved(site: Int): Int =
        vars["varbit.ent_totems_site_${site}_base_carved"]

    private fun Player.setBaseCarved(site: Int, v: Int) {
        setVar("varbit.ent_totems_site_${site}_base_carved", v)
    }

    private fun Player.sectionValue(site: Int, section: Section): Int =
        vars["varbit.ent_totems_site_${site}_${section.suffix}"]

    private fun Player.setSection(site: Int, section: Section, v: Int) {
        if (section == Section.BASE) {
            setBase(site, v)
        } else {
            setVar("varbit.ent_totems_site_${site}_${section.suffix}", v)
        }
    }

    private fun Player.decorations(site: Int): Int =
        vars["varbit.ent_totems_site_${site}_decorations"]

    private fun Player.setDecorations(site: Int, v: Int) {
        setVar("varbit.ent_totems_site_${site}_decorations", v)
    }

    private fun Player.decay(site: Int): Int = vars["varbit.ent_totems_site_${site}_decay"]

    private fun Player.setDecay(site: Int, v: Int) {
        setVar("varbit.ent_totems_site_${site}_decay", v)
    }

    private fun Player.points(site: Int): Int = vars["varbit.ent_totems_site_${site}_points"]

    private fun Player.setPoints(site: Int, v: Int) {
        setVar("varbit.ent_totems_site_${site}_points", v)
    }

    private val activeTrails = mutableMapOf<Int, Trail>()

    private data class Trail(
        val site: Int,
        val part0: CoordGrid,
        val part1: CoordGrid,
        val locs: List<LocInfo>,
        val spawnCycle: Int,
    )

    private enum class Section(val suffix: String) {
        BASE("base"),
        LOW("low"),
        MID("mid"),
        TOP("top"),
    }

    private enum class Animal(val index: Int, val suffix: Char, val spirit: String) {
        A(0, 'a', "buffalo"),
        B(1, 'b', "jaguar"),
        C(2, 'c', "eagle"),
        D(3, 'd', "snake"),
        E(4, 'e', "scorpion"),
    }

    private enum class TotemAction {
        BUILD,
        CARVE,
        DECORATE,
    }

    private data class TotemTask(
        val action: TotemAction,
        val site: Int,
        val woodId: Int,
        val section: Section = Section.BASE,
        val animalIndex: Int = 0,
        val obj: String = "",
    ) {
        companion object {
            fun build(site: Int, woodId: Int) = TotemTask(TotemAction.BUILD, site, woodId)

            fun carve(site: Int, woodId: Int, section: Section, animalIndex: Int) =
                TotemTask(TotemAction.CARVE, site, woodId, section, animalIndex)

            fun decorate(site: Int, woodId: Int, obj: String) =
                TotemTask(TotemAction.DECORATE, site, woodId, obj = obj)
        }
    }

    private data class Wood(
        val id: Int,
        val key: String,
        val name: String,
        val level: Int,
        val actionXp10: Int,
        val decorXp10: Int,
        val durability: Int,
        val offerings: Int,
        val log: String,
        val visitUnit10: Int,
    )

    private companion object {
        const val LOC_BASE_NONE = "loc.ent_totems_base_none"
        val OFFERING_LOCS =
            listOf(
                "loc.ent_totems_offerings_a",
                "loc.ent_totems_offerings_b",
                "loc.ent_totems_offerings_c",
                "loc.ent_totems_offerings_d",
            )
        val ADMIRE_LOCS =
            listOf(
                "loc.ent_totems_admire_loc_1",
                "loc.ent_totems_admire_loc_2",
                "loc.ent_totems_admire_loc_3",
            )
        const val LOC_TRAIL_0 = "loc.ent_totems_trail_part_0"
        const val LOC_TRAIL_1 = "loc.ent_totems_trail_part_1"
        const val LOOT_OBJ = "obj.ent_totems_loot"
        const val CARVE_ANIM = "seq.brut_human_knifeuse"
        const val VISIT_TIMER = "timer.ent_totems_visit"
        const val RESEARCH_CURRENCY = "currency.vale_research_points"
        const val ACTION_QUEUE = "queue.ent_totems_action"
        const val REWARD_QUEUE = "queue.ent_totems_visit_reward"

        const val VISIT_INTERVAL = 80
        const val INTRO_DONE = 127
        const val CARVED_BASE = 10
        const val MAX_DECORATIONS = 4
        const val OFFERINGS_SOFT_CAP = 15_000
        const val POINTS_MAX = 16_383
        const val RUMMAGE_COST = 100
        const val RUMMAGE_HASTY_COST = 500
        const val WRONG_CARVE_PENALTY = 0.25

        // Build/carve/decorate take two game ticks; queues scheduled mid-processing
        // decrement once on the same tick, so an extra cycle is queued.
        const val ACTION_QUEUE_CYCLES = 3

        // Ents admire a totem for 14 ticks before leaving offerings.
        const val ADMIRE_TICKS = 14
        const val TRAIL_XP_BONUS = 1.15
        const val TRAIL_XP_ACTIONS = 4
        const val TRAIL_LIFETIME = 200
        const val TRAIL_CHANCE = 3
        const val ADMIRE_ENERGY_RESTORE = 2_000
        const val BUILD_ENERGY_RESTORE = 1_500
        const val SHOP_SELL_PERCENT = 100.0
        const val SHOP_BUY_PERCENT = 80.0

        val RANULPH = CoordGrid(1395, 3353, 0)
        val ISADORA = CoordGrid(1397, 3353, 0)

        val SITES = (1..8).toList()

        val ENT_NPCS = SITES.map { "npc.ent_totems_ent_destination_$it" }

        val SITE_TILES: Map<Pair<Int, Int>, Int> by lazy {
            buildMap {
                for (row in ValeTotemsSitesRow.all()) {
                    put(row.totem.x to row.totem.z, row.key)
                    put(row.offerings.x to row.offerings.z, row.key)
                }
            }
        }

        val SITE_SPIRITS: Map<Int, CoordGrid> by lazy {
            ValeTotemsSitesRow.all().associate { it.key to it.spirit }
        }

        val SECTIONS = listOf(Section.LOW, Section.MID, Section.TOP)

        val ANIMALS = Animal.entries.toList()

        private val WOOD_KEYS = listOf("oak", "willow", "maple", "yew", "magic", "redwood")

        val WOODS: List<Wood> by lazy {
            ValeTotemsWoodsRow.all().map { row ->
                Wood(
                    id = row.key,
                    key = WOOD_KEYS[row.key - 1],
                    name = row.name,
                    level = row.level,
                    actionXp10 = row.actionXp,
                    decorXp10 = row.decorXp,
                    durability = row.durability,
                    offerings = row.offerings,
                    log = row.log.internalName,
                    visitUnit10 = row.visitUnit,
                )
            }
        }

        val DECORATION_LEVELS: Map<String, Int> by lazy {
            buildMap {
                for (row in FletchingRecipesRow.all()) {
                    val level = row.statReq.firstOrNull()?.t1 ?: continue
                    put(row.output.internalName, level)
                }
            }
        }

        val SHOP_PRICES: Map<Int, Int> by lazy {
            mapOf(
                "obj.bowstring_spool".asRSCM(RSCMType.OBJ) to 250,
                "obj.fletching_knife".asRSCM(RSCMType.OBJ) to 350,
                "obj.greenman_mask".asRSCM(RSCMType.OBJ) to 500,
                "obj.ent_branch".asRSCM(RSCMType.OBJ) to 20,
            )
        }

        val DECORATION_WOOD_IDS: Map<Int, Int> by lazy {
            val enum =
                ServerCacheManager.getEnum(
                    "enum.vale_totems_decoration_wood".asRSCM(RSCMType.ENUM),
                ) ?: error("vale_totems_decoration_wood enum missing")
            enum.values.mapKeys { (k, _) -> k as Int }.mapValues { (_, v) -> v as Int }
        }

        // The wiki's rummage rates are per 399; weights here are x10 so the
        // fractional entries (7.2, 32.5, 0.5) stay exact integers.
        const val RUMMAGE_TOTAL = 3990

        val RUMMAGE_TABLE =
            listOf(
                RummageDrop("obj.dirty_arrowtips", 26, 32, 800),
                RummageDrop("obj.ent_branch", 1, 1, 650),
                RummageDrop("obj.flax_bale", 5, 6, 500),
                RummageDrop("obj.blessed_bone_shard", 70, 90, 350),
                RummageDrop("obj.forestry_currency", 70, 90, 250),
                RummageDrop("obj.feather", 400, 500, 150),
                RummageDrop("obj.magic_roots", 2, 3, 72),
                RummageDrop("obj.yew_roots", 3, 4, 72),
                RummageDrop("obj.maple_roots", 3, 4, 48),
                RummageDrop("obj.willow_roots", 3, 4, 48),
                RummageDrop("obj.bird_nest_seeds", 0, 0, 325),
                RummageDrop("obj.bird_nest_ring", 0, 0, 160),
                RummageDrop("obj.bird_nest_egg_red", 0, 0, 5),
                RummageDrop("obj.bird_nest_egg_green", 0, 0, 5),
                RummageDrop("obj.bird_nest_egg_blue", 0, 0, 5),
                RummageDrop(SEEDS, 0, 0, 550),
            )

        const val SEEDS = "seeds"

        val NEST_OBJS =
            setOf(
                "obj.bird_nest_seeds",
                "obj.bird_nest_ring",
                "obj.bird_nest_egg_red",
                "obj.bird_nest_egg_green",
                "obj.bird_nest_egg_blue",
            )

        // Seed sub-tables per Fletching-level tier; weights are per 1000 within the
        // 55/399 seed roll, scaled from the wiki's empirical drop rates.
        val SEEDS_0 =
            listOf(
                RummageDrop("obj.cadavaberry_bush_seed", 7, 11, 251),
                RummageDrop("obj.apple_tree_seed", 1, 2, 197),
                RummageDrop("obj.watermelon_seed", 5, 8, 162),
                RummageDrop("obj.limpwurt_seed", 3, 6, 150),
                RummageDrop("obj.pineapple_tree_seed", 1, 2, 99),
                RummageDrop("obj.acorn", 3, 6, 99),
                RummageDrop("obj.papaya_tree_seed", 1, 2, 25),
                RummageDrop("obj.willow_seed", 1, 2, 14),
                RummageDrop("obj.maple_seed", 1, 2, 2),
                RummageDrop("obj.palm_tree_seed", 1, 2, 1),
            )

        val SEEDS_50 =
            listOf(
                RummageDrop("obj.cadavaberry_bush_seed", 7, 11, 197),
                RummageDrop("obj.apple_tree_seed", 1, 2, 160),
                RummageDrop("obj.limpwurt_seed", 3, 6, 133),
                RummageDrop("obj.watermelon_seed", 5, 8, 132),
                RummageDrop("obj.acorn", 3, 6, 89),
                RummageDrop("obj.pineapple_tree_seed", 1, 2, 89),
                RummageDrop("obj.willow_seed", 1, 2, 61),
                RummageDrop("obj.papaya_tree_seed", 1, 2, 52),
                RummageDrop("obj.maple_seed", 1, 2, 36),
                RummageDrop("obj.palm_tree_seed", 1, 2, 20),
                RummageDrop("obj.yew_seed", 1, 2, 20),
                RummageDrop("obj.magic_tree_seed", 1, 2, 10),
                RummageDrop("obj.dragonfruit_tree_seed", 1, 2, 1),
            )

        val SEEDS_65 =
            listOf(
                RummageDrop("obj.willow_seed", 1, 2, 166),
                RummageDrop("obj.papaya_tree_seed", 1, 2, 117),
                RummageDrop("obj.maple_seed", 1, 2, 110),
                RummageDrop("obj.limpwurt_seed", 3, 6, 95),
                RummageDrop("obj.cadavaberry_bush_seed", 7, 11, 86),
                RummageDrop("obj.apple_tree_seed", 1, 2, 70),
                RummageDrop("obj.acorn", 3, 6, 67),
                RummageDrop("obj.pineapple_tree_seed", 1, 2, 67),
                RummageDrop("obj.palm_tree_seed", 1, 2, 65),
                RummageDrop("obj.yew_seed", 1, 2, 65),
                RummageDrop("obj.watermelon_seed", 5, 8, 58),
                RummageDrop("obj.magic_tree_seed", 1, 2, 33),
                RummageDrop("obj.dragonfruit_tree_seed", 1, 2, 1),
            )

        val SEEDS_80 =
            listOf(
                RummageDrop("obj.willow_seed", 1, 2, 238),
                RummageDrop("obj.papaya_tree_seed", 1, 2, 158),
                RummageDrop("obj.maple_seed", 1, 2, 157),
                RummageDrop("obj.palm_tree_seed", 1, 2, 95),
                RummageDrop("obj.yew_seed", 1, 2, 95),
                RummageDrop("obj.limpwurt_seed", 3, 6, 71),
                RummageDrop("obj.pineapple_tree_seed", 1, 2, 52),
                RummageDrop("obj.acorn", 3, 6, 52),
                RummageDrop("obj.magic_tree_seed", 1, 2, 47),
                RummageDrop("obj.cadavaberry_bush_seed", 7, 11, 12),
                RummageDrop("obj.apple_tree_seed", 1, 2, 12),
                RummageDrop("obj.watermelon_seed", 5, 8, 9),
                RummageDrop("obj.dragonfruit_tree_seed", 1, 2, 2),
            )

        val SEEDS_90 =
            listOf(
                RummageDrop("obj.willow_seed", 1, 2, 249),
                RummageDrop("obj.maple_seed", 1, 2, 165),
                RummageDrop("obj.papaya_tree_seed", 1, 2, 165),
                RummageDrop("obj.yew_seed", 1, 2, 100),
                RummageDrop("obj.palm_tree_seed", 1, 2, 99),
                RummageDrop("obj.limpwurt_seed", 3, 6, 67),
                RummageDrop("obj.acorn", 3, 6, 50),
                RummageDrop("obj.pineapple_tree_seed", 1, 2, 50),
                RummageDrop("obj.magic_tree_seed", 1, 2, 50),
                RummageDrop("obj.dragonfruit_tree_seed", 1, 2, 2),
                RummageDrop("obj.watermelon_seed", 5, 8, 1),
                RummageDrop("obj.apple_tree_seed", 1, 2, 1),
                RummageDrop("obj.cadavaberry_bush_seed", 7, 11, 1),
            )

        private val TRAIL_STEPPED_ATTR: AttributeKey<MutableSet<Int>> = AttributeKey(temp = true)
    }

    private data class RummageDrop(val obj: String, val lo: Int, val hi: Int, val weight: Int)
}
