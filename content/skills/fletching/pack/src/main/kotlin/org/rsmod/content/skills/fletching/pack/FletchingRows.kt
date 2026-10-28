package org.rsmod.content.skills.fletching.pack

import dev.openrune.tables.production.ProductionTableScope
import org.rsmod.content.skills.fletching.pack.Fletching.KIND_ASSEMBLE
import org.rsmod.content.skills.fletching.pack.Fletching.KIND_ATTACH
import org.rsmod.content.skills.fletching.pack.Fletching.KIND_CARVE
import org.rsmod.content.skills.fletching.pack.Fletching.KIND_CHISEL
import org.rsmod.content.skills.fletching.pack.Fletching.KIND_STRING

// xp values are in tenths (5 xp = 50). For KIND_ATTACH/KIND_CHISEL rows xp is awarded per
// output item; for all other kinds it is awarded once per completed craft.
internal fun ProductionTableScope.fletchingRows() {
    carveRows()
    stringRows()
    assembleRows()
    attachRows()
    chiselRows()
}

internal fun ProductionTableScope.fletchRow(
    name: String,
    kind: String,
    level: Int,
    xp: Int,
    inputs: List<Pair<String, Int>>,
    output: String,
    outputCount: Int = 1,
    tool: String? = null,
    anim: String = "seq.human_fletching",
    ticks: Int = 3,
    firstTicks: Int? = null,
    featherInput: Boolean = false,
) {
    row("dbrow.fletch_$name") {
        production {
            inputs.forEach { (item, amount) -> input(item, amount) }
            statReq("stat.fletching", level)
            xp(xp)
            output(output, outputCount)
        }
        column(Fletching.COL_KIND, kind)
        column(Fletching.COL_ANIM, anim)
        column(Fletching.COL_TICKS, ticks)
        firstTicks?.let { column(Fletching.COL_FIRST_TICKS, it) }
        tool?.let { columnRSCM(Fletching.COL_TOOL, it) }
        if (featherInput) column(Fletching.COL_FEATHER_INPUT, 1)
    }
}

