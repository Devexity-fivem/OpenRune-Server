package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import java.awt.Color
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.ui.setColour
import org.rsmod.game.entity.Npc
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.map.CoordGrid

/**
 * The `external` side of phase 2: what the boss spec can't express itself. The spec owns the
 * timeline and calls these at the right tick.
 */
internal class OcellusPhaseTwo(
    private val deps: BossDeps,
    private val hpBar: BossHpBarScript,
    private val meter: OcellusMeter,
    interactions: AiPlayerInteractions,
    private val partySize: () -> Int,
) {
    private val walls = OcellusWalls(deps)
    private val collapse = OcellusCollapse(deps, walls::clear)
    private val quadrants = OcellusQuadrants(deps, interactions, collapse.stoneLocs)
    private val void = OcellusVoid(deps)
    private var swordHitbox: Npc? = null
    private var storedDamage = 0

    fun register() {
        walls.register()
        collapse.register()
        quadrants.register()
        void.register()
        deps.extensionRegistry.register(ENTER) { enter(it.npc) }
        deps.extensionRegistry.register(FINISH) { finish(it.npc) }
        deps.extensionRegistry.register(PHASE_THREE) { phaseThree(it.npc) }
        deps.extensionRegistry.register(OPEN) { open(it.npc) }
        deps.extensionRegistry.register(DISCHARGE) { discharge(it.npc) }
        deps.extensionRegistry.register(CLOSE) { removeHitbox() }
        deps.extensionRegistry.register(SET_LEVEL) { it.npc.setCombatLevel(PHASE_TWO_LEVEL) }
    }

    fun isOutsideQuadrant(npc: Npc, tile: CoordGrid): Boolean = quadrants.isOutside(npc, tile)

    fun describeQuadrants(): String = quadrants.describe()

    fun absorb(damage: Int) {
        storedDamage += damage
    }

    fun clear() {
        walls.clear()
        collapse.clear()
        quadrants.clear()
        void.clear()
        removeHitbox()
        storedDamage = 0
    }

    private fun playSong(npc: Npc, song: Int) {
        for (fighter in OcellusArena.players(npc, deps.playerList)) {
            fighter.midiSong(song, fadeOutSpeed = SONG_FADE_SPEED, fadeInDelay = SONG_FADE_SPEED)
        }
    }

    private fun finish(npc: Npc) {
        npc.hideAllOps()
        playSong(npc, PHASE_THREE_SONG)
    }

    private fun phaseThree(npc: Npc) {
        val type = ServerCacheManager.getNpc("npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC))!!
        npc.moveRestrict = type.moveRestrict
        npc.apRequiresLineOfSight = false
        npc.chasesWhileInApRange = true
        npc.setCombatLevel(PHASE_TWO_LEVEL)
        npc.hideAllOps()
    }

    private fun enter(npc: Npc) {
        playSong(npc, PHASE_TWO_SONG)
        meter.hide(npc)
        val type = ServerCacheManager.getNpc("npc.tfa_ocellus_p2".asRSCM(RSCMType.NPC))!!
        npc.copyStats(type)
        npc.baseHitpointsLvl = POOL_HP_BASE + POOL_HP_PER_PLAYER * partySize()
        npc.hitpoints = npc.baseHitpointsLvl
        npc.moveRestrict = type.moveRestrict
        npc.apRequiresLineOfSight = false
        npc.setCombatLevel(PHASE_TWO_TRANSFORM_LEVEL)
        for (fighter in OcellusArena.players(npc, deps.playerList)) {
            hpBar.onUpdate(fighter, npc)
            hpBar.onRefresh(fighter, npc) {
                setColour("component.hpbar_hud:health_bar_back", BAR_BACK)
                setColour("component.hpbar_hud:health_bar_sliding", BAR_SLIDING)
                setColour("component.hpbar_hud:health_bar_remaining", BAR_REMAINING)
            }
        }
    }

    private fun open(npc: Npc) {
        npc.showHitmark(hitmark(HITMARK_SHIELD_MAX, npc.baseHitpointsLvl))
        showPoolBar(npc)
        removeHitbox()
        val hitbox = Npc("npc.tfa_ocellus_p2_sword_hitbox", npc.coords)
        hitbox.moveRestrict = MoveRestrict.NoMove
        hitbox.baseHitpointsLvl = HITBOX_HP
        hitbox.hitpoints = HITBOX_HP
        deps.npcRepo.add(hitbox, Int.MAX_VALUE)
        swordHitbox = hitbox
        storedDamage = 0
    }

    private fun discharge(npc: Npc) {
        val damage = (storedDamage * DISCHARGE_SHARE).toInt().coerceAtMost(npc.hitpoints - 1)
        storedDamage = 0
        npc.hitpoints -= damage
        npc.showHitmark(hitmark(HITMARK_REGULAR_TINT, damage))
        showPoolBar(npc)
        for (fighter in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(fighter, npc)
    }

    private fun showPoolBar(npc: Npc) {
        val fill = (npc.hitpoints.toLong() * BAR_SEGMENTS / npc.baseHitpointsLvl).toInt()
        npc.showHeadbar(Headbar.fromNoSource(HEADBAR_HEALTH_100, HEADBAR_HEALTH_100, fill, fill, 0, 0))
    }

    private fun removeHitbox() {
        swordHitbox?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        swordHitbox = null
    }

    private fun hitmark(id: Int, damage: Int): Hitmark =
        Hitmark.fromNoSource(self = id, source = id, public = id, damage = damage, delay = 0)

    companion object {
        const val ENTER = "ocellus.p2_enter"
        const val OPEN = "ocellus.p2_open"
        const val DISCHARGE = "ocellus.p2_discharge"
        const val CLOSE = "ocellus.p2_close"
        const val SET_LEVEL = "ocellus.p2_set_level"

        const val WALLS_SPAWN = OcellusWalls.SPAWN
        const val WALLS_TELEGRAPH = OcellusWalls.TELEGRAPH
        const val WALLS_CLOSED_TELEGRAPH = OcellusWalls.CLOSED_TELEGRAPH
        const val WALLS_FIRE = OcellusWalls.FIRE
        const val POOLS = OcellusWalls.POOLS
        const val POOLS_DETONATE = OcellusWalls.POOLS_DETONATE
        const val PORTALS_SPAWN = OcellusWalls.PORTALS_SPAWN
        const val PORTALS_TICK = OcellusWalls.PORTALS_TICK
        const val PORTALS_END = OcellusWalls.PORTALS_END
        const val WALLS_CLEAR = OcellusWalls.CLEAR
        const val WALLS_DESPAWN = OcellusWalls.DESPAWN

        const val QUADRANT_ENTER = OcellusQuadrants.ENTER
        const val QUADRANT_FREEZE = OcellusQuadrants.FREEZE
        const val QUADRANT_THAW = OcellusQuadrants.THAW
        const val QUADRANT_BARRIER_SPAWN = OcellusQuadrants.BARRIER_SPAWN
        const val QUADRANT_BARRIER = OcellusQuadrants.BARRIER
        const val QUADRANT_BARRIER_DESPAWN = OcellusQuadrants.BARRIER_DESPAWN
        const val QUADRANT_BARRIER_REMOVE = OcellusQuadrants.BARRIER_REMOVE
        const val QUADRANT_COLLAPSE = OcellusQuadrants.COLLAPSE
        const val QUADRANT_STONES_FALL = OcellusQuadrants.STONES_FALL
        const val QUADRANT_STONES_BLOCK = OcellusQuadrants.STONES_BLOCK


        const val FINISH = "ocellus.p2_finish"
        const val PHASE_THREE = "ocellus.p3_enter"
        const val SWORD_RAISE = OcellusCollapse.SWORD_RAISE
        const val SWORD_IDLE = OcellusCollapse.SWORD_IDLE
        const val SWORD_DROP = OcellusCollapse.SWORD_DROP
        const val ARENA_CRACK = OcellusCollapse.CRACK
        const val ARENA_COLLAPSE = OcellusCollapse.COLLAPSE
        const val GAP_WALLS = OcellusCollapse.GAP_WALLS

        private const val PHASE_TWO_SONG = 893
        private const val PHASE_THREE_SONG = 894
        private const val SONG_FADE_SPEED = 60

        private const val PHASE_TWO_TRANSFORM_LEVEL = 1424
        private const val PHASE_TWO_LEVEL = 1601
        private const val POOL_HP_BASE = 1020
        private const val POOL_HP_PER_PLAYER = 2200
        private const val HITMARK_SHIELD_MAX = 44
        private const val HITMARK_REGULAR_TINT = 17
        const val HITBOX_HEADBAR = 0
        private val BAR_BACK = Color(200, 0, 0)
        private val BAR_SLIDING = Color(0, 144, 0)
        private val BAR_REMAINING = Color(0, 200, 0)
        private const val HEADBAR_HEALTH_100 = 19
        private const val BAR_SEGMENTS = 100
        private const val HITBOX_HP = 30000
        private const val DISCHARGE_SHARE = 0.5
    }
}
