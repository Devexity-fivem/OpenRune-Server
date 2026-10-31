package org.rsmod.content.areas.misc.apeatoll

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.plugin.module.PluginModule

class ApeAtollModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(GreegreeAttackBlock::class.java)
    }
}