private fun ProductionTableScope.carveRows() {
    // arrow shafts: first craft lands after 2 ticks, repeats every 3
    fletchRow("shafts_logs", KIND_CARVE, 1, 50, listOf("obj.logs" to 1), "obj.arrow_shaft", 15,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_oak", KIND_CARVE, 15, 100, listOf("obj.oak_logs" to 1), "obj.arrow_shaft", 30,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_willow", KIND_CARVE, 30, 150, listOf("obj.willow_logs" to 1), "obj.arrow_shaft", 45,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_maple", KIND_CARVE, 45, 200, listOf("obj.maple_logs" to 1), "obj.arrow_shaft", 60,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_yew", KIND_CARVE, 60, 250, listOf("obj.yew_logs" to 1), "obj.arrow_shaft", 75,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_magic", KIND_CARVE, 75, 300, listOf("obj.magic_logs" to 1), "obj.arrow_shaft", 90,
        tool = "obj.knife", firstTicks = 2)
    fletchRow("shafts_redwood", KIND_CARVE, 90, 350, listOf("obj.redwood_logs" to 1), "obj.arrow_shaft", 105,
        tool = "obj.knife", firstTicks = 2)

    fletchRow("javelin_shafts", KIND_CARVE, 3, 50, listOf("obj.logs" to 1), "obj.javelin_shaft", 15,
        tool = "obj.knife", firstTicks = 2)

    fletchRow("shortbow_u", KIND_CARVE, 5, 50, listOf("obj.logs" to 1), "obj.unstrung_shortbow", tool = "obj.knife")
    fletchRow("longbow_u", KIND_CARVE, 10, 100, listOf("obj.logs" to 1), "obj.unstrung_longbow", tool = "obj.knife")
    fletchRow("oak_shortbow_u", KIND_CARVE, 20, 165, listOf("obj.oak_logs" to 1), "obj.unstrung_oak_shortbow", tool = "obj.knife")
    fletchRow("oak_longbow_u", KIND_CARVE, 25, 250, listOf("obj.oak_logs" to 1), "obj.unstrung_oak_longbow", tool = "obj.knife")
    fletchRow("comp_ogre_bow_u", KIND_CARVE, 30, 450,
        listOf("obj.achey_tree_logs" to 1, "obj.wolf_bones" to 1), "obj.unstrung_zogre_bow",
        tool = "obj.knife", anim = "seq.human_ogre_fletching")
    fletchRow("willow_shortbow_u", KIND_CARVE, 35, 333, listOf("obj.willow_logs" to 1), "obj.unstrung_willow_shortbow", tool = "obj.knife")
    fletchRow("willow_longbow_u", KIND_CARVE, 40, 415, listOf("obj.willow_logs" to 1), "obj.unstrung_willow_longbow", tool = "obj.knife")
    fletchRow("maple_shortbow_u", KIND_CARVE, 50, 500, listOf("obj.maple_logs" to 1), "obj.unstrung_maple_shortbow", tool = "obj.knife")
    fletchRow("maple_longbow_u", KIND_CARVE, 55, 583, listOf("obj.maple_logs" to 1), "obj.unstrung_maple_longbow", tool = "obj.knife")
    fletchRow("yew_shortbow_u", KIND_CARVE, 65, 675, listOf("obj.yew_logs" to 1), "obj.unstrung_yew_shortbow", tool = "obj.knife")
    fletchRow("yew_longbow_u", KIND_CARVE, 70, 750, listOf("obj.yew_logs" to 1), "obj.unstrung_yew_longbow", tool = "obj.knife")
    fletchRow("magic_shortbow_u", KIND_CARVE, 80, 833, listOf("obj.magic_logs" to 1), "obj.unstrung_magic_shortbow", tool = "obj.knife")
    fletchRow("magic_longbow_u", KIND_CARVE, 85, 915, listOf("obj.magic_logs" to 1), "obj.unstrung_magic_longbow", tool = "obj.knife")

    fletchRow("stock_wood", KIND_CARVE, 9, 60, listOf("obj.logs" to 1), "obj.xbows_crossbow_stock_wood", tool = "obj.knife")
    fletchRow("stock_oak", KIND_CARVE, 24, 160, listOf("obj.oak_logs" to 1), "obj.xbows_crossbow_stock_oak", tool = "obj.knife")
    fletchRow("stock_willow", KIND_CARVE, 39, 220, listOf("obj.willow_logs" to 1), "obj.xbows_crossbow_stock_willow", tool = "obj.knife")
    fletchRow("stock_teak", KIND_CARVE, 46, 270, listOf("obj.teak_logs" to 1), "obj.xbows_crossbow_stock_teak", tool = "obj.knife")
    fletchRow("stock_maple", KIND_CARVE, 54, 320, listOf("obj.maple_logs" to 1), "obj.xbows_crossbow_stock_maple", tool = "obj.knife")
    fletchRow("stock_mahogany", KIND_CARVE, 61, 410, listOf("obj.mahogany_logs" to 1), "obj.xbows_crossbow_stock_mahogany", tool = "obj.knife")
    fletchRow("stock_yew", KIND_CARVE, 69, 500, listOf("obj.yew_logs" to 1), "obj.xbows_crossbow_stock_yew", tool = "obj.knife")
    fletchRow("stock_magic", KIND_CARVE, 78, 700, listOf("obj.magic_logs" to 1), "obj.xbows_crossbow_stock_magic", tool = "obj.knife")

    fletchRow("shield_oak", KIND_CARVE, 27, 500, listOf("obj.oak_logs" to 2), "obj.oak_shield", tool = "obj.knife")
    fletchRow("shield_willow", KIND_CARVE, 42, 830, listOf("obj.willow_logs" to 2), "obj.willow_shield", tool = "obj.knife")
    fletchRow("shield_maple", KIND_CARVE, 57, 1165, listOf("obj.maple_logs" to 2), "obj.maple_shield", tool = "obj.knife")
    fletchRow("shield_yew", KIND_CARVE, 72, 1500, listOf("obj.yew_logs" to 2), "obj.yew_shield", tool = "obj.knife")
    fletchRow("shield_magic", KIND_CARVE, 87, 1830, listOf("obj.magic_logs" to 2), "obj.magic_shield", tool = "obj.knife")
    fletchRow("shield_redwood", KIND_CARVE, 92, 2160, listOf("obj.redwood_logs" to 2), "obj.redwood_shield", tool = "obj.knife")

    fletchRow("battlestaff", KIND_CARVE, 40, 800, listOf("obj.celastrus_wood" to 1), "obj.battlestaff", tool = "obj.knife")
    fletchRow("redwood_hiking_staff", KIND_CARVE, 90, 105, listOf("obj.redwood_logs" to 1), "obj.redwood_hiking_staff", tool = "obj.knife")
    fletchRow("ogre_shafts", KIND_CARVE, 5, 64, listOf("obj.achey_tree_logs" to 1), "obj.ogre_arrow_shaft", 4,
        tool = "obj.knife", anim = "seq.human_ogre_fletching")
    fletchRow("hunters_spear", KIND_CARVE, 60, 95,
        listOf("obj.teak_logs" to 1, "obj.hunting_jerboa_tail" to 5, "obj.hg_speartip" to 1),
        "obj.hg_hunter_spear", 5, tool = "obj.knife")
    fletchRow("blowpipe_camphor", KIND_CARVE, 58, 1400,
        listOf("obj.camphor_logs" to 2, "obj.squid_beak" to 1), "obj.camphor_blowpipe", tool = "obj.knife")
    fletchRow("blowpipe_ironwood", KIND_CARVE, 72, 1700,
        listOf("obj.ironwood_logs" to 2, "obj.squid_beak" to 1), "obj.ironwood_blowpipe", tool = "obj.knife")
    fletchRow("blowpipe_rosewood", KIND_CARVE, 84, 2000,
        listOf("obj.rosewood_logs" to 2, "obj.squid_beak" to 1), "obj.rosewood_blowpipe", tool = "obj.knife")
}

