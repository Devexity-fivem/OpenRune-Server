package org.rsmod.content.skills.runecrafting.action

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import kotlin.math.floor
import org.rsmod.api.enums.RunecraftingEnums
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ComboruneRecipeRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.runecrafting.bloodEssenceCharges
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.consumeChargeAfterCombo
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.isWearing
import org.rsmod.content.skills.runecrafting.items.BloodEssence
import org.rsmod.content.skills.runecrafting.items.BloodEssence.commitBloodRuneBonus
import org.rsmod.content.skills.runecrafting.items.BloodEssence.rollBloodRuneBonus
import org.rsmod.content.skills.runecrafting.items.RaimentsOfTheEye.applyBonus
import org.rsmod.content.skills.runecrafting.magic.MagicImbue.isActive
import org.rsmod.game.inv.InvObj

object RunecraftAction {
    private const val RUNECRAFT_WAIT_CYCLE = 3
    private const val RUNECRAFT_SOUND = 2710
    private const val RUNE_ESSENCE = EssencePouch.RUNE_ESSENCE
    private const val PURE_ESSENCE = EssencePouch.PURE_ESSENCE
    private const val DAEYALT_ESSENCE = EssencePouch.DAEYALT_ESSENCE
    private const val GUARDIAN_ESSENCE = EssencePouch.GUARDIAN_ESSENCE
    private const val DARK_ESSENCE = EssencePouch.DARK_ESSENCE_FRAGMENT
    private const val DAEYALT_XP_MULTIPLIER = 1.5
    private const val OURANIA_XP_MULTIPLIER = 1.7
    private const val CORE_RUNE_MULTIPLIER = 11
    private const val CORE_XP_MULTIPLIER = 10

    suspend fun ProtectedAccess.preCraft() {
        anim("seq.human_runecraft")
        spotanim("spotanim.runecrafting", height = 100)
        soundSynth(RUNECRAFT_SOUND)
        delay(RUNECRAFT_WAIT_CYCLE)
    }

    suspend fun ProtectedAccess.craftRune(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean = false,
    ) {
        if (!canCraftRune(rune)) {
            return
        }

        preCraft()
        if (!canCraftRune(rune)) {
            return
        }

        val validEssence = rune.input.map { it.internalName }.toSet()
        val acceptsDaeyalt = PURE_ESSENCE in validEssence
        val daeyaltEssCount =
            if (acceptsDaeyalt) {
                inv.count(DAEYALT_ESSENCE)
            } else {
                0
            }

        if (daeyaltEssCount > 0) {
            craftDaeyaltEssence(rune, xpMods, daeyaltEssCount, ouraniaAltar)
            return
        }

        craftStandardEssence(rune, xpMods, validEssence, ouraniaAltar)
    }

