package org.rsmod.content.bosses.worldbosses

import jakarta.inject.Inject
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull
import org.rsmod.plugin.module.PluginModule

// Grotesque Guardians — Dawn fights airborne in every phase she appears, so she
// can only be engaged with ranged/magic or a halberd (no salamander exception;
// OSRS restricts her to halberds only, unlike Kree'arra).
public class WorldBossesModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(DawnMeleeBlockHook::class.java)
    }
}

internal class DawnMeleeBlockHook @Inject constructor(private val types: AttackTypes) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!npc.visType.internalName.startsWith(DAWN_PREFIX)) {
            return NpcAttackValidateResult.Pass
        }

        val type = types.get(player)
        if (type != null && !type.isMelee) {
            return NpcAttackValidateResult.Pass
        }

        val weapon = getOrNull(player.righthand)
        if (weapon != null && weapon.isCategoryType("category.halberd")) {
            return NpcAttackValidateResult.Pass
        }

        return NpcAttackValidateResult.Deny(
            "${npc.name} is flying too high for you to reach with melee."
        )
    }

    private companion object {
        private const val DAWN_PREFIX = "npc.gargboss_dawn"
    }
}