private fun ProductionTableScope.stringRows() {
    val s = "obj.bow_string"
    fletchRow("string_shortbow", KIND_STRING, 5, 50, listOf("obj.unstrung_shortbow" to 1, s to 1), "obj.shortbow")
    fletchRow("string_longbow", KIND_STRING, 10, 100, listOf("obj.unstrung_longbow" to 1, s to 1), "obj.longbow")
    fletchRow("string_oak_shortbow", KIND_STRING, 20, 165, listOf("obj.unstrung_oak_shortbow" to 1, s to 1), "obj.oak_shortbow")
    fletchRow("string_oak_longbow", KIND_STRING, 25, 250, listOf("obj.unstrung_oak_longbow" to 1, s to 1), "obj.oak_longbow")
    fletchRow("string_zogre_bow", KIND_STRING, 30, 450, listOf("obj.unstrung_zogre_bow" to 1, s to 1), "obj.zogre_bow",
        anim = "seq.human_ogre_fletching")
    fletchRow("string_willow_shortbow", KIND_STRING, 35, 333, listOf("obj.unstrung_willow_shortbow" to 1, s to 1), "obj.willow_shortbow")
    fletchRow("string_willow_longbow", KIND_STRING, 40, 415, listOf("obj.unstrung_willow_longbow" to 1, s to 1), "obj.willow_longbow")
    fletchRow("string_maple_shortbow", KIND_STRING, 50, 500, listOf("obj.unstrung_maple_shortbow" to 1, s to 1), "obj.maple_shortbow")
    fletchRow("string_maple_longbow", KIND_STRING, 55, 583, listOf("obj.unstrung_maple_longbow" to 1, s to 1), "obj.maple_longbow")
    fletchRow("string_yew_shortbow", KIND_STRING, 65, 675, listOf("obj.unstrung_yew_shortbow" to 1, s to 1), "obj.yew_shortbow")
    fletchRow("string_yew_longbow", KIND_STRING, 70, 750, listOf("obj.unstrung_yew_longbow" to 1, s to 1), "obj.yew_longbow")
    fletchRow("string_magic_shortbow", KIND_STRING, 80, 833, listOf("obj.unstrung_magic_shortbow" to 1, s to 1), "obj.magic_shortbow")
    fletchRow("string_magic_longbow", KIND_STRING, 85, 915, listOf("obj.unstrung_magic_longbow" to 1, s to 1), "obj.magic_longbow")

    val cs = "obj.xbows_crossbow_string"
    fletchRow("string_xbow_bronze", KIND_STRING, 9, 60, listOf("obj.xbows_crossbow_unstrung_bronze" to 1, cs to 1), "obj.xbows_crossbow_bronze")
    fletchRow("string_xbow_blurite", KIND_STRING, 24, 160, listOf("obj.xbows_crossbow_unstrung_blurite" to 1, cs to 1), "obj.xbows_crossbow_blurite")
    fletchRow("string_xbow_iron", KIND_STRING, 39, 220, listOf("obj.xbows_crossbow_unstrung_iron" to 1, cs to 1), "obj.xbows_crossbow_iron")
    fletchRow("string_xbow_steel", KIND_STRING, 46, 270, listOf("obj.xbows_crossbow_unstrung_steel" to 1, cs to 1), "obj.xbows_crossbow_steel")
    fletchRow("string_xbow_mithril", KIND_STRING, 54, 320, listOf("obj.xbows_crossbow_unstrung_mithril" to 1, cs to 1), "obj.xbows_crossbow_mithril")
    fletchRow("string_xbow_adamant", KIND_STRING, 61, 410, listOf("obj.xbows_crossbow_unstrung_adamantite" to 1, cs to 1), "obj.xbows_crossbow_adamantite")
    fletchRow("string_xbow_runite", KIND_STRING, 69, 500, listOf("obj.xbows_crossbow_unstrung_runite" to 1, cs to 1), "obj.xbows_crossbow_runite")
    fletchRow("string_xbow_dragon", KIND_STRING, 78, 700, listOf("obj.xbows_crossbow_unstrung_dragon" to 1, cs to 1), "obj.xbows_crossbow_dragon")

    fletchRow("string_ballista_light", KIND_STRING, 47, 3000,
        listOf("obj.ballista_unstrung_light" to 1, "obj.ballista_rope" to 1), "obj.light_ballista")
    fletchRow("string_ballista_heavy", KIND_STRING, 72, 6000,
        listOf("obj.ballista_unstrung_heavy" to 1, "obj.ballista_rope" to 1), "obj.heavy_ballista")
}

