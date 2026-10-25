package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the final phase's overhead icons do. Under a deflect the hit still lands on Ocellus but
 * [REFLECT_PERCENT] of it comes back at whoever threw it, for the deflected style only. Under soul
 * split every hit Ocellus lands on a player in the style it is swinging heals him by the damage
 * dealt. The spec writes [VARN] beside each icon it raises.
 */
internal class OcellusCurses(private val deps: BossDeps, private val npcList: NpcList) {
    private val finalForm = "npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC)

    fun register(ctx: ScriptContext) {
        with(ctx) {
            onEvent<PlayerHitEvents.Impact> {
                if (!hit.isFromNpc) return@onEvent
                val boss = hit.resolveNpcSource(npcList) ?: return@onEvent
                soulSplit(boss, hit.type, hit.damage)
            }
        }
    }

    fun reflect(event: NpcHitEvents.Impact) {
        val boss = event.npc
        val hit = event.hit
        if (!hit.isFromPlayer || hit.damage <= 0) return
        val deflected =
            when (boss.vars[VARN]) {
                DEFLECT_MISSILES -> HitType.Ranged
                DEFLECT_MELEE -> HitType.Melee
                else -> return
            }
        if (hit.type != deflected) return
        val attacker = hit.resolvePlayerSource(deps.playerList) ?: return
        if (!attacker.isValidTarget()) return
        val damage = maxOf(1, hit.damage * REFLECT_PERCENT / PERCENT)
        attacker.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun soulSplit(boss: Npc, type: HitType, damage: Int) {
        if (boss.visType.id != finalForm || boss.vars[VARN] != SOUL_SPLIT) return
        if (type != HitType.Melee && type != HitType.Ranged && type != HitType.Magic) return
        if (damage > 0 && boss.hitpoints > 0) boss.heal(damage, showHitsplat = true)
    }

    companion object {
        const val VARN = "varn.ocellus_curse"
        const val NONE = 0
        const val DEFLECT_MISSILES = 1
        const val DEFLECT_MELEE = 2
        const val SOUL_SPLIT = 3

        // PLACEHOLDER: no fighter-side capture of the final phase shows what a deflect returns.
        private const val REFLECT_PERCENT = 20
        private const val PERCENT = 100
    }
}
