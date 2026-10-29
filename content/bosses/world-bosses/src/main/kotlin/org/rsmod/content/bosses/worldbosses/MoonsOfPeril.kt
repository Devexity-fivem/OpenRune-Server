package org.rsmod.content.bosses.worldbosses

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Moons of Peril — the *_vis moons are 1hp pre-fight statues. The real combat
// types are multinpcs driven by varbit.pmoon_boss_hide_npc: 0 renders the
// statue, 1 the enraged form. Engaging transmogs the spawned prop to the real
// type for its stats and flips the varbit so op dispatch and the client both
// resolve to the enraged combat form.
class MoonsOfPeril @Inject constructor(private val attackManager: PlayerAttackManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.pmoon_boss_blood_moon_vis") { engage(player, it.npc, BLOOD_MOON) }
        onOpNpc1("npc.pmoon_boss_eclipse_moon_vis") { engage(player, it.npc, ECLIPSE_MOON) }
        onOpNpc1("npc.pmoon_boss_blue_moon_vis") { engage(player, it.npc, BLUE_MOON) }
        onOpNpc2("npc.pmoon_boss_blood_moon_vis") { engage(player, it.npc, BLOOD_MOON) }
        onOpNpc2("npc.pmoon_boss_eclipse_moon_vis") { engage(player, it.npc, ECLIPSE_MOON) }
        onOpNpc2("npc.pmoon_boss_blue_moon_vis") { engage(player, it.npc, BLUE_MOON) }
        onApNpc2("npc.pmoon_boss_blood_moon_vis") { engage(player, it.npc, BLOOD_MOON) }
        onApNpc2("npc.pmoon_boss_eclipse_moon_vis") { engage(player, it.npc, ECLIPSE_MOON) }
        onApNpc2("npc.pmoon_boss_blue_moon_vis") { engage(player, it.npc, BLUE_MOON) }
    }

    private fun engage(player: Player, npc: Npc, realType: Int) {
        if (npc.visType.id != realType) {
            val type = ServerCacheManager.getNpc(realType) ?: return
            npc.transmog(type, Int.MAX_VALUE)
            npc.baseHitpointsLvl = type.hitpoints
            npc.hitpoints = type.hitpoints
        }
        VarPlayerIntMapSetter.set(player, hideNpcVarbit(), 1)
        attackManager.continueCombat(player, npc)
    }

    private fun hideNpcVarbit() =
        checkNotNull(ServerCacheManager.getVarbit(HIDE_NPC_VARBIT)) {
            "varbit $HIDE_NPC_VARBIT missing"
        }

    private companion object {
        private val BLOOD_MOON = "npc.pmoon_boss_blood_moon".asRSCM(RSCMType.NPC)
        private val ECLIPSE_MOON = "npc.pmoon_boss_eclipse_moon".asRSCM(RSCMType.NPC)
        private val BLUE_MOON = "npc.pmoon_boss_blue_moon".asRSCM(RSCMType.NPC)
        private val HIDE_NPC_VARBIT = "varbit.pmoon_boss_hide_npc".asRSCM(RSCMType.VARBIT)
    }
}