private fun ProductionTableScope.assembleRows() {
    val h = "obj.hammer"
    fletchRow("xbow_bronze", KIND_ASSEMBLE, 9, 120,
        listOf("obj.xbows_crossbow_stock_wood" to 1, "obj.xbows_crossbow_limbs_bronze" to 1),
        "obj.xbows_crossbow_unstrung_bronze", tool = h)
    fletchRow("xbow_blurite", KIND_ASSEMBLE, 24, 320,
        listOf("obj.xbows_crossbow_stock_oak" to 1, "obj.xbows_crossbow_limbs_blurite" to 1),
        "obj.xbows_crossbow_unstrung_blurite", tool = h)
    fletchRow("xbow_iron", KIND_ASSEMBLE, 39, 440,
        listOf("obj.xbows_crossbow_stock_willow" to 1, "obj.xbows_crossbow_limbs_iron" to 1),
        "obj.xbows_crossbow_unstrung_iron", tool = h)
    fletchRow("xbow_steel", KIND_ASSEMBLE, 46, 540,
        listOf("obj.xbows_crossbow_stock_teak" to 1, "obj.xbows_crossbow_limbs_steel" to 1),
        "obj.xbows_crossbow_unstrung_steel", tool = h)
    fletchRow("xbow_mithril", KIND_ASSEMBLE, 54, 640,
        listOf("obj.xbows_crossbow_stock_maple" to 1, "obj.xbows_crossbow_limbs_mithril" to 1),
        "obj.xbows_crossbow_unstrung_mithril", tool = h)
    fletchRow("xbow_adamant", KIND_ASSEMBLE, 61, 820,
        listOf("obj.xbows_crossbow_stock_mahogany" to 1, "obj.xbows_crossbow_limbs_adamantite" to 1),
        "obj.xbows_crossbow_unstrung_adamantite", tool = h)
    fletchRow("xbow_runite", KIND_ASSEMBLE, 69, 1000,
        listOf("obj.xbows_crossbow_stock_yew" to 1, "obj.xbows_crossbow_limbs_runite" to 1),
        "obj.xbows_crossbow_unstrung_runite", tool = h)
    fletchRow("xbow_dragon", KIND_ASSEMBLE, 78, 1350,
        listOf("obj.xbows_crossbow_stock_magic" to 1, "obj.xbows_crossbow_limbs_dragon" to 1),
        "obj.xbows_crossbow_unstrung_dragon", tool = h)

    fletchRow("ballista_incomplete_light", KIND_ASSEMBLE, 47, 1500,
        listOf("obj.ballista_frame_light" to 1, "obj.ballista_limbs" to 1), "obj.ballista_incomplete_light")
    fletchRow("ballista_incomplete_heavy", KIND_ASSEMBLE, 72, 3000,
        listOf("obj.ballista_frame_heavy" to 1, "obj.ballista_limbs" to 1), "obj.ballista_incomplete_heavy")
    fletchRow("ballista_unstrung_light", KIND_ASSEMBLE, 47, 1500,
        listOf("obj.ballista_incomplete_light" to 1, "obj.ballista_spring" to 1), "obj.ballista_unstrung_light")
    fletchRow("ballista_unstrung_heavy", KIND_ASSEMBLE, 72, 3000,
        listOf("obj.ballista_incomplete_heavy" to 1, "obj.ballista_spring" to 1), "obj.ballista_unstrung_heavy")
}

