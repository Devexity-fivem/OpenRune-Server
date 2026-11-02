package org.rsmod.content.minigames.gotr.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * Rewards Guardian drop table, sourced from
 * https://oldschool.runescape.wiki/w/Rewards_Guardian and
 * https://oldschool.runescape.wiki/w/Intricate_pouch (Jagex-published rates).
 *
 * Weighted sections roll one entry; denominator sections roll each entry
 * independently. `quest`/`fallback_obj` swap the reward when the quest is not
 * complete. `req_level` is the Runecraft level needed for the full quantity.
 */
object GotrRewardsTable {

    const val KEY = 0
    const val SECTION = 1
    const val OBJ = 2
    const val QTY_MIN = 3
    const val QTY_MAX = 4
    const val WEIGHT = 5
    const val DENOMINATOR = 6
    const val REQ_LEVEL = 7
    const val UNIQUE_VARBIT = 8
    const val QUEST = 9
    const val FALLBACK_OBJ = 10

    fun rewards() =
        dbTable("dbtable.gotr_reward_table", serverOnly = true) {
            column("key", KEY, VarType.STRING)
            column("section", SECTION, VarType.STRING)
            column("obj", OBJ, VarType.STRING)
            column("qty_min", QTY_MIN, VarType.INT)
            column("qty_max", QTY_MAX, VarType.INT)
            column("weight", WEIGHT, VarType.INT)
            column("denominator", DENOMINATOR, VarType.INT)
            column("req_level", REQ_LEVEL, VarType.INT)
            column("unique_varbit", UNIQUE_VARBIT, VarType.STRING)
            column("quest", QUEST, VarType.STRING)
            column("fallback_obj", FALLBACK_OBJ, VarType.STRING)

            fun reward(
                key: String,
                section: String,
                obj: String = "",
                min: Int = 1,
                max: Int = min,
                weight: Int = 0,
                denominator: Int = 0,
                reqLevel: Int = 0,
                unique: String = "",
                quest: String = "",
                fallback: String = "",
            ) {
                row("dbrow.gotr_reward_$key") {
                    column(KEY, key)
                    column(SECTION, section)
                    column(OBJ, obj)
                    column(QTY_MIN, min)
                    column(QTY_MAX, max)
                    column(WEIGHT, weight)
                    column(DENOMINATOR, denominator)
                    column(REQ_LEVEL, reqLevel)
                    column(UNIQUE_VARBIT, unique)
                    column(QUEST, quest)
                    column(FALLBACK_OBJ, fallback)
                }
            }

            // Main table: single weighted pick out of 140 (125 when the pouch
            // entry is skipped because every essence pouch is owned).
            reward("pouch", "main", weight = 15)
            reward("air_rune", "main", "obj.airrune", 400, 500, weight = 4, reqLevel = 1)
            reward("water_rune", "main", "obj.waterrune", 400, 500, weight = 4, reqLevel = 5)
            reward("earth_rune", "main", "obj.earthrune", 400, 500, weight = 4, reqLevel = 9)
            reward("fire_rune", "main", "obj.firerune", 400, 500, weight = 4, reqLevel = 14)
            reward("mind_rune", "main", "obj.mindrune", 250, 400, weight = 4, reqLevel = 2)
            reward("body_rune", "main", "obj.bodyrune", 80, 150, weight = 4, reqLevel = 20)
            reward("chaos_rune", "main", "obj.chaosrune", 61, 150, weight = 10, reqLevel = 35)
            reward("cosmic_rune", "main", "obj.cosmicrune", 20, 30, weight = 10, reqLevel = 27)
            reward("nature_rune", "main", "obj.naturerune", 28, 150, weight = 10, reqLevel = 44)
            reward("law_rune", "main", "obj.lawrune", 5, 120, weight = 10, reqLevel = 54)
            reward("death_rune", "main", "obj.deathrune", 5, 120, weight = 10, reqLevel = 65)
            reward("blood_rune", "main", "obj.bloodrune", 5, 120, weight = 10, reqLevel = 77)
            reward("talisman", "main", weight = 16)
            reward("abyssal_pearl", "main", "obj.abyssal_pearl", 14, 16, weight = 18)
            reward("intricate_pouch", "main", "obj.gotr_intricate_pouch", weight = 5)
            reward("abyssal_ashes", "main", "obj.abyssal_ashes", weight = 1)
            reward("needle", "main", "obj.needle", weight = 1)

            // Talisman sub-table: 16/140 to enter, then a guaranteed pick out
            // of 560 (the published 4900 denominators are 140 x 35 combined).
            reward("air_talisman", "talisman", "obj.cert_air_talisman", weight = 48)
            reward("water_talisman", "talisman", "obj.cert_water_talisman", weight = 48)
            reward("earth_talisman", "talisman", "obj.cert_earth_talisman", weight = 48)
            reward("fire_talisman", "talisman", "obj.cert_fire_talisman", weight = 48)
            reward("mind_talisman", "talisman", "obj.cert_mind_talisman", weight = 64)
            reward("body_talisman", "talisman", "obj.cert_body_talisman", weight = 64)
            reward("chaos_talisman", "talisman", "obj.cert_chaos_talisman", weight = 64)
            reward("cosmic_talisman", "talisman", "obj.cert_cosmic_talisman", weight = 64)
            reward("nature_talisman", "talisman", "obj.cert_nature_talisman", weight = 64)
            reward("elemental_talisman", "talisman", "obj.elemental_talisman", weight = 16)
            reward(
                "law_talisman",
                "talisman",
                "obj.law_talisman",
                weight = 16,
                quest = "quest_trollstrongholdquest",
                fallback = "obj.air_talisman",
            )
            reward(
                "death_talisman",
                "talisman",
                "obj.cert_death_talisman",
                weight = 16,
                quest = "quest_mourningsendpart2",
                fallback = "obj.cert_air_talisman",
            )

            // Rare reward table: independent rolls per search. `pet` is the
            // published tertiary roll; identical independent-roll semantics.
            reward("atlaxs_diary", "rare", "obj.gotr_book", denominator = 20, unique = "varbit.gotr_book_obtained")
            reward("catalytic_talisman", "rare", "obj.catalytic_talisman", denominator = 200)
            reward("abyssal_needle", "rare", "obj.abyssal_needle", denominator = 300, unique = "varbit.gotr_unlocked_needle")
            reward("abyssal_lantern", "rare", "obj.abyssal_lantern", denominator = 700)
            reward("red_dye", "rare", "obj.abyssal_red_dye", denominator = 1200)
            reward("green_dye", "rare", "obj.abyssal_green_dye", denominator = 1200)
            reward("blue_dye", "rare", "obj.abyssal_blue_dye", denominator = 1200)
            reward("abyssal_protector", "tertiary", "obj.abyssalpet", denominator = 4000)

            // Intricate pouch pre-roll: independent rolls on open.
            reward("pouch_blood_essence", "pouch_preroll", "obj.blood_essence_inactive", denominator = 200)
            reward("pouch_lamp", "pouch_preroll", "obj.macro_genilamp", denominator = 15)
            reward("pouch_locket", "pouch_preroll", "obj.gotr_tarnished_locket", denominator = 40, unique = "varbit.gotr_locket_obtained")
            reward("pouch_lost_bag", "pouch_preroll", "obj.gotr_lost_bag", denominator = 60, unique = "varbit.gotr_bag_obtained")

            // Intricate pouch main table: single pick out of 45.
            reward("pouch_astral_rune", "pouch_main", "obj.astralrune", 150, 200, weight = 5)
            reward("pouch_blood_rune", "pouch_main", "obj.bloodrune", 150, 200, weight = 5)
            reward("pouch_chaos_rune", "pouch_main", "obj.chaosrune", 200, 300, weight = 5)
            reward("pouch_cosmic_rune", "pouch_main", "obj.cosmicrune", 200, 300, weight = 5)
            reward("pouch_death_rune", "pouch_main", "obj.deathrune", 150, 200, weight = 5)
            reward("pouch_law_rune", "pouch_main", "obj.lawrune", 200, 250, weight = 5)
            reward("pouch_nature_rune", "pouch_main", "obj.naturerune", 200, 300, weight = 5)
            reward("pouch_soul_rune", "pouch_main", "obj.soulrune", 150, 200, weight = 5)
            reward("pouch_shield_half", "pouch_main", "obj.dragonshield_a", weight = 1)
            reward("pouch_dragon_spear", "pouch_main", "obj.dragon_spear", weight = 1)
            reward("pouch_crystal_key", "pouch_main", "obj.crystal_key", weight = 1)
            reward("pouch_dragon_med", "pouch_main", "obj.dragon_med_helm", weight = 1)
            reward("pouch_pure_essence", "pouch_main", "obj.cert_blankrune", 500, 1000, weight = 1)

            // Intricate pouch tertiary: independent 1/10 hard clue.
            reward("pouch_hard_clue", "pouch_tertiary", "obj.trail_clue_hard_map001", denominator = 10)
        }
}
