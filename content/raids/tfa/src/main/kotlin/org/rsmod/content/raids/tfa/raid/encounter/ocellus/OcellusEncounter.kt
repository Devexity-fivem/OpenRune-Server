package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.content.raids.tfa.raid.reward.TfaVault
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

@Singleton
class OcellusEncounter
@Inject
constructor(
    private val deps: BossDeps,
    private val interactions: AiPlayerInteractions,
    private val hpBar: BossHpBarScript,
    private val vault: TfaVault,
    energy: SpecialAttackEnergy,
) {
    internal val meter = OcellusMeter(deps, hpBar)
    internal val phaseTwo = OcellusPhaseTwo(deps, hpBar, meter, interactions, energy) { partySize }

    private var boss: Npc? = null
    private var started = false
    private var startedAt = 0
    private var partySize = 1

    fun reset() {
        started = false
        phaseTwo.clear()
        boss?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        val npc = Npc("npc.tfa_ocellus_intro", INTRO_SPAWN)
        npc.respawnDir = Direction.North
        npc.moveRestrict = MoveRestrict.NoMove
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        boss = npc
        phaseTwo.placeCensers(CENTRE)
    }

    /** Lights a censer for [player], returning why it can't be if it can't. */
    fun lightCenser(at: BoundLocInfo): String? {
        val npc = boss?.takeIf { it.isSlotAssigned && started } ?: return OcellusCensers.NOTHING
        return phaseTwo.lightCenser(npc, at)
    }

    fun ready(player: Player) {
        if (started) return
        val npc = boss?.takeIf { it.isSlotAssigned } ?: return
        start(npc, player, partySize = 1)
        player.mes("Challenge started: Ocellus.")
    }

    /** Testing shortcut: resets the room, starts the fight and sends it straight to phase 3. */
    fun startAtPhaseThree(player: Player): Boolean {
        ready(player)
        val npc = boss?.takeIf { it.isSlotAssigned && started } ?: return false
        npc.vars[OcellusVarns.THRESHOLD] = OcellusVarns.SKIP_TO_PHASE_THREE
        return true
    }

    fun setHealthPercent(percent: Int): Boolean {
        val npc = boss?.takeIf { it.isSlotAssigned && started } ?: return false
        npc.hitpoints = maxOf(1, (npc.baseHitpointsLvl.toLong() * percent / 100).toInt())
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(fighter, npc)
        return true
    }

    fun complete(npc: Npc) {
        val ticks = deps.mapClock.cycle - startedAt
        phaseTwo.stopHazards()
        phaseTwo.playVictorySong(npc)
        val fighters = OcellusArena.players(npc, deps.playerList)
        vault.begin(fighters, npc.spawnCoords.translate(BLADE_DX, BLADE_DZ))
        for (fighter in fighters) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(ticks, precise)
            fighter.mes("Challenge complete: Ocellus. Duration: <col=ef1020>$duration</col>")
        }
        deps.worldQueues.add(HUD_FADE_DELAY) {
            for (fighter in fighters) hpBar.fadeOut(fighter)
            deps.worldQueues.add(HUD_HIDE_DELAY) { fighters.forEach(hpBar::onDeathClose) }
        }
    }

    /** Hands the fight to the boss spec, which starts on its first combat tick. */
    private fun start(npc: Npc, player: Player, partySize: Int) {
        started = true
        startedAt = deps.mapClock.cycle
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
    }

    companion object {
        val ENTRANCE: CoordGrid = CoordGrid(4128, 5658, 1)
        private val INTRO_SPAWN = CoordGrid(4126, 5669, 1)

        /** The middle tile of the room, where the walls cross. */
        val CENTRE: CoordGrid = INTRO_SPAWN.translate(1, 4)

        /** A safe floor tile in the south-west quadrant, clear of the cross and the boss's spot. */
        val PHASE_THREE_SPOT: CoordGrid = CENTRE.translate(-4, -4)

        private const val BLADE_DX = -6
        private const val BLADE_DZ = 9
        private const val HUD_FADE_DELAY = 10
        private const val HUD_HIDE_DELAY = 4
        private const val HUNT_CONSTANT_RANGED = 2
        private const val SHIELD_HP_BASE = 480
        private const val SHIELD_HP_PER_PLAYER = 1050
    }
}
