package org.rsmod.content.raids.tfa

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothAttackHook
import org.rsmod.content.raids.tfa.raid.encounter.dreadnoth.DreadnothHpFloor
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianAttackHook
import org.rsmod.content.raids.tfa.raid.encounter.guardian.GuardianHpFloor
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusAttackHook
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusShieldFloor
import org.rsmod.plugin.module.PluginModule

class TfaModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcHitpointsFloorHook>(OcellusShieldFloor::class.java)
        addSetBinding<NpcHitpointsFloorHook>(GuardianHpFloor::class.java)
        addSetBinding<NpcHitpointsFloorHook>(DreadnothHpFloor::class.java)
        addSetBinding<NpcAttackValidateHook>(OcellusAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(GuardianAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(DreadnothAttackHook::class.java)
    }
}