private fun ProductionTableScope.attachRows() {
    // headless arrows
    fletchRow("headless_arrow", KIND_ATTACH, 1, 10,
        listOf("obj.arrow_shaft" to 15, "obj.feather" to 15), "obj.headless_arrow", 15,
        ticks = 2, anim = "seq.human_fletching_add_feather", featherInput = true)

    // arrows: headless + tips
    fletchRow("arrow_bronze", KIND_ATTACH, 1, 13, listOf("obj.headless_arrow" to 15, "obj.bronze_arrowheads" to 15),
        "obj.bronze_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_iron", KIND_ATTACH, 15, 25, listOf("obj.headless_arrow" to 15, "obj.iron_arrowheads" to 15),
        "obj.iron_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_steel", KIND_ATTACH, 30, 50, listOf("obj.headless_arrow" to 15, "obj.steel_arrowheads" to 15),
        "obj.steel_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_mithril", KIND_ATTACH, 45, 75, listOf("obj.headless_arrow" to 15, "obj.mithril_arrowheads" to 15),
        "obj.mithril_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_broad", KIND_ATTACH, 52, 100, listOf("obj.headless_arrow" to 15, "obj.slayer_broad_arrowhead" to 15),
        "obj.slayer_broad_arrows", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_adamant", KIND_ATTACH, 60, 100, listOf("obj.headless_arrow" to 15, "obj.adamant_arrowheads" to 15),
        "obj.adamant_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_rune", KIND_ATTACH, 75, 125, listOf("obj.headless_arrow" to 15, "obj.rune_arrowheads" to 15),
        "obj.rune_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_amethyst", KIND_ATTACH, 82, 135, listOf("obj.headless_arrow" to 15, "obj.amethyst_arrowheads" to 15),
        "obj.amethyst_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    fletchRow("arrow_dragon", KIND_ATTACH, 90, 150, listOf("obj.headless_arrow" to 15, "obj.dragon_arrowheads" to 15),
        "obj.dragon_arrow", 15, ticks = 2, anim = "seq.human_fletching_add_arrow_tips")

    // bolts: unfeathered + feathers
    fun boltRow(name: String, level: Int, xp: Int, unfeathered: String, output: String, metalAnim: String) =
        fletchRow(name, KIND_ATTACH, level, xp, listOf(unfeathered to 10, "obj.feather" to 10), output, 10,
            ticks = 2, anim = "seq.human_fletching_add_bolt_feathers_$metalAnim", featherInput = true)
    boltRow("bolt_bronze", 9, 5, "obj.xbows_crossbow_bolts_bronze_unfeathered", "obj.bolt", "bronze")
    boltRow("bolt_blurite", 24, 10, "obj.xbows_crossbow_bolts_blurite_unfeathered", "obj.xbows_crossbow_bolts_blurite", "blurite")
    boltRow("bolt_iron", 39, 15, "obj.xbows_crossbow_bolts_iron_unfeathered", "obj.xbows_crossbow_bolts_iron", "iron")
    boltRow("bolt_silver", 43, 25, "obj.xbows_crossbow_bolts_silver_unfeathered", "obj.xbows_crossbow_bolts_silver", "silver")
    boltRow("bolt_steel", 46, 35, "obj.xbows_crossbow_bolts_steel_unfeathered", "obj.xbows_crossbow_bolts_steel", "steel")
    boltRow("bolt_mithril", 54, 50, "obj.xbows_crossbow_bolts_mithril_unfeathered", "obj.xbows_crossbow_bolts_mithril", "mithril")
    boltRow("bolt_broad", 55, 30, "obj.slayer_broad_bolt_unfinished", "obj.slayer_broad_bolt", "mithril")
    boltRow("bolt_adamant", 61, 70, "obj.xbows_crossbow_bolts_adamantite_unfeathered", "obj.xbows_crossbow_bolts_adamantite", "adamant")
    boltRow("bolt_runite", 69, 100, "obj.xbows_crossbow_bolts_runite_unfeathered", "obj.xbows_crossbow_bolts_runite", "rune")
    boltRow("bolt_dragon", 84, 120, "obj.dragon_bolts_unfeathered", "obj.dragon_bolts", "dragon")

    // darts: tip + feathers
    fun dartRow(name: String, level: Int, xp: Int, tip: String, output: String, metalAnim: String) =
        fletchRow(name, KIND_ATTACH, level, xp, listOf(tip to 10, "obj.feather" to 10), output, 10,
            ticks = 2, anim = "seq.human_fletching_add_dart_feathers_$metalAnim", featherInput = true)
    dartRow("dart_bronze", 10, 18, "obj.bronze_dart_tip", "obj.bronze_dart", "bronze")
    dartRow("dart_iron", 22, 38, "obj.iron_dart_tip", "obj.iron_dart", "iron")
    dartRow("dart_steel", 37, 75, "obj.steel_dart_tip", "obj.steel_dart", "steel")
    dartRow("dart_mithril", 52, 112, "obj.mithril_dart_tip", "obj.mithril_dart", "mithril")
    dartRow("dart_adamant", 67, 150, "obj.adamant_dart_tip", "obj.adamant_dart", "adamant")
    dartRow("dart_rune", 81, 188, "obj.rune_dart_tip", "obj.rune_dart", "rune")
    dartRow("dart_amethyst", 90, 210, "obj.amethyst_dart_tip", "obj.amethyst_dart", "amethyst")
    dartRow("dart_dragon", 95, 250, "obj.dragon_dart_tip", "obj.dragon_dart", "dragon")

    // javelins: shaft + heads
    fun javRow(name: String, level: Int, xp: Int, head: String, output: String) =
        fletchRow(name, KIND_ATTACH, level, xp, listOf("obj.javelin_shaft" to 15, head to 15), output, 15,
            ticks = 2, anim = "seq.human_fletching_add_arrow_tips")
    javRow("javelin_bronze", 3, 10, "obj.bronze_javelin_head", "obj.bronze_javelin")
    javRow("javelin_iron", 17, 20, "obj.iron_javelin_head", "obj.iron_javelin")
    javRow("javelin_steel", 32, 50, "obj.steel_javelin_head", "obj.steel_javelin")
    javRow("javelin_mithril", 47, 80, "obj.mithril_javelin_head", "obj.mithril_javelin")
    javRow("javelin_adamant", 62, 100, "obj.adamant_javelin_head", "obj.adamant_javelin")
    javRow("javelin_rune", 77, 124, "obj.rune_javelin_head", "obj.rune_javelin")
    javRow("javelin_amethyst", 84, 135, "obj.amethyst_javelin_head", "obj.amethyst_javelin")
    javRow("javelin_dragon", 92, 150, "obj.dragon_javelin_head", "obj.dragon_javelin")

    // tipped bolts: bolt + tips
    fun tippedRow(name: String, level: Int, xp: Int, bolt: String, tip: String, output: String, tipAnim: String) =
        fletchRow(name, KIND_ATTACH, level, xp, listOf(bolt to 10, tip to 10), output, 10,
            ticks = 2, anim = tipAnim)
    val bronzeTip = "seq.human_fletching_add_bolt_tips_bronze"
    tippedRow("tip_bronze_opal", 11, 16, "obj.bolt", "obj.opal_bolttips",
        "obj.opal_bolt", bronzeTip)
    tippedRow("tip_blurite_jade", 26, 24, "obj.xbows_crossbow_bolts_blurite", "obj.xbows_bolt_tips_jade",
        "obj.xbows_crossbow_bolts_blurite_tipped_jade", "seq.human_fletching_add_bolt_tips_blurite")
    tippedRow("tip_iron_pearl", 41, 32, "obj.xbows_crossbow_bolts_iron", "obj.pearl_bolttips",
        "obj.pearl_bolt", "seq.human_fletching_add_bolt_tips_iron")
    tippedRow("tip_steel_topaz", 48, 39, "obj.xbows_crossbow_bolts_steel", "obj.xbows_bolt_tips_redtopaz",
        "obj.xbows_crossbow_bolts_steel_tipped_redtopaz", "seq.human_fletching_add_bolt_tips_steel")
    tippedRow("tip_bronze_barbed", 51, 95, "obj.bolt", "obj.barbed_bolttips",
        "obj.barbed_bolt", bronzeTip)
    tippedRow("tip_mithril_sapphire", 56, 47, "obj.xbows_crossbow_bolts_mithril", "obj.xbows_bolt_tips_sapphire",
        "obj.xbows_crossbow_bolts_mithril_tipped_sapphire", "seq.human_fletching_add_bolt_tips_mithril")
    tippedRow("tip_mithril_emerald", 58, 55, "obj.xbows_crossbow_bolts_mithril", "obj.xbows_bolt_tips_emerald",
        "obj.xbows_crossbow_bolts_mithril_tipped_emerald", "seq.human_fletching_add_bolt_tips_mithril")
    tippedRow("tip_adamant_ruby", 63, 63, "obj.xbows_crossbow_bolts_adamantite", "obj.xbows_bolt_tips_ruby",
        "obj.xbows_crossbow_bolts_adamantite_tipped_ruby", "seq.human_fletching_add_bolt_tips_adamant")
    tippedRow("tip_adamant_diamond", 65, 70, "obj.xbows_crossbow_bolts_adamantite", "obj.xbows_bolt_tips_diamond",
        "obj.xbows_crossbow_bolts_adamantite_tipped_diamond", "seq.human_fletching_add_bolt_tips_adamant")
    tippedRow("tip_runite_dragonstone", 71, 82, "obj.xbows_crossbow_bolts_runite", "obj.xbows_bolt_tips_dragonstone",
        "obj.xbows_crossbow_bolts_runite_tipped_dragonstone", "seq.human_fletching_add_bolt_tips_rune")
    tippedRow("tip_runite_onyx", 73, 94, "obj.xbows_crossbow_bolts_runite", "obj.xbows_bolt_tips_onyx",
        "obj.xbows_crossbow_bolts_runite_tipped_onyx", "seq.human_fletching_add_bolt_tips_rune")
    tippedRow("tip_broad_amethyst", 76, 106, "obj.slayer_broad_bolt", "obj.xbows_bolt_tips_amethyst",
        "obj.slayer_broad_bolt_amethyst", "seq.human_fletching_add_bolt_tips_rune")

    // tipped dragon bolts (all lvl 84)
    fun dragonTipRow(gem: String, tip: String, xp: Int) =
        fletchRow("tip_dragon_$gem", KIND_ATTACH, 84, xp,
            listOf("obj.dragon_bolts" to 10, tip to 10),
            "obj.dragon_bolts_unenchanted_$gem", 10, ticks = 2, anim = "seq.human_fletching_add_bolt_tips_dragon")
    dragonTipRow("opal", "obj.opal_bolttips", 16)
    dragonTipRow("jade", "obj.xbows_bolt_tips_jade", 24)
    dragonTipRow("pearl", "obj.pearl_bolttips", 32)
    dragonTipRow("topaz", "obj.xbows_bolt_tips_redtopaz", 40)
    dragonTipRow("sapphire", "obj.xbows_bolt_tips_sapphire", 47)
    dragonTipRow("emerald", "obj.xbows_bolt_tips_emerald", 55)
    dragonTipRow("ruby", "obj.xbows_bolt_tips_ruby", 63)
    dragonTipRow("diamond", "obj.xbows_bolt_tips_diamond", 70)
    dragonTipRow("dragonstone", "obj.xbows_bolt_tips_dragonstone", 82)
    dragonTipRow("onyx", "obj.xbows_bolt_tips_onyx", 94)

    // brutal arrows: flighted ogre + nails, hammer required
    fun brutalRow(name: String, level: Int, xp: Int, nails: String, output: String) =
        fletchRow(name, KIND_ATTACH, level, xp, listOf("obj.ogre_headless_arrow" to 6, nails to 6), output, 6,
            tool = "obj.hammer", ticks = 2, anim = "seq.human_ogre_fletching")
    brutalRow("brutal_bronze", 7, 14, "obj.nails_bronze", "obj.zogre_brutal_bronze")
    brutalRow("brutal_iron", 18, 26, "obj.nails_iron", "obj.zogre_brutal_iron")
    brutalRow("brutal_steel", 33, 51, "obj.nails", "obj.zogre_brutal_steel")
    brutalRow("brutal_black", 38, 65, "obj.nails_black", "obj.zogre_brutal_black")
    brutalRow("brutal_mithril", 49, 75, "obj.nails_mithril", "obj.zogre_brutal_mithril")
    brutalRow("brutal_adamant", 62, 102, "obj.nails_adamant", "obj.zogre_brutal_adamant")
    brutalRow("brutal_rune", 77, 125, "obj.nails_rune", "obj.zogre_brutal_rune")

    // ogre arrows
    fletchRow("ogre_headless", KIND_ATTACH, 5, 9,
        listOf("obj.ogre_arrow_shaft" to 4, "obj.feather" to 4), "obj.ogre_headless_arrow", 4,
        ticks = 2, anim = "seq.human_ogre_fletching", featherInput = true)
    fletchRow("ogre_arrow", KIND_ATTACH, 5, 10,
        listOf("obj.ogre_headless_arrow" to 6, "obj.wolfbone_arrowheads" to 6), "obj.ogre_arrow", 6,
        ticks = 2, anim = "seq.human_ogre_fletching")

    // atlatl darts
    fletchRow("atlatl_headless", KIND_ATTACH, 74, 15,
        listOf("obj.atlatl_dart_shaft" to 10, "obj.feather" to 10), "obj.headless_atlatl_dart", 10,
        ticks = 2, featherInput = true)
    fletchRow("atlatl_dart", KIND_ATTACH, 74, 95,
        listOf("obj.headless_atlatl_dart" to 10, "obj.atlatl_dart_tips" to 10), "obj.atlatl_dart", 10,
        ticks = 2, anim = "seq.human_fletching_add_arrow_tips")

    // mith grapple
    fletchRow("grapple_unf", KIND_ATTACH, 59, 110,
        listOf("obj.xbows_crossbow_bolts_mithril" to 1, "obj.xbows_grapple_tip_mithril" to 1),
        "obj.xbows_grapple_tip_bolt_mithril", 1, ticks = 2)
    fletchRow("grapple_rope", KIND_ATTACH, 59, 0,
        listOf("obj.xbows_grapple_tip_bolt_mithril" to 1, "obj.rope" to 1),
        "obj.xbows_grapple_tip_bolt_mithril_rope", 1, ticks = 2)
}

