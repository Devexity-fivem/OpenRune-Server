package org.rsmod.content.raids.tfa

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusAttackHook
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusShieldFloor
import org.rsmod.plugin.module.PluginModule

class TfaModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcHitpointsFloorHook>(OcellusShieldFloor::class.java)
        addSetBinding<NpcAttackValidateHook>(OcellusAttackHook::class.java)
    }
}
