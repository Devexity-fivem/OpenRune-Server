package org.rsmod.content.areas.misc.tutorial

import org.rsmod.api.player.hook.PlayerInvUpdateHook
import org.rsmod.plugin.module.PluginModule

class TutorialIslandModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerInvUpdateHook>(TutorialIslandInvHook::class.java)
    }
}