private fun ProductionTableScope.chiselRows() {
    fun gemRow(name: String, level: Int, xp: Int, gem: String, tips: String, count: Int = 12) =
        fletchRow(name, KIND_CHISEL, level, xp, listOf(gem to 1), tips, count,
            tool = "obj.chisel", ticks = 2, anim = "seq.human_cutting")
    gemRow("tips_opal", 11, 15, "obj.opal", "obj.opal_bolttips")
    gemRow("tips_jade", 26, 20, "obj.jade", "obj.xbows_bolt_tips_jade")
    gemRow("tips_pearl", 41, 32, "obj.smalloysterpearls", "obj.pearl_bolttips", 6)
    gemRow("tips_redtopaz", 48, 39, "obj.red_topaz", "obj.xbows_bolt_tips_redtopaz")
    gemRow("tips_sapphire", 56, 40, "obj.sapphire", "obj.xbows_bolt_tips_sapphire")
    gemRow("tips_emerald", 58, 55, "obj.emerald", "obj.xbows_bolt_tips_emerald")
    gemRow("tips_ruby", 63, 63, "obj.ruby", "obj.xbows_bolt_tips_ruby")
    gemRow("tips_diamond", 65, 70, "obj.diamond", "obj.xbows_bolt_tips_diamond")
    gemRow("tips_dragonstone", 71, 82, "obj.dragonstone", "obj.xbows_bolt_tips_dragonstone")
    gemRow("tips_onyx", 73, 94, "obj.onyx", "obj.xbows_bolt_tips_onyx", 24)

    val chiselAnim = "seq.human_fletching_huntingbolts_chisel"
    fletchRow("wolfbone_tips", KIND_CHISEL, 5, 25, listOf("obj.wolf_bones" to 1), "obj.wolfbone_arrowheads", 4,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("kebbit_bolts", KIND_CHISEL, 32, 58, listOf("obj.huntingbeast_spike" to 1), "obj.huntingbow_bolts", 6,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("long_kebbit_bolts", KIND_CHISEL, 42, 79, listOf("obj.huntingbeast_bigspike" to 1), "obj.huntingbow_bigbolts", 6,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("sunlight_bolts", KIND_CHISEL, 62, 100, listOf("obj.sunlight_antler" to 1), "obj.sunlight_antelope_bolt", 12,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("moonlight_bolts", KIND_CHISEL, 82, 120, listOf("obj.moonlight_antler" to 1), "obj.moonlight_antelope_bolt", 12,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("atlatl_tips", KIND_CHISEL, 74, 100, listOf("obj.custodian_broken_antler" to 1), "obj.atlatl_dart_tips", 6,
        tool = "obj.chisel", ticks = 2, anim = chiselAnim)
    fletchRow("toxic_blowpipe", KIND_CHISEL, 78, 1200, listOf("obj.blowpipe_fang" to 1), "obj.toxic_blowpipe", 1,
        tool = "obj.chisel", ticks = 2)
}
