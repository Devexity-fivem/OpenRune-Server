package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LunarUtilityScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton("component.magic_spellbook:humidify") {
            cast(HUMIDIFY_OBJ, { hasHumidifyTargets() }, { humidify() })
        }
        onIfOverlayButton("component.magic_spellbook:bake_pie") {
            cast(BAKE_PIE_OBJ, { hasUncookedPies() }, { bakePie() })
        }
        onIfOverlayButton("component.magic_spellbook:spin_flax") {
            cast(SPIN_FLAX_OBJ, { hasFlax() }, { spinFlax() })
        }
        onIfOverlayButton("component.magic_spellbook:superglass") {
            cast(SUPERGLASS_OBJ, { hasSuperglassIngredients() }, { superglass() })
        }
        onIfOverlayButton("component.magic_spellbook:hunter_kit") {
            cast(HUNTER_KIT_OBJ, { true }, { hunterKit() })
        }
        onIfOverlayButton("component.magic_spellbook:cure_me") {
            cast(CURE_ME_OBJ, { isPoisoned() }, { cureMe() })
        }
        onIfOverlayButton("component.magic_spellbook:string_jewel") {
            cast(STRING_JEWEL_OBJ, { hasUnstrungAmulets() }, { stringJewellery() })
        }
    }

    private suspend fun ProtectedAccess.cast(
        spellObj: String,
        canCast: ProtectedAccess.() -> Boolean,
        action: ProtectedAccess.() -> Unit,
    ) {
        if (!QuestRequirements.hasCompleted(player, REQUIRED_QUEST)) {
            mes("You need to have completed Lunar Diplomacy to cast this spell.")
            return
        }
        val item = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(item) ?: return
        if (!canCast() || runes.attemptCast(player, spell).isFailure()) {
            return
        }
        anim(CAST_ANIM)
        delay(CAST_TICKS)
        action()
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.hasHumidifyTargets(): Boolean {
        val targets = HUMIDIFY_VESSELS.keys + WATER_SKINS + EMPTY_WATERING_CANS
        if (targets.any { inv.contains(it) }) {
            return true
        }
        mes("You have nothing to humidify.")
        return false
    }

    private fun ProtectedAccess.humidify() {
        for ((from, to) in HUMIDIFY_VESSELS) {
            val count = inv.count(from)
            if (count == 0 || invDel(inv, from, count).failure) {
                continue
            }
            invAdd(inv, to, count)
        }
        for (skin in WATER_SKINS) {
            val count = inv.count(skin)
            if (count == 0 || invDel(inv, skin, count).failure) {
                continue
            }
            invAdd(inv, "obj.water_skin4", count)
        }
        for (can in EMPTY_WATERING_CANS) {
            val count = inv.count(can)
            if (count == 0 || invDel(inv, can, count).failure) {
                continue
            }
            invAdd(inv, "obj.watering_can_8", count)
        }
        spotanim(HUMIDIFY_SPOTANIM)
    }

    private fun ProtectedAccess.hasUncookedPies(): Boolean {
        if (PIES.keys.any { inv.contains(it) }) {
            return true
        }
        mes("You have no pies that you can bake.")
        return false
    }

    private fun ProtectedAccess.bakePie() {
        for ((from, to) in PIES) {
            val count = inv.count(from)
            if (count == 0 || invDel(inv, from, count).failure) {
                continue
            }
            invAdd(inv, to, count)
        }
        spotanim(BAKE_PIE_SPOTANIM)
    }

    private fun ProtectedAccess.hasFlax(): Boolean {
        if (inv.contains("obj.flax")) {
            return true
        }
        mes("You don't have any flax to spin.")
        return false
    }

    private fun ProtectedAccess.spinFlax() {
        val count = inv.count("obj.flax").coerceAtMost(MAX_SPIN_FLAX)
        invDel(inv, "obj.flax", count)
        invAdd(inv, "obj.bow_string", count)
    }

    private fun ProtectedAccess.hasSuperglassIngredients(): Boolean {
        val sand = inv.count("obj.bucket_sand")
        val ashUnits =
            inv.count("obj.soda_ash") +
                inv.count("obj.seaweed") +
                inv.count("obj.giant_seaweed") * GIANT_SEAWEED_ASH
        if (sand.coerceAtMost(ashUnits) > 0) {
            return true
        }
        mes("You don't have the required ingredients to make molten glass.")
        return false
    }

    private fun ProtectedAccess.superglass() {
        val sand = inv.count("obj.bucket_sand")
        val sodaAsh = inv.count("obj.soda_ash")
        val seaweed = inv.count("obj.seaweed")
        val giantSeaweed = inv.count("obj.giant_seaweed")
        val ashUnits = sodaAsh + seaweed + giantSeaweed * GIANT_SEAWEED_ASH
        val pairs = sand.coerceAtMost(ashUnits)
        var remaining = pairs
        val usedSodaAsh = minOf(remaining, sodaAsh)
        invDel(inv, "obj.soda_ash", usedSodaAsh)
        remaining -= usedSodaAsh
        val usedSeaweed = minOf(remaining, seaweed)
        invDel(inv, "obj.seaweed", usedSeaweed)
        remaining -= usedSeaweed
        val usedGiant = (remaining + GIANT_SEAWEED_ASH - 1) / GIANT_SEAWEED_ASH
        invDel(inv, "obj.giant_seaweed", usedGiant)
        invDel(inv, "obj.bucket_sand", pairs)
        invAdd(inv, "obj.molten_glass", pairs * SUPERGLASS_BONUS_TENTHS / 10)
    }

    private fun ProtectedAccess.hunterKit() {
        for (item in HUNTER_KIT_ITEMS) {
            invAdd(inv, item, 1)
        }
    }

    private fun ProtectedAccess.hasUnstrungAmulets(): Boolean {
        if (AMULETS.keys.any { inv.contains(it) }) {
            return true
        }
        mes("You don't have any unstrung amulets to string.")
        return false
    }

    private fun ProtectedAccess.stringJewellery() {
        for ((from, to) in AMULETS) {
            val count = inv.count(from)
            if (count == 0 || invDel(inv, from, count).failure) {
                continue
            }
            invAdd(inv, to, count)
            statAdvance("stat.crafting", STRING_CRAFT_XP * count)
        }
        spotanim(STRING_JEWEL_SPOTANIM)
    }

    private fun ProtectedAccess.isPoisoned(): Boolean {
        if (PlayerPoison.isPoisoned(player)) {
            return true
        }
        mes("You are not poisoned.")
        return false
    }

    private fun ProtectedAccess.cureMe() {
        PlayerPoison.clear(player)
        spotanim(CURE_ME_SPOTANIM)
    }

    private companion object {
        const val REQUIRED_QUEST = "quest_lunardiplomacy"
        const val CAST_ANIM = "seq.human_cast_selfimbue"
        const val CAST_TICKS = 2

        const val HUMIDIFY_OBJ = "obj.68_humidify"
        const val BAKE_PIE_OBJ = "obj.65_bake_pie"
        const val SPIN_FLAX_OBJ = "obj.76_spin_flax"
        const val SUPERGLASS_OBJ = "obj.77_superglass"
        const val HUNTER_KIT_OBJ = "obj.71_hunter_kit"
        const val CURE_ME_OBJ = "obj.71_cure_me"
        const val STRING_JEWEL_OBJ = "obj.80_string_jewelry"

        const val HUMIDIFY_SPOTANIM = "spotanim.dream_humidify_spell_spotanim"
        const val BAKE_PIE_SPOTANIM = "spotanim.quest_lunar_spellbook_bake_pie_spot_anim"
        const val CURE_ME_SPOTANIM = "spotanim.quest_lunar_spellbook_cure_poison_spot_anim"
        const val STRING_JEWEL_SPOTANIM =
            "spotanim.3quest_lunar_spellbook_string_jewelery_spot_anim"

        const val MAX_SPIN_FLAX = 5
        const val GIANT_SEAWEED_ASH = 6
        const val SUPERGLASS_BONUS_TENTHS = 13
        const val STRING_CRAFT_XP = 4.0

        val AMULETS =
            mapOf(
                "obj.unstrung_gold_amulet" to "obj.strung_gold_amulet",
                "obj.unstrung_sapphire_amulet" to "obj.strung_sapphire_amulet",
                "obj.unstrung_emerald_amulet" to "obj.strung_emerald_amulet",
                "obj.unstrung_ruby_amulet" to "obj.strung_ruby_amulet",
                "obj.unstrung_diamond_amulet" to "obj.strung_diamond_amulet",
                "obj.unstrung_dragonstone_amulet" to "obj.strung_dragonstone_amulet",
                "obj.unstrung_onyx_amulet" to "obj.strung_onyx_amulet",
                "obj.unstrung_opal_amulet" to "obj.strung_opal_amulet",
                "obj.unstrung_jade_amulet" to "obj.strung_jade_amulet",
                "obj.unstrung_topaz_amulet" to "obj.strung_topaz_amulet",
            )

        val HUMIDIFY_VESSELS =
            mapOf(
                "obj.vial_empty" to "obj.vial_water",
                "obj.jug_empty" to "obj.jug_water",
                "obj.bucket_empty" to "obj.bucket_water",
                "obj.bowl_empty" to "obj.bowl_water",
                "obj.cup_empty" to "obj.cup_water",
                "obj.fishbowl_empty" to "obj.fishbowl_water",
            )

        val WATER_SKINS = listOf("obj.water_skin0", "obj.water_skin1", "obj.water_skin2", "obj.water_skin3")

        val EMPTY_WATERING_CANS =
            listOf(
                "obj.watering_can_0",
                "obj.watering_can_1",
                "obj.watering_can_2",
                "obj.watering_can_3",
                "obj.watering_can_4",
                "obj.watering_can_5",
                "obj.watering_can_6",
                "obj.watering_can_7",
            )

        val PIES =
            mapOf(
                "obj.uncooked_redberry_pie" to "obj.redberry_pie",
                "obj.uncooked_meat_pie" to "obj.meat_pie",
                "obj.uncooked_apple_pie" to "obj.apple_pie",
            )

        val HUNTER_KIT_ITEMS =
            listOf(
                "obj.noose_wand",
                "obj.hunting_butterfly_net",
                "obj.hunting_snare",
                "obj.hunting_snare_rabbit",
                "obj.hunting_teasing_stick",
                "obj.hunting_box_trap",
                "obj.torch_lit",
                "obj.ii_impling_jar",
            )
    }
}
