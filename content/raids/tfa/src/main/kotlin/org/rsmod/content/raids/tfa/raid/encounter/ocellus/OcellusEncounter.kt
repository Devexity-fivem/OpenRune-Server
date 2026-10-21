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

    private var boss: Npc? = null
    private var challenger: Player? = null
    private var started = false

    fun reset() {
        started = false
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
        start(npc, player, partySize = 1)
        player.mes("Challenge started: Ocellus.")
    }

    /** Hands the fight to the boss spec, which starts on its first combat tick. */
    private fun start(npc: Npc, player: Player, partySize: Int) {
        started = true
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
        logger.info {
            "[ocellus] started: slot=${npc.slotId} hp=${npc.hitpoints} attackRange=${npc.attackRange} " +
                "moveRestrict=${npc.moveRestrict} engaging ${player.displayName} at ${player.coords}"
        }
    }

    fun onShieldBroken() {
        logger.info { "[ocellus] shield broken" }
        boss?.let(meter::hide)
        challenger?.mes("Ocellus's shield breaks. Phase 2 is not implemented yet.")
    }

    companion object {
        private val logger = InlineLogger()

        val ENTRANCE: CoordGrid = CoordGrid(4128, 5658, 1)
        private val INTRO_SPAWN = CoordGrid(4126, 5669, 1)

        private const val SHIELD_HP_BASE = 480
        private const val SHIELD_HP_PER_PLAYER = 1050
    }
}
