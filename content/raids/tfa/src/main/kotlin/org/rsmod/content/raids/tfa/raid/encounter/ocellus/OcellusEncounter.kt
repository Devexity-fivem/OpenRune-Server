package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

@Singleton
class OcellusEncounter
@Inject
constructor(
    private val deps: BossDeps,
    private val interactions: AiPlayerInteractions,
    private val hpBar: BossHpBarScript,
) {
    internal val meter = OcellusMeter(deps, hpBar)
    internal val phaseTwo = OcellusPhaseTwo(deps, hpBar, meter) { partySize }
    internal val debug = OcellusDebug(deps, { boss }) { phaseTwo.describeQuadrants() }

    private var boss: Npc? = null
    private var started = false
    private var partySize = 1

    fun reset() {
        started = false
        debug.reset()
        phaseTwo.clear()
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
        start(npc, player, partySize = 1)
        player.mes("Challenge started: Ocellus.")
    }

    fun skipToPhaseThree(): Boolean {
        val npc = boss?.takeIf { it.isSlotAssigned && started } ?: return false
        if (npc.visType.id != "npc.tfa_ocellus_p2".asRSCM(RSCMType.NPC)) return false
        npc.vars[OcellusVarns.THRESHOLD] = OcellusVarns.SKIP_PHASE_TWO
        return true
    }

    fun setHealthPercent(percent: Int): Boolean {
        val npc = boss?.takeIf { it.isSlotAssigned && started } ?: return false
        npc.hitpoints = maxOf(1, (npc.baseHitpointsLvl.toLong() * percent / 100).toInt())
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(fighter, npc)
        return true
    }

    /** Hands the fight to the boss spec, which starts on its first combat tick. */
    private fun start(npc: Npc, player: Player, partySize: Int) {
        started = true
        this.partySize = partySize
        val shield = ServerCacheManager.getNpc("npc.tfa_ocellus_p1".asRSCM(RSCMType.NPC))!!
        npc.transmog(shield, Int.MAX_VALUE)
        npc.copyStats(shield)
        npc.baseHitpointsLvl = SHIELD_HP_BASE + SHIELD_HP_PER_PLAYER * partySize
        npc.hitpoints = npc.baseHitpointsLvl
        npc.moveRestrict = shield.moveRestrict
        npc.say("Now... ascension...")
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onOpen(fighter, npc)
        meter.show(npc)
        npc.apPlayer2(player, interactions)
        npc.setHuntMode(checkNotNull(ServerCacheManager.getHunt(HUNT_CONSTANT_RANGED)))
        npc.setHunt(shield.huntRange)
        logger.info {
            "[ocellus] started: slot=${npc.slotId} hp=${npc.hitpoints} attackRange=${npc.attackRange} " +
                "moveRestrict=${npc.moveRestrict} engaging ${player.displayName} at ${player.coords}"
        }
    }

    companion object {
        private val logger = InlineLogger()

        val ENTRANCE: CoordGrid = CoordGrid(4128, 5658, 1)
        private val INTRO_SPAWN = CoordGrid(4126, 5669, 1)

        /** The middle tile of the room, where the walls cross. */
        val CENTRE: CoordGrid = INTRO_SPAWN.translate(1, 4)

        private const val HUNT_CONSTANT_RANGED = 2
        private const val SHIELD_HP_BASE = 480
        private const val SHIELD_HP_PER_PLAYER = 1050
    }
}
