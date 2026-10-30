package org.rsmod.content.skills.magic.spellbook.altars

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DarkAltarScript @Inject constructor(private val spellbooks: MagicSpellbookManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.archeus_altar_dark") { commune() }
    }

    private suspend fun ProtectedAccess.commune() {
        arriveDelay()
        anim("seq.human_pray")
        if (spellbooks.activeSpellbook(player) == Spellbook.Arceuus) {
            spellbooks.setSpellbook(player, Spellbook.Standard)
            mes("Your magical energies revert to normal.")
        } else {
            spellbooks.setSpellbook(player, Spellbook.Arceuus)
            mes("You feel a strange power infuse your body...")
        }
    }
}
