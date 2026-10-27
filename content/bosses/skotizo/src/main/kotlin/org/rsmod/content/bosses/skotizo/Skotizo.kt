package org.rsmod.content.bosses.skotizo

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Skotizo (npc.cata_boss) in his lair beneath the Catacombs of Kourend. Stabs in
 * melee range, lobs dark fireballs at range and prefers melee up close. Four
 * Awakened Altars stand around the room: during the fight he randomly awakens
 * one, and each awake altar reduces the damage players deal to him — 25% per
 * altar (100% with all four), or 15% for Arclight/Emberlight wielders (60%
 * max). An awakened altar has 100 hp; destroying it reverts it to a closed
 * altar that may not reawaken for one minute. Once two thirds of his health
 * are depleted he calls three reanimated demon spawns, resummoning them if
 * they are slain, and may call a dark ankou while an altar is awake.
 * Deviation: the dark-totem altar entrance and the instanced lair are not
 * modelled; the four closed altars are spawned around the boss instead.
 */
class Skotizo @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val bossId by lazy { BOSS.asRSCM(RSCMType.NPC) }

    // Tile of a deactivated altar -> earliest map cycle it may reawaken.
    private val altarCooldownUntil = mutableMapOf<CoordGrid, Int>()

    override val spec =
        boss(BOSS) {
            stats(attackRate = ATTACK_RATE)

            val swipe =
                ability("swipe") {
                    anim("seq.demon_update_attack")
                    hit {
                        damage(0..MAX_HIT).roll()
                        type(Melee)
                    }
                }

            val fireball =
                ability("fireball") {
                    anim("seq.demon_update_fireball_cast")
                    projectile(
                        spotanim = "spotanim.godwars_black_demon_fireball_proj",
                        impact = "spotanim.godwars_black_demon_fireball_spot",
                        hit = Effect.Hit(damage = (0..MAX_HIT).roll(), type = Magic),
                    )
                }

            val awaken = ability("awaken") { include(external(AWAKEN_ALTAR)) }

            val summonDemons =
                ability("summon_demons") {
                    summon(
                        npc = MINION,
                        count = 3,
                        radius = 3,
                        mode = NpcMode.ApPlayer2,
                        duration = Int.MAX_VALUE,
                        owned = true,
                    )
                }

            val summonAnkou =
                ability("summon_ankou") {
                    summon(
                        npc = ANKOU,
                        count = 1,
                        radius = 3,
                        mode = NpcMode.ApPlayer2,
                        duration = Int.MAX_VALUE,
                        owned = true,
                    )
                }

            incoming {
                for (n in MAX_ALTARS downTo 1) {
                    rule(altarsAwakeAtLeast(n) and Condition.HitDemonbane) {
                        scalePercent(100 - min(15 * n, 60))
                    }
                    rule(altarsAwakeAtLeast(n)) { scalePercent(100 - min(25 * n, 100)) }
                }
            }

            triggers {
                on(Condition.HpBelow(MINION_HP_FRACTION)) runs Effect.Run(summonDemons.name)
            }

            every(ALTAR_TRY_TICKS, whenever(altarAwakenable(), Effect.Run(awaken.name)))
            every(
                MINION_RETRY_TICKS,
                whenever(
                    Condition.HpBelow(MINION_HP_FRACTION) and noDemonsAlive(),
                    Effect.Run(summonDemons.name),
                ),
            )
            every(
                ANKOU_TRY_TICKS,
                whenever(anyAltarAwake() and noAnkouAlive(), Effect.Run(summonAnkou.name)),
            )

            phase("combat") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(swipe, weight = 4, requires = WithinMeleeRange)
                    +random(fireball, weight = 3)
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register(AWAKEN_ALTAR) { ctx -> awakenAltar(ctx.npc) }
        onEvent<NpcStateEvents.Spawn>(bossId) { ensureAltars(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.id == bossId) ensureAltars(npc) }
        onEvent<NpcStateEvents.Delete> { closeDeletedAltar(npc) }
    }

    private fun ensureAltars(boss: Npc) {
        for (dir in DIRECTIONS) {
            val present =
                deps.npcRepo.findAll(ZoneKey.from(boss.coords), LAIR_RADIUS).any {
                    it.isVisType(dir.closed) || it.isVisType(dir.open)
                }
            if (present) continue
            val tile = boss.coords.translate(dir.dx, dir.dz)
            val type = ServerCacheManager.getNpc(dir.closed.asRSCM(RSCMType.NPC)) ?: continue
            val altar = Npc(type, tile)
            altar.mode = NpcMode.None
            deps.npcRepo.add(altar, Int.MAX_VALUE)
            // Int.MAX_VALUE would flag respawns=true; altar replacement is handled by
            // closeDeletedAltar on the Delete event that `del` publishes for one-shot npcs.
            altar.respawns = false
        }
    }

    private fun awakenAltar(boss: Npc) {
        val cycle = deps.mapClock.cycle
        val candidates =
            DIRECTIONS.mapNotNull { dir ->
                val altar =
                    deps.npcRepo.findAll(ZoneKey.from(boss.coords), LAIR_RADIUS).firstOrNull {
                        it.hitpoints > 0 && it.isVisType(dir.closed)
                    } ?: return@mapNotNull null
                if ((altarCooldownUntil[altar.coords] ?: 0) > cycle) null else dir to altar
            }
        if (candidates.isEmpty()) return
        val (dir, altar) = candidates[deps.random.of(candidates.size)]
        val open = ServerCacheManager.getNpc(dir.open.asRSCM(RSCMType.NPC)) ?: return
        altar.transmog(open, Int.MAX_VALUE)
    }

    private fun closeDeletedAltar(npc: Npc) {
        // Awakened altars revert to their base (closed) type before Delete publishes,
        // so matching is on the base type; every altar npc is a closed variant at base.
        val dir = DIRECTIONS.firstOrNull { npc.isType(it.closed) } ?: return
        val closed = ServerCacheManager.getNpc(dir.closed.asRSCM(RSCMType.NPC)) ?: return
        altarCooldownUntil[npc.coords] = deps.mapClock.cycle + ALTAR_COOLDOWN_TICKS
        val altar = Npc(closed, npc.coords)
        altar.mode = NpcMode.None
        deps.npcRepo.add(altar, Int.MAX_VALUE)
        altar.respawns = false
    }

    private fun openAltars(boss: Npc): List<Npc> =
        deps.npcRepo.findAll(ZoneKey.from(boss.coords), LAIR_RADIUS).filter { altar ->
            altar.hitpoints > 0 && DIRECTIONS.any { altar.isVisType(it.open) }
        }.toList()

    private fun closedAltarReady(boss: Npc): Boolean {
        val cycle = deps.mapClock.cycle
        return deps.npcRepo.findAll(ZoneKey.from(boss.coords), LAIR_RADIUS).any { altar ->
            altar.hitpoints > 0 &&
                DIRECTIONS.any { altar.isVisType(it.closed) } &&
                (altarCooldownUntil[altar.coords] ?: 0) <= cycle
        }
    }

    private fun altarsAwakeAtLeast(n: Int): Condition =
        Condition.Custom { npc, _ -> openAltars(npc).size >= n }

    private fun anyAltarAwake(): Condition = altarsAwakeAtLeast(1)

    private fun altarAwakenable(): Condition = Condition.Custom { npc, _ -> closedAltarReady(npc) }

    private fun noDemonsAlive(): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo.findAll(ZoneKey.from(npc.coords), LAIR_RADIUS).none {
                it.hitpoints > 0 && it.isType(MINION)
            }
        }

    private fun noAnkouAlive(): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo.findAll(ZoneKey.from(npc.coords), LAIR_RADIUS).none {
                it.hitpoints > 0 && it.isType(ANKOU)
            }
        }

    private data class AltarDir(val dx: Int, val dz: Int, val closed: String, val open: String)

    private companion object {
        private const val BOSS = "npc.cata_boss"
        private const val MINION = "npc.cata_boss_minion"
        private const val ANKOU = "npc.cata_boss_ankou"
        private const val AWAKEN_ALTAR = "skotizo.awaken_altar"

        private const val ATTACK_RATE = 6
        private const val MAX_HIT = 38
        private const val MAX_ALTARS = 4
        private const val MINION_HP_FRACTION = 0.34
        private const val ALTAR_DISTANCE = 9
        private const val LAIR_RADIUS = 20
        private const val ALTAR_COOLDOWN_TICKS = 100

        private val ALTAR_TRY_TICKS = 30..60
        private val MINION_RETRY_TICKS = 50..90
        private val ANKOU_TRY_TICKS = 80..140

        private val DIRECTIONS =
            listOf(
                AltarDir(
                    0,
                    ALTAR_DISTANCE,
                    "npc.cata_boss_altar_closed_n",
                    "npc.cata_boss_altar_open_n",
                ),
                AltarDir(
                    ALTAR_DISTANCE,
                    0,
                    "npc.cata_boss_altar_closed_e",
                    "npc.cata_boss_altar_open_e",
                ),
                AltarDir(
                    0,
                    -ALTAR_DISTANCE,
                    "npc.cata_boss_altar_closed_s",
                    "npc.cata_boss_altar_open_s",
                ),
                AltarDir(
                    -ALTAR_DISTANCE,
                    0,
                    "npc.cata_boss_altar_closed_w",
                    "npc.cata_boss_altar_open_w",
                ),
            )
    }
}
