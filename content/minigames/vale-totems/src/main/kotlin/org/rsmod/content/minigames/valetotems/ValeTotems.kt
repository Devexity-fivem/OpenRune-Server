package org.rsmod.content.minigames.valetotems

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
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
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ValeTotemsSitesRow
import org.rsmod.api.table.ValeTotemsWoodsRow
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
            statAdvance(
                "stat.construction",
                wood.level.toDouble() * xpMods.get(player, "stat.construction"),
            )
            player.runEnergy = (player.runEnergy + BUILD_ENERGY_RESTORE).coerceAtMost(10_000)
            UpdateRun.energy(player, player.runEnergy)
            spam("You build a ${wood.name.lowercase()} frame on the totem site.")
            player.timerAt(VISIT_TIMER, worldClock.cycle, VISIT_INTERVAL)
        }
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
            player.setSection(site, target, CARVED_BASE + animal.index)
            anim(CARVE_ANIM)
            grantActionXp(wood)
            spam("You carve the spirit of the ${animal.spirit} into the totem.")
            if (player.allSectionsCarved(site)) {
                player.setBaseCarved(site, 1)
                if (vars["varbit.ent_totems_carve_chat"] == 0) {
                    vars["varbit.ent_totems_carve_chat"] = 1
                    mes("Your totem is fully carved - you can now decorate its base.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.decorateMenu(wood: Wood, loc: BoundLocInfo) {
        val site = siteOf(loc.coords) ?: return
        if (player.decorations(site) >= MAX_DECORATIONS) {
            mes("The totem is already fully decorated.")
            return
        }
        val held = decorationObjs(wood.id).filter { inv.count(it) > 0 }
        if (held.isEmpty()) {
            mes("You don't have any suitable decorations for this totem.")
            return
        }
        if (held.size == 1) {
            decorate(site, wood, held.first())
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
            decorate(site, wood, selection.entry.internal)
        }
    }

    private fun ProtectedAccess.decorateUse(wood: Wood, loc: BoundLocInfo, obj: ItemServerType) {
        val site = siteOf(loc.coords) ?: return
        if (DECORATION_WOOD_IDS[obj.id] != wood.id) {
            mes("That item isn't a suitable decoration for this totem.")
            return
        }
        decorate(site, wood, RSCM.getReverseMapping(RSCMType.OBJ, obj.id))
    }

    private fun ProtectedAccess.decorate(site: Int, wood: Wood, obj: String) {
        if (player.decorations(site) >= MAX_DECORATIONS || inv.count(obj) == 0) return
        invDel(inv, obj, 1)
        player.setDecorations(site, player.decorations(site) + 1)
        anim(CARVE_ANIM)
        val wrong = wrongCarves(site)
        var xp = wood.decorXp10 / 10.0
        repeat(wrong) { xp *= WRONG_CARVE_XP_FACTOR }
        statAdvance("stat.fletching", xp * xpMods.get(player, "stat.fletching"))
        spam("You attach a decoration to the totem.")
        if (wrong > 0 && vars["varbit.ent_totems_decorate_chat"] == 0) {
            vars["varbit.ent_totems_decorate_chat"] = 1
            mes("The spirits seem displeased with the animals carved on this totem.")
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
                val tableRoll = Random.nextInt(399)
                var acc = 0
                for (drop in RUMMAGE_TABLE) {
                    acc += drop.weight
                    if (tableRoll < acc) {
                        reward(drop.obj, Random.nextInt(drop.lo, drop.hi + 1))
                        return
                    }
                }
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
        for (site in SITES) {
            if (player.base(site) == 0 || !player.allSectionsCarved(site)) continue
            val decay = player.decay(site) - 1
            if (decay <= 0) {
                collapse(site)
                spam("Your totem has fallen apart.")
            } else {
                player.setDecay(site, decay)
                grantOfferings(site)
            }
        }
        spawnTrail()
        player.timerAt(VISIT_TIMER, worldClock.cycle, VISIT_INTERVAL)
    }

    private fun ProtectedAccess.grantOfferings(site: Int) {
        val wood = WOODS.firstOrNull { it.id == player.base(site) } ?: return
        val correct = player.correctCarves(site)
        val decors = player.decorations(site)
        var amount = wood.offerings * (1 + correct) * (1 + decors) / 20.0
        if (vars["varbit.ent_totems_site_${site}_trail_buff_active"] != 0) {
            amount *= 2
            vars["varbit.ent_totems_site_${site}_trail_buff_active"] = 0
            player.syncBuffsVisible()
        }
        val gained = amount.roundToInt()
        if (gained > 0) {
            player.setPoints(site, (player.points(site) + gained).coerceAtMost(POINTS_MAX))
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

        const val VISIT_INTERVAL = 80
        const val INTRO_DONE = 127
        const val CARVED_BASE = 10
        const val MAX_DECORATIONS = 4
        const val OFFERINGS_SOFT_CAP = 15_000
        const val POINTS_MAX = 16_383
        const val RUMMAGE_COST = 100
        const val RUMMAGE_HASTY_COST = 500
        const val WRONG_CARVE_XP_FACTOR = 0.75
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
                )
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

        val RUMMAGE_TABLE =
            listOf(
                RummageDrop("obj.dirty_arrowtips", 26, 32, 80),
                RummageDrop("obj.ent_branch", 1, 1, 65),
                RummageDrop("obj.flax", 5, 6, 50),
                RummageDrop("obj.blessed_bone_shard", 70, 90, 35),
                RummageDrop("obj.forestry_currency", 70, 90, 25),
                RummageDrop("obj.feather", 400, 500, 15),
                RummageDrop("obj.magic_roots", 2, 3, 30),
                RummageDrop("obj.yew_roots", 3, 4, 30),
                RummageDrop("obj.maple_roots", 3, 4, 20),
                RummageDrop("obj.willow_roots", 3, 4, 20),
            )

        private val TRAIL_STEPPED_ATTR: AttributeKey<MutableSet<Int>> = AttributeKey(temp = true)
    }

    private data class RummageDrop(val obj: String, val lo: Int, val hi: Int, val weight: Int)
}
