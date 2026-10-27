package org.rsmod.content.bosses.abyssalsire

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Abyssal Sire (npc.abyssalsire_sire_wandering / _panicking).
 *
 * OSRS: melee whips that hit up to 66, poisonous fumes in a 3x3 under the player,
 * mouth-launched spawnlings that grow into scions, and a forced teleport-yank when
 * attacked from beyond arm's reach. Below a third of its health it stops attacking
 * with melee entirely — spawning constantly and summoning miasma — and detonates
 * a point-blank apocalypse after dragging the player in.
 * Deviation: phase-1 stasis/respiratory-vent arena play is not modelled — the Sire
 * spawns in its wandering (combat) form.
 */
class AbyssalSire @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val lastYank = IdentityHashMap<Npc, Int>()
    private val spawnlings = IdentityHashMap<Npc, MutableList<Pair<Npc, Int>>>()

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onCombatTick = { combatTick(npc, it) })
        BossCombat.register(this, spawnSpec, deps)
        BossCombat.register(this, scionSpec, deps)

        deps.extensionRegistry.register("abyssalsire.track_spawn") { access, spawn, _, _ ->
            access?.npc?.let { boss ->
                spawnlings.getOrPut(boss) { mutableListOf() } +=
                    spawn to deps.mapClock.cycle + SPAWN_MATURE_TICKS
            }
        }

        for (type in listOf(SPAWN, SCION)) {
            val npcType =
                ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC))
                    ?: error("Missing npc type: $type")
            onNpcHit(npcType) { bindAttacker(npc, hit) }
        }
    }

    private fun combatTick(boss: Npc, target: Player) {
        if (!boss.isWithinDistance(target, YANK_RANGE)) {
            val last = lastYank[boss] ?: 0
            if (deps.mapClock.cycle - last >= YANK_COOLDOWN) {
                lastYank[boss] = deps.mapClock.cycle
                yankIn(boss, target)
            }
        }
        matureSpawnlings(boss, target)
    }

    private fun yankIn(boss: Npc, target: Player) {
        boss.anim("seq.sire_attack_teleport_player", 0, 0)
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val dest =
            YANK_TILES
                .map { centre.translate(it.first, it.second) }
                .firstOrNull { !deps.collision.isWalkBlocked(it) } ?: return
        PathingEntityCommon.teleport(target, deps.collision, dest)
        target.mes("The Sire pulls you in!")
    }

    private fun matureSpawnlings(boss: Npc, target: Player) {
        val list = spawnlings[boss] ?: return
        val now = deps.mapClock.cycle
        val mature =
            list.filter { (spawnling, matureAt) ->
                spawnling.isSlotAssigned && spawnling.hitpoints > 0 && now >= matureAt
            }
        if (mature.isEmpty()) return
        list.removeAll(mature)
        for ((spawnling, _) in mature) {
            val tile = spawnling.coords
            deps.npcRepo.del(spawnling, Int.MAX_VALUE)
            val scion = deps.spawnOwnedNpc(boss, SCION, tile, MINION_DURATION) ?: continue
            scion.mode = NpcMode.ApPlayer2
            scion.assignSpawnOwner(target, now)
            scion.apPlayer2(target, deps.playerInteractions)
        }
    }

    private fun bindAttacker(minion: Npc, hit: Hit) {
        if (!hit.isFromPlayer) return
        val attacker = hit.resolvePlayerSource(deps.playerList) ?: return
        if (minion.isSlotAssigned && minion.hitpoints > 0) {
            minion.mode = NpcMode.ApPlayer2
            minion.assignSpawnOwner(attacker, deps.mapClock.cycle)
            minion.apPlayer2(attacker, deps.playerInteractions)
        }
    }

    override val spec =
        boss(SIRE, PANICKING) {
            stats(attackRate = 7)

            val whips =
                ability("whips") {
                    anim("seq.sire_attack_double_whips")
                    hit {
                        damage((0..MELEE_MAX).roll())
                        type(Melee)
                    }
                }

            val miasma =
                ability("miasma") {
                    anim("seq.sire_attack_miasma")
                    tileAoE(
                        tiles = { _, player -> player.coords.area(FUME_RADIUS) },
                        damage = (0..FUME_MAX).roll(),
                        type = Magic,
                    )
                    poison(FUME_POISON)
                }

            val spawnToss =
                ability("spawn_toss") {
                    anim("seq.sire_attack_spawns")
                    summon(
                        npc = SPAWN,
                        count = 2,
                        radius = 3,
                        centeredOn = CurrentTarget,
                        mode = NpcMode.ApPlayer2,
                        duration = MINION_DURATION,
                        owned = true,
                        onSummon = "abyssalsire.track_spawn",
                    )
                }

            val spawnBarrage =
                ability("spawn_barrage") {
                    anim("seq.sire_attack_spawns_two")
                    summon(
                        npc = SPAWN,
                        count = 3,
                        radius = 3,
                        centeredOn = CurrentTarget,
                        mode = NpcMode.ApPlayer2,
                        duration = MINION_DURATION,
                        owned = true,
                        onSummon = "abyssalsire.track_spawn",
                    )
                }

            val apocalypse =
                ability("apocalypse") {
                    anim("seq.sire_apocalypse")
                    tileAoE(
                        tiles = { _, player -> player.coords.area(APOCALYPSE_RADIUS) },
                        damage = (0..APOCALYPSE_MAX).roll(),
                        type = Magic,
                    )
                }

            phase("wandering", transmog = "npc.abyssalsire_sire_wandering") {
                attackRate = 7
                weightedSelectorRandom {
                    +random(whips, weight = 4, requires = WithinMeleeRange)
                    +random(miasma, weight = 2)
                    +random(spawnToss, weight = 2)
                }
            }

            phase("panicking", entryHp = PANICKING_HP, transmog = "npc.abyssalsire_sire_panicking") {
                attackRate = 7
                weightedSelectorRandom {
                    +random(spawnBarrage, weight = 3)
                    +random(miasma, weight = 3)
                    +random(apocalypse, weight = 1)
                }
            }
        }

    private val spawnSpec =
        boss(SPAWN) {
            stats(attackRate = MINION_ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.abyssal_spawn_attack")
                    hit {
                        damage((0..MINION_MAX).roll())
                        type(Melee)
                    }
                }

            phase("combat") {
                weightedSelectorRandom { +random(melee, weight = 1, requires = WithinMeleeRange) }
            }
        }

    private val scionSpec =
        boss(SCION) {
            stats(attackRate = MINION_ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.abyssal_scion_attack_melee")
                    hit {
                        damage((0..MINION_MAX).roll())
                        type(Melee)
                    }
                }

            val ranged =
                ability("ranged") {
                    anim("seq.abyssal_scion_attack_ranged")
                    hit {
                        damage((0..MINION_MAX).roll())
                        type(Ranged)
                    }
                }

            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(ranged, weight = 1)
                }
            }
        }

    private companion object {
        private const val SIRE = "npc.abyssalsire_sire_wandering"
        private const val PANICKING = "npc.abyssalsire_sire_panicking"
        private const val SPAWN = "npc.abyssalsire_spawn"
        private const val SCION = "npc.abyssalsire_scion"

        private const val MELEE_MAX = 66
        private const val PANICKING_HP = 0.34
        private const val FUME_RADIUS = 1
        private const val FUME_MAX = 10
        private const val FUME_POISON = 8
        private const val APOCALYPSE_RADIUS = 2
        private const val APOCALYPSE_MAX = 96

        private const val YANK_RANGE = 3
        private const val YANK_COOLDOWN = 10
        private const val SPAWN_MATURE_TICKS = 40
        private const val MINION_DURATION = 99_999
        private const val MINION_ATTACK_RATE = 5
        private const val MINION_MAX = 6

        private val YANK_TILES = listOf(0 to 4, 4 to 0, 0 to -4, -4 to 0)
    }

    private fun CoordGrid.area(r: Int): List<CoordGrid> =
        (-r..r).flatMap { dx -> (-r..r).map { dz -> translate(dx, dz) } }
}
