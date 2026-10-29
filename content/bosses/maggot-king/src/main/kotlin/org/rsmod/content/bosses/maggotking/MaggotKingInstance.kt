package org.rsmod.content.bosses.maggotking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The darkwood lair copy the king fights in. The overworld doorway teleports the player into the
 * player strip south of the bone wall; the lair's own doorway teleports them back out.
 */
class MaggotKingInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val playerList: PlayerList,
    private val bossHpBar: BossHpBarScript,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_maggot_king"

    override fun area(): InstanceArea = INSTANCE

    override fun runsPreludeOnFreshRun(): Boolean = true

    override fun bestTimeVarp(): String = BEST_TIME

    override fun ScriptContext.configure() {
        onEnterPrelude { result, enter ->
            val session =
                when (result) {
                    is InstanceManager.Result.Created -> result.session
                    is InstanceManager.Result.Joined -> result.session
                    else -> return@onEnterPrelude
                }
            mesbox(CRAWL_MESSAGE)
            fadeOverlay(0, 255, 0, 0, FADE_CLIENT_DURATION)
            delay(ENTRY_FADE_CYCLES)
            minimapHideMap()
            enter()
            player.lairEntered = true
            landInStrip(session)
            faceDirection(Direction.North)
            delay(ENTRY_REVEAL_CYCLES)
            minimapReset()
            fadeOverlay(0, 0, 0, 255, FADE_CLIENT_DURATION)
            closeFadeOverlay()
            // The king is aggressive: it comes for anyone inside the lair, including a player
            // re-entering a live session after death.
            engage(session)
        }
        onEnterObject { defaultInstanceEntry() }
        onExitObject {
            bossHpBar.onDeathClose(player)
            fadeOverlay(0, 255, 0, 0, FADE_CLIENT_DURATION)
            delay(HIDE_DELAY_CYCLES)
            minimapHideMap()
            defaultLeaveFlow()
            delay(REVEAL_DELAY_CYCLES)
            minimapReset()
            faceDirection(Direction.South)
            fadeOverlay(0, 0, 0, 255, FADE_CLIENT_DURATION)
            closeFadeOverlay()
        }
        onInstancePlayerLeave { bossHpBar.onDeathClose(player) }
    }

    // The copy lands the player inside the strip just south of the doorway rather than wherever
    // the region default puts them.
    private fun ProtectedAccess.landInStrip(session: InstanceSession) {
        val landing = manager.resolveCoord(session, STRIP_LANDING) ?: return
        telejump(landing, org.rsmod.api.player.hook.TeleportType.Exempt)
    }

    // The king is aggressive: it opens on the player as soon as the entry cutscene ends.
    private fun engage(session: InstanceSession) {
        val king = manager.npcsForInstance(session.id).firstOrNull { it.type.id == BOSS_ID } ?: return
        val player =
            session.occupants.firstNotNullOfOrNull { uuid ->
                playerList.firstOrNull { it.uuid == uuid }
            } ?: return
        bossHpBar.onOpen(player, king)
        king.apPlayer2(player, aiPlayerInteractions)
    }

    companion object {
        const val INSTANCE_KEY = "maggot_king"
        const val BEST_TIME = "varp.maggot_king_best_time"
        private val BOSS_ID = MaggotKingEncounter.BOSS.asRSCM(RSCMType.NPC)

        private const val CRAWL_MESSAGE = "You crawl through the darkwood trees..."
        private const val FADE_CLIENT_DURATION = 50
        private const val ENTRY_FADE_CYCLES = 2
        private const val ENTRY_REVEAL_CYCLES = 1
        private const val HIDE_DELAY_CYCLES = 1
        private const val REVEAL_DELAY_CYCLES = 2

        private val STRIP_LANDING = CoordGrid(2911, 8034)

        private var Player.lairEntered by boolVarBit("varbit.maggot_king_entered")

        private val INSTANCE =
            InstanceArea.copyRegions(
                centerRegionId = 11645,
                npcSpawns =
                    listOf(InstanceNpc(MaggotKingEncounter.BOSS, MaggotKingEncounter.BOSS_SPAWN)),
            )
    }
}
