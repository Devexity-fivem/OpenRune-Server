package org.rsmod.content.minigames.chompyhunting

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.plugin.module.PluginModule

class ChompyHuntingModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(ChompyAttackGate::class.java)
        addSetBinding<NpcDeathKillHook>(ChompyKillHook::class.java)
    }
}