    private suspend fun ProtectedAccess.craftDaeyaltEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        daeyaltEssCount: Int,
        ouraniaAltar: Boolean,
    ) {
        val level = player.runecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val baseProduced = daeyaltEssCount * baseMultiplier
        val produced = applyBonus(baseProduced)

        val xpMultiplier =
            if (ouraniaAltar) {
                DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
            } else {
                DAEYALT_XP_MULTIPLIER
            }

        finishEssenceCraft(
            rune,
            listOf(InvObj(DAEYALT_ESSENCE, daeyaltEssCount)),
            daeyaltEssCount * rune.xp / 10.0 * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
        )
    }

    private suspend fun ProtectedAccess.craftStandardEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        validEssence: Set<String>,
        ouraniaAltar: Boolean,
    ) {
        val runeEssCount = if (RUNE_ESSENCE in validEssence) inv.count(RUNE_ESSENCE) else 0
        val pureEssCount = if (PURE_ESSENCE in validEssence) inv.count(PURE_ESSENCE) else 0
        val guardianEssCount =
            if (PURE_ESSENCE in validEssence) {
                inv.count(GUARDIAN_ESSENCE)
            } else {
                0
            }
        val darkEssCount =
            if (DARK_ESSENCE in validEssence) {
                inv.count(DARK_ESSENCE)
            } else {
                0
            }
        val totalEssence = runeEssCount + pureEssCount + guardianEssCount + darkEssCount
        if (totalEssence <= 0) {
            return
        }

        val inputs = buildList {
            if (pureEssCount > 0) add(InvObj(PURE_ESSENCE, pureEssCount))
            if (guardianEssCount > 0) add(InvObj(GUARDIAN_ESSENCE, guardianEssCount))
            if (darkEssCount > 0) add(InvObj(DARK_ESSENCE, darkEssCount))
            if (runeEssCount > 0) add(InvObj(RUNE_ESSENCE, runeEssCount))
        }

        val level = player.runecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()

        val basePure = pureEssCount * baseMultiplier
        val baseGuardian = guardianEssCount * baseMultiplier
        val baseDark = darkEssCount * baseMultiplier
        val baseRune = runeEssCount * baseMultiplier
        val totalXp = totalEssence * rune.xp / 10.0
        val xpMultiplier = if (ouraniaAltar) OURANIA_XP_MULTIPLIER else 1.0
        val produced =
            applyBonus(basePure) +
                applyBonus(baseGuardian) +
                applyBonus(baseDark) +
                applyBonus(baseRune)

        finishEssenceCraft(
            rune,
            inputs,
            totalXp * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
        )
    }

    private suspend fun ProtectedAccess.finishEssenceCraft(
        rune: RunecraftingRunesRow,
        inputs: List<InvObj>,
        xp: Double,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean,
        producedRunes: Int,
    ) {
        val essenceConsumed = inputs.sumOf { it.count }
        val extract = rune.extract
        val extractBonus = RunecraftingEnums.runecrafting_extract_runes[extract] ?: 0
        val extractCount = if (!ouraniaAltar && extractBonus > 0) {
            minOf(essenceConsumed, inv.count(extract.internalName))
        } else {
            0
        }
        val extractRunes = extractCount * extractBonus
        val bloodRunes = if (!ouraniaAltar && rune.output.internalName == BloodEssence.BLOOD_RUNE) {
            rollBloodRuneBonus(essenceConsumed)
        } else {
            0
        }
        val crafted = player.invTransaction(inv) {
            val inventory = select(inv)
            for (input in inputs) delete(inventory, input.id, input.count)
            if (extractCount > 0) delete(inventory, extract.id, extractCount)
            if (bloodRunes > 0 && bloodRunes == player.bloodEssenceCharges) {
                delete(inventory, BloodEssence.ACTIVE.asRSCM(RSCMType.OBJ), 1)
            }
            add(inventory, rune.output.id, producedRunes + extractRunes + bloodRunes)
        }
        if (crafted.failure) {
            mes("You don't have enough inventory space to bind these runes.")
            return
        }
        commitBloodRuneBonus(bloodRunes)
        advanceRunecraftingXp(xp, xpMods)
    }

    private suspend fun ProtectedAccess.canCraftRune(rune: RunecraftingRunesRow): Boolean {
        val level = player.runecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s.",
            )
            return false
        }

        if (
            rune.output.internalName == "obj.astralrune" &&
            !QuestRequirements.hasCompleted(player, "quest_lunardiplomacy")
        ) {
            mesbox("You need to complete Lunar Diplomacy to craft astral runes.")
            return false
        }

        val validEssenceIds = rune.input.map { it.internalName }.toSet()
        val acceptsPureSubstitutes = PURE_ESSENCE in validEssenceIds
        val checkIds =
            when {
                acceptsPureSubstitutes ->
                    validEssenceIds + DAEYALT_ESSENCE + GUARDIAN_ESSENCE
                DARK_ESSENCE in validEssenceIds ->
                    validEssenceIds
                else -> validEssenceIds
            }
        val hasEssence = checkIds.any(inv::contains)
        if (!hasEssence) {
            val essenceName = rune.input.first().name.lowercase()
            if (acceptsPureSubstitutes) {
                mesbox(
                    "You do not have any $essenceName, Daeyalt essence, or guardian essence to bind.",
                )
            } else {
                mesbox("You do not have any $essenceName to bind.")
            }
            return false
        }

        return true
    }

    suspend fun ProtectedAccess.craftOurania(xpMods: XpModifiers) {
        val daeyaltCount = inv.count(DAEYALT_ESSENCE)
        if (daeyaltCount > 0) {
            craftOuraniaDaeyalt(xpMods, daeyaltCount)
            return
        }

        if (!hasPureLikeEssence()) {
            mesbox("You do not have any pure essence to bind.")
            return
        }

        preCraft()

        val essenceCount = countPureLikeEssence()
        if (removePureLikeEssence(essenceCount).not()) {
            return
        }

        craftOuraniaBatch(essenceCount, xpMultiplier = OURANIA_XP_MULTIPLIER, xpMods = xpMods)
    }

    private suspend fun ProtectedAccess.craftOuraniaDaeyalt(
        xpMods: XpModifiers,
        daeyaltCount: Int,
    ) {
        preCraft()
        if (invDel(inv, DAEYALT_ESSENCE, daeyaltCount).failure) {
            return
        }
        val xpMultiplier = DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
        craftOuraniaBatch(daeyaltCount, xpMultiplier = xpMultiplier, xpMods = xpMods)
    }

    private suspend fun ProtectedAccess.craftOuraniaBatch(
        essenceCount: Int,
        xpMultiplier: Double,
        xpMods: XpModifiers,
    ) {
        val level = player.baseRunecraftingLvl
        var totalXp = 0.0

        repeat(essenceCount) {
            val rune = rollOuraniaRune(level)
            val multiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
            val produced = applyBonus(multiplier)
            totalXp += rune.xp / 10.0 * xpMultiplier
            invAdd(inv, rune.output.internalName, produced)
        }

        advanceRunecraftingXp(totalXp, xpMods)
    }

    suspend fun ProtectedAccess.craftCore(
        rune: RunecraftingRunesRow,
        coreItem: String,
        xpMods: XpModifiers,
    ) {
        if (!inv.contains(coreItem)) {
            return
        }

        val level = player.baseRunecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s.",
            )
            return
        }

        preCraft()

        if (invDel(inv, coreItem, 1).failure) {
            return
        }

        val multiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val produced = applyBonus(CORE_RUNE_MULTIPLIER * multiplier)
        invAdd(inv, rune.output.internalName, produced)
        advanceRunecraftingXp(rune.xp * CORE_XP_MULTIPLIER.toDouble(), xpMods)
    }

    suspend fun ProtectedAccess.craftAether(xpMods: XpModifiers) {
        val aetherRune =
            RunecraftingRunesRow.all().firstOrNull {
                it.output.internalName == "obj.aetherrune"
            } ?: return

        val level = player.baseRunecraftingLvl
        if (level < aetherRune.statReq.first().t1) {
            mesbox("You need Runecrafting level ${aetherRune.statReq.first().t1} to craft aether runes.")
            return
        }

        val guardianCount = inv.count(GUARDIAN_ESSENCE)
        val soulCount = inv.count("obj.soulrune")
        val craftCount = minOf(guardianCount, soulCount)
        if (craftCount <= 0) {
            mes("You need guardian essence and soul runes to craft aether runes.")
            return
        }

        if (!inv.contains("obj.cosmic_soul_catalyst")) {
            mes("You need an aether catalyst to craft aether runes.")
            return
        }

        preCraft()

        if (invDel(inv, GUARDIAN_ESSENCE, craftCount).failure) {
            return
        }
        if (invDel(inv, "obj.soulrune", craftCount).failure) {
            return
        }

        val wearingBinding = player.isWearing()
        val successes =
            if (wearingBinding) {
                craftCount
            } else {
                (1..craftCount).count { random.of(100) < 50 }
            }

        var produced = applyBonus(successes)
        if (successes > 0 && invDel(inv, "obj.scar_extract_scarred", 1).completed() > 0) {
            produced += getExtractBonus("obj.scar_extract_scarred")
        }

        val totalRunes = invDel(inv, "obj.cosmic_soul_catalyst", produced).completed()
        if (totalRunes > 0) {
            invAdd(inv, aetherRune.output.internalName, totalRunes)
            advanceRunecraftingXp(totalRunes * aetherRune.xp / 10.0, xpMods)
        }
        if (wearingBinding) {
            consumeChargeAfterCombo()
        }
    }

    private fun rollOuraniaRune(level: Int): RunecraftingRunesRow {
        val eligible =
            RunecraftingRunesRow.all().filter { row ->
                val output = row.output.internalName
                output !in ouraniaExcludedRunes &&
                    PURE_ESSENCE in row.input.map { it.internalName }
            }
        return eligible.randomOrNull() ?: RunecraftingRunesRow.all().first { it.output.internalName == "obj.airrune" }
    }

    private val ouraniaExcludedRunes =
        setOf(
            "obj.sunfirerune",
            "obj.wrathrune",
            "obj.aetherrune",
        )

    private fun getExtractBonus(extract: String): Int =
        RunecraftingEnums.runecrafting_extract_runes.backing.entries
            .firstOrNull { it.key.internalName == extract }?.value ?: 0

    fun getBonusMultiplier(rune: String, level: Int): Double {
        val interval = RunecraftingEnums.runecrafting_multiple_intervals.backing.entries
            .firstOrNull { it.key.internalName == rune }?.value ?: return 1.0
        return floor(level / interval.toDouble()) + 1
    }

    suspend fun ProtectedAccess.craftCombination(combo: ComboruneRecipeRow, xpMods: XpModifiers) {
        val output = combo.output ?: return
        val input = combo.input ?: return
        val talisman = combo.talisman ?: return
        val xp = combo.xp ?: return

        if (!canCraftCombo(output, input, talisman, combo.statReq.first().t1)) {
            return
        }

        preCraft()
        if (!canCraftCombo(output, input, talisman, combo.statReq.first().t1)) {
            return
        }
        val craftCount = minOf(countPureLikeEssence(), inv.count(input.internalName))
        if (craftCount <= 0) {
            return
        }

        val usingMagicImbue = player.isActive()
        val wearingBinding = player.isWearing()
        val successes = if (wearingBinding) craftCount else (1..craftCount).count { random.of(100) < 50 }
        val extractCount = minOf(successes, inv.count("obj.scar_extract_twisted"))
        val finalCount = applyBonus(successes) + extractCount * getExtractBonus("obj.scar_extract_twisted")
        val pureCount = minOf(craftCount, inv.count(PURE_ESSENCE))
        val guardianCount = craftCount - pureCount
        val crafted = player.invTransaction(inv) {
            val inventory = select(inv)
            if (!usingMagicImbue) delete(inventory, talisman.id, 1)
            if (pureCount > 0) delete(inventory, PURE_ESSENCE.asRSCM(RSCMType.OBJ), pureCount)
            if (guardianCount > 0) delete(inventory, GUARDIAN_ESSENCE.asRSCM(RSCMType.OBJ), guardianCount)
            delete(inventory, input.id, craftCount)
            if (extractCount > 0) delete(inventory, "obj.scar_extract_twisted".asRSCM(RSCMType.OBJ), extractCount)
            if (finalCount > 0) add(inventory, output.id, finalCount)
        }
        if (crafted.failure) {
            mes("You don't have enough inventory space to bind these runes.")
            return
        }
        if (successes > 0) advanceRunecraftingXp(successes * (xp.toDouble() / 10.0), xpMods)
        if (wearingBinding) consumeChargeAfterCombo()
    }

    fun ProtectedAccess.advanceRunecraftingXp(baseXp: Double, xpMods: XpModifiers) {
        statAdvance("stat.runecrafting", baseXp * xpMods.get(player, "stat.runecrafting"))
    }

    private fun ProtectedAccess.countPureLikeEssence(): Int =
        inv.count(PURE_ESSENCE) + inv.count(GUARDIAN_ESSENCE)

    private fun ProtectedAccess.hasPureLikeEssence(): Boolean = countPureLikeEssence() > 0

    private fun ProtectedAccess.removePureLikeEssence(amount: Int): Boolean {
        var remaining = amount
        if (remaining <= 0) {
            return true
        }

        val pureAvailable = inv.count(PURE_ESSENCE)
        if (pureAvailable > 0) {
            val take = minOf(pureAvailable, remaining)
            if (invDel(inv, PURE_ESSENCE, take).failure) {
                return false
            }
            remaining -= take
        }

        if (remaining > 0 && invDel(inv, GUARDIAN_ESSENCE, remaining).failure) {
            return false
        }

        return true
    }

    private suspend fun ProtectedAccess.canCraftCombo(
        output: ItemServerType,
        input: ItemServerType,
        talisman: ItemServerType,
        requiredLevel: Int?,
    ): Boolean {
        val levelReq = requiredLevel ?: return false
        val level = player.runecraftingLvl
        val outputName = output.name
        val inputName = input.name
        val talismanName = talisman.name

        if (level < levelReq) {
            mesbox("You need Runecrafting level $levelReq to craft ${outputName}s.")
            return false
        }
        if (!hasPureLikeEssence()) {
            mes("You need pure essence to craft ${outputName}s.")
            return false
        }
        if (!inv.contains(input.internalName)) {
            mes("You need ${inputName}s to craft ${outputName}s.")
            return false
        }
        if (!player.isActive() && !inv.contains(talisman.internalName)) {
            mes("You need a $talismanName to craft ${outputName}s.")
            return false
        }
        return true
    }
}
