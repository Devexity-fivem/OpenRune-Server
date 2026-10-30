package org.rsmod.content.skills.magic.arceuus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NoopSpellsScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (spell in NoopSpell.entries) {
            onIfOverlayButton(spell.component) { cast(spell) }
        }
    }

    private fun ProtectedAccess.cast(spell: NoopSpell) {
        val spellObj = ServerCacheManager.getItem(spell.obj.asRSCM(RSCMType.OBJ)) ?: return
        val magicSpell = spells.getObjSpell(spellObj) ?: return

        if (runes.attemptCast(player, magicSpell).isFailure()) {
            return
        }

        statAdvance("stat.magic", magicSpell.castXp)
        anim(spell.anim)
        spotanim(spell.spotanim)
    }

    internal enum class NoopSpell(
        val obj: String,
        val component: String,
        val anim: String,
        val spotanim: String,
    ) {
        ResurrectCrops(
            obj = "obj.br_swordfish",
            component = "component.magic_spellbook:resurrect_crops",
            anim = "seq.res_necromancy_plant_spell",
            spotanim = "spotanim.arceuus_crop_res_spotanim",
        ),
    }
}
