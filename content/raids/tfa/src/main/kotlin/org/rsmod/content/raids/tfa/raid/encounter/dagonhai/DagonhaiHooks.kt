package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.FINAL
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

internal val DAGONHAI_TRIO_IDS by lazy {
    setOf(
        "npc.tfa_dagonhai_leader".asRSCM(RSCMType.NPC),
        "npc.tfa_dagonhai_leader_unshielded".asRSCM(RSCMType.NPC),
        "npc.tfa_dagonhai_minion_1".asRSCM(RSCMType.NPC),
        "npc.tfa_dagonhai_minion_1_unshielded".asRSCM(RSCMType.NPC),
        "npc.tfa_dagonhai_minion_2".asRSCM(RSCMType.NPC),
        "npc.tfa_dagonhai_minion_2_unshielded".asRSCM(RSCMType.NPC),
    )
}

/**
 * The trio shares one pool: hits never kill them until the cauldron's final explosion marks them
 * with [FINAL]. Rifts and imps die normally.
 */
internal class DagonhaiHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int =
        if (npc.visType.id in DAGONHAI_TRIO_IDS && npc.vars[FINAL] == 0) 1 else 0
}

/** Retired (dead) ritualists can't be attacked again. */
internal class DagonhaiAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.visType.id in DAGONHAI_TRIO_IDS && npc.ignoreCombatInteractions) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }
}
