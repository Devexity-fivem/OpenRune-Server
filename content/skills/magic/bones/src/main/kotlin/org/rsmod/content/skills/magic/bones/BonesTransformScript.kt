package org.rsmod.content.skills.magic.bones

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BonesTransformScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    private val convertibleBones = linkedMapOf<String, Int>()
    private val heldBones = mutableSetOf<String>()

    override fun ScriptContext.startup() {
        // Bones convert up to and including the big-bones prayer tier; ashes are
        // not bones and higher-tier bones are rejected outright.
        for (row in SkillPrayerRow.all()) {
            if (row.ashes) {
                continue
            }
            val name = row.item.internalName
            heldBones += name
            if (row.expTenths <= MAX_CONVERTIBLE_XP_TENTHS) {
                convertibleBones[name] = 1
            }
        }
        convertibleBones += MAGE_TRAINING_BONES

        for (bonesSpell in BonesSpell.entries) {
            val spell = bonesSpell.resolveSpell() ?: continue
            onIfOverlayButton(spell.component) { cast(spell, bonesSpell) }
        }
    }

    private fun BonesSpell.resolveSpell(): MagicSpell? {
        val item = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(item)
    }

    private fun ProtectedAccess.cast(spell: MagicSpell, bonesSpell: BonesSpell) {
        if (actionDelay > mapClock) {
            return
        }
        if (convertibleBones.keys.none { inv.contains(it) }) {
            if (heldBones.any { inv.contains(it) }) {
                mes(bonesSpell.unsuitableMessage)
            } else {
                mes(NO_BONES_MESSAGE)
            }
            return
        }
        val castResult = runes.attemptCast(player, spell)
        if (castResult.isFailure()) {
            return
        }
        actionDelay = mapClock + CAST_DELAY
        anim(CAST_ANIM)
        spotanim(CAST_SPOTANIM)
        soundSynth(CAST_SOUND)
        for ((bone, fruitPerBone) in convertibleBones) {
            convert(bone, bonesSpell.fruit, fruitPerBone)
        }
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.convert(bone: String, fruit: String, fruitPerBone: Int) {
        val count = inv.count(bone)
        if (count == 0 || invDel(inv, bone, count).failure) {
            return
        }
        invAdd(inv, fruit, count * fruitPerBone)
    }

    private enum class BonesSpell(
        val spellObj: String,
        val fruit: String,
        val unsuitableMessage: String,
    ) {
        Bananas(
            "obj.15_bones_bananas",
            "obj.banana",
            "The bones you're holding aren't suitable for bananification.",
        ),
        Peaches(
            "obj.60_bones_peaches",
            "obj.peach",
            "The bones you're holding aren't suitable for peachifying.",
        ),
    }

    private companion object {
        const val CAST_ANIM = "seq.human_castbonestobananas"
        const val CAST_SPOTANIM = "spotanim.bonestobananas_casting"
        const val CAST_SOUND = "synth.bones_to_bananas_all"
        const val CAST_DELAY = 4
        const val MAX_CONVERTIBLE_XP_TENTHS = 150
        const val NO_BONES_MESSAGE = "You aren't holding any bones!"

        val MAGE_TRAINING_BONES =
            mapOf(
                "obj.magictraining_bones1" to 1,
                "obj.magictraining_bones2" to 2,
                "obj.magictraining_bones3" to 3,
                "obj.magictraining_bones4" to 4,
            )
    }
}
