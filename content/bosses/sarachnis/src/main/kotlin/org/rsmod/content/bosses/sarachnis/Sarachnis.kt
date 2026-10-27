package org.rsmod.content.bosses.sarachnis

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.clearOwnedNpcs
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sarachnis (npc.sarachnis) in the Forthos Dungeon tomb. She melee-attacks when
 * her target is adjacent and fires ranged bolts at every player in the tomb when
 * it is not; both styles heal her for a share of the damage dealt. Every fourth
 * attack she spits a sticky web that binds the target, then relocates to a new
 * spot in the arena. At 66% and 33% health she calls a Spawn of Sarachnis pair —
 * an orange melee user west of the room and a blue magic user east — which die
 * with her.
 * Deviations: the post-web relocation teleports rather than sprinting two tiles
 * a tick, and the aranea-boots web immunity is not modelled.
 */
class Sarachnis @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec =
        boss(BOSS) {
            stats(attackRate = ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.giantspider_attack")
                    hit {
                        damage(0..MAX_HIT).roll()
                        type(Melee)
                        lifesteal(LIFESTEAL_PERCENT)
                    }
                }

            val ranged =
                ability("ranged") {
                    anim("seq.giantspider_attack")
                    projectile(
                        spotanim = "spotanim.sarachnis_rangeproj",
                        hit =
                            Effect.Hit(
                                target = TargetExpr.AllInRadius(ARENA_RADIUS),
                                damage = (0..MAX_HIT).roll(),
                                type = Ranged,
                                lifesteal = LIFESTEAL_PERCENT,
                            ),
                    )
                }

            val web =
                ability("web_bind") {
                    anim("seq.giantspider_attack")
                    projectile(
                        spotanim = "spotanim.sarachnis_web_proj",
                        hit =
                            Effect.Hit(
                                damage = (0..0).roll(),
                                type = Typeless,
                                onHit = Effect.Freeze(WEB_BIND_TICKS),
                                onHitEvenOnMiss = true,
                            ),
                    )
                    delay(WEB_REPOSITION_DELAY)
                    teleport(TargetExpr.RandomWalkableTile(WEB_REPOSITION_RADIUS, of = TargetExpr.SpawnTile()))
                }

            val spawns =
                ability("spawn_minions") {
                    summon(
                        npc = MELEE_SPAWN,
                        count = 1,
                        centeredOn = TargetExpr.SpawnTile(dx = -SPAWN_OFFSET, dz = 0),
                        mode = NpcMode.ApPlayer2,
                        duration = Int.MAX_VALUE,
                        owned = true,
                    )
                    summon(
                        npc = MAGE_SPAWN,
                        count = 1,
                        centeredOn = TargetExpr.SpawnTile(dx = SPAWN_OFFSET, dz = 0),
                        mode = NpcMode.ApPlayer2,
                        duration = Int.MAX_VALUE,
                        owned = true,
                    )
                }

            triggers {
                on(Condition.HpBelow(FIRST_SPAWN_HP)) runs Effect.Run(spawns.name)
                on(Condition.HpBelow(SECOND_SPAWN_HP)) runs Effect.Run(spawns.name)
            }

            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(ranged, weight = 1, requires = Condition.Not(WithinMeleeRange))
                }
                forceEveryAttacks(WEB_ATTACK_COUNT, WEB_ATTACK_COUNT, web)
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onLethal = deps::clearOwnedNpcs)
    }

    private companion object {
        private const val BOSS = "npc.sarachnis"
        private const val MELEE_SPAWN = "npc.sarachnis_melee_spawn"
        private const val MAGE_SPAWN = "npc.sarachnis_mage_spawn"

        private const val ATTACK_RATE = 4
        private const val MAX_HIT = 31
        private const val LIFESTEAL_PERCENT = 50
        private const val ARENA_RADIUS = 15

        private const val WEB_ATTACK_COUNT = 4
        private const val WEB_BIND_TICKS = 6
        private const val WEB_REPOSITION_DELAY = 2
        private const val WEB_REPOSITION_RADIUS = 10

        private const val FIRST_SPAWN_HP = 0.66
        private const val SECOND_SPAWN_HP = 0.33
        private const val SPAWN_OFFSET = 5
    }
}
