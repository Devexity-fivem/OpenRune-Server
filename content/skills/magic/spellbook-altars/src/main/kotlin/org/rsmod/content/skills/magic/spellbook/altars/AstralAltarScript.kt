package org.rsmod.content.skills.magic.spellbook.altars

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AstralAltarScript @Inject constructor(private val spellbooks: MagicSpellbookManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2("loc.astral_altar") { prayAt() }
    }

    private suspend fun ProtectedAccess.prayAt() {
        arriveDelay()
        if (!QuestRequirements.hasCompleted(player, "quest_lunardiplomacy")) {
            mes("You need to complete Lunar Diplomacy to use this altar.")
            return
        }
        anim("seq.human_pray")
        if (spellbooks.activeSpellbook(player) == Spellbook.Lunars) {
            spellbooks.setSpellbook(player, Spellbook.Standard)
            mes("Lunar spells deactivated!")
        } else {
            spellbooks.setSpellbook(player, Spellbook.Lunars)
            mes("Lunar spells activated!")
        }
    }
}
