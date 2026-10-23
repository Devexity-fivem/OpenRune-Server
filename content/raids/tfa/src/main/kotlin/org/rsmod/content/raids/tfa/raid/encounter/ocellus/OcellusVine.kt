package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * The void vine. A random player in the boss's quadrant is rooted on the spot by an attackable
 * vine that only another player can bring down. If it is still standing when its time is up it
 * lets go, and the rooted player takes the hit.
 *
 * The vine is an invisible npc: it is the thing to click, and the entangle spotanims on it are
 * what the party sees. It stands on the rooted player's own tile, so they can't target it.
 */
internal class OcellusVine(private val deps: BossDeps) {
    private val roots = IdentityHashMap<Npc, Player>()

    fun register() {
        deps.extensionRegistry.register(SPAWN) { spawn(it.npc) }
    }

    fun clear() {
        for ((vine, victim) in roots.entries.toList()) {
            roots.remove(vine)
            victim.frozen = false
            if (vine.isSlotAssigned) deps.npcRepo.del(vine, Int.MAX_VALUE)
        }
    }

    private fun spawn(boss: Npc) {
        val candidates = OcellusArena.quadrantPlayers(boss, deps.playerList)
        if (candidates.isEmpty()) return
        val victim = deps.random.pick(candidates)

        val vine = Npc(VINE, victim.coords)
        vine.moveRestrict = MoveRestrict.NoMove
        vine.mode = NpcMode.None
        deps.npcRepo.add(vine, Int.MAX_VALUE)
        if (HIDE_VINE) vine.infoProtocol.setTransparency(0, 1, INVISIBLE, INVISIBLE, true)
        roots[vine] = victim

        victim.frozen = true
        victim.abortRoute()
        vine.spotanim(START_SPOT)
        deps.worldQueues.add(IDLE_AT) { if (roots[vine] === victim) vine.spotanim(IDLE_SPOT) }
        watch(boss, vine, victim, LIFETIME)
    }

    private fun watch(boss: Npc, vine: Npc, victim: Player, ticksLeft: Int) {
        deps.worldQueues.add(1) {
            if (roots[vine] !== victim) return@add
            when {
                vine.hitpoints <= 0 || !vine.isSlotAssigned -> release(vine, victim)
                ticksLeft <= 1 -> expire(boss, vine, victim)
                else -> watch(boss, vine, victim, ticksLeft - 1)
            }
        }
    }

    private fun release(vine: Npc, victim: Player) {
        roots.remove(vine)
        victim.frozen = false
        if (vine.isSlotAssigned) vine.spotanim(END_SPOT)
    }

    private fun expire(boss: Npc, vine: Npc, victim: Player) {
        release(vine, victim)
        if (boss.isSlotAssigned && victim.isValidTarget()) {
            val damage = deps.random.of(EXPIRE_DAMAGE)
            victim.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
        deps.worldQueues.add(END_TICKS) {
            if (vine.isSlotAssigned) deps.npcRepo.del(vine, Int.MAX_VALUE)
        }
    }

    companion object {
        const val SPAWN = "ocellus.vine_spawn"

        // PLACEHOLDER: the wiki only says 50+; no captured figure.
        private val EXPIRE_DAMAGE = 50..70

        private const val VINE = "npc.tfa_ocellus_p3_void_vine"
        private const val LIFETIME = 32
        private const val IDLE_AT = 2
        private const val END_TICKS = 2
        private const val INVISIBLE = 255
        private const val HIDE_VINE = true

        private const val START_SPOT = "spotanim.vfx_ocellus_spell_entangle01_start"
        private const val IDLE_SPOT = "spotanim.vfx_ocellus_spell_entangle01_idle"
        private const val END_SPOT = "spotanim.vfx_ocellus_spell_entangle01_end"
    }
}
