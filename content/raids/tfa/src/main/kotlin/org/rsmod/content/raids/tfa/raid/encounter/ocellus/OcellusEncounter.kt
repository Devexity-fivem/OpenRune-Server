package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

@Singleton
class OcellusEncounter @Inject constructor(private val deps: BossDeps) {
    private var boss: Npc? = null
    private var phaseOne: OcellusPhaseOne? = null
    private var challenger: Player? = null
    private var generation = 0

    private val started: Boolean
        get() = phaseOne != null

    fun reset() {
        generation++
        phaseOne = null
        boss?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        val npc = Npc("npc.tfa_ocellus_intro", INTRO_SPAWN)
        npc.respawnDir = Direction.North
        npc.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        boss = npc
    }

    fun ready(player: Player) {
        if (started) return
        val npc = boss?.takeIf { it.isSlotAssigned } ?: return
        challenger = player
        start(npc, partySize = 1)
        player.mes("Challenge started: Ocellus.")
    }

    private fun start(npc: Npc, partySize: Int) {
        val shield = ServerCacheManager.getNpc("npc.tfa_ocellus_p1".asRSCM(RSCMType.NPC))!!
        npc.transmog(shield, Int.MAX_VALUE)
        npc.copyStats(shield)
        npc.baseHitpointsLvl = SHIELD_HP_BASE + SHIELD_HP_PER_PLAYER * partySize
        npc.hitpoints = npc.baseHitpointsLvl
        npc.moveRestrict = shield.moveRestrict
        npc.ignoreCombatInteractions = true
        npc.say("Now... ascension...")
        val phase = OcellusPhaseOne(deps, npc, ::onShieldBroken)
        phaseOne = phase
        phase.start()
        schedule(generation)
    }

    private fun schedule(gen: Int) {
        deps.worldQueues.add(1) {
            if (gen != generation) return@add
            phaseOne?.tick()
            schedule(gen)
        }
    }

    private fun onShieldBroken() {
        challenger?.mes("Ocellus's shield breaks. Phase 2 is not implemented yet.")
        generation++
    }

    companion object {
        val ENTRANCE: CoordGrid = CoordGrid(4128, 5658, 1)
        private val INTRO_SPAWN = CoordGrid(4126, 5669, 1)

        private const val SHIELD_HP_BASE = 480
        private const val SHIELD_HP_PER_PLAYER = 1050
    }
}
