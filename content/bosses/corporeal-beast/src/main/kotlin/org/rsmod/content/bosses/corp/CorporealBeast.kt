package org.rsmod.content.bosses.corp

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.util.WeaponCategory
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.npc.heal
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// OSRS wiki / Kronos-184 reference (design only): melee stomp up to ~51, three
// magic projectiles (weak 0-65, powered 30-65 partially piercing prayers, split
// volley that scatters 5 tiles around the target). The Corporeal Beast takes
// halved damage from anything that is not a spear or halberd, caps single hits
// at 100, and a hit of 32+ has a 1/8 chance to release the dark energy core
// (the same 1/8 rolls on each attack while it is below half health). The core
// is not a combat npc: it leeches 5-13 typeless damage every 2 ticks from
// players in its 3x3 and feeds half of it back to the beast; when nobody is
// adjacent it hops onto a player, preferring whoever stands northernmost.
class CorporealBeast @Inject constructor(private val deps: BossDeps) : PluginScript() {

    init {
        deps.extensionRegistry.register(CORE_TICK) { ctx -> coreTick(ctx.npc, ctx.target) }
    }

    private val spec: BossSpec = boss("npc.corp_beast") {

        stats(attackRate = ATTACK_RATE)

        val stomp =
            ability("stomp") {
                anim("seq.corp_double_stomp")
                hit {
                    damage(0..51).roll()
                    type(Melee)
                }
            }

        val weak =
            ability("weak_proj") {
                anim("seq.corp_double_stomp")
                projectile(
                    spotanim = "spotanim.corp_spirit_beast_weak_proj",
                    hit = Effect.Hit(damage = Roll(0..65), type = Magic),
                )
            }

        val powered =
            ability("powered_proj") {
                anim("seq.corp_double_stomp")
                spotanim("spotanim.corp_magic_splash")
                projectile(
                    spotanim = "spotanim.corp_spirit_beast_strong_proj",
                    hit =
                        Effect.Hit(
                            damage = Roll(30..65),
                            type = Magic,
                        ),
                )
            }

        val split =
            ability("split_proj") {
                anim("seq.corp_double_stomp")
                projectile(
                    spotanim = "spotanim.corp_spirit_beast_mid_proj",
                    hit = Effect.Hit(damage = Roll(0..42), type = Magic),
                )
                debris(
                    telegraph = "spotanim.corp_ground_stomp",
                    damage = Roll(15..35),
                    type = Magic,
                    count = 5..5,
                    scatterRadius = 2,
                    windup = 2,
                    center = TargetExpr.CurrentTargetTile,
                )
            }

        val spawnCore =
            ability("spawn_core") {
                include(
                    chance(
                        CORE_SPAWN_ONE_IN,
                        sequence(
                            Effect.Summon(
                                npc = "npc.dark_core",
                                count = 1,
                                radius = 1,
                                centeredOn = TargetExpr.Self,
                                mode = NpcMode.None,
                                duration = Int.MAX_VALUE,
                                owned = true,
                            ),
                            Effect.NextAttackIn(CORE_SPAWN_RESET_TICKS),
                        ),
                    )
                )
            }

        // Wiki: the core only appears once the beast is below half health —
        // 1/8 on each 32+ hit taken, and 1/8 on each of its own attack cycles.
        onIncomingHit(
            spawnCore,
            requires =
                Condition.HitDamageAtLeast(CORE_SPAWN_HIT) and
                    Condition.HpBelow(0.5) and
                    noCoreAlive(),
        )

        incoming { rule(Condition.Always) { cap(DAMAGE_CAP) } }

        every(
            CORE_TICK_RATE,
            whenever(coreAlive(), external(CORE_TICK)),
            skipWhileBusy = false,
        )

        // While below half hp, each attack cycle rolls the same 1/8 for a core.
        every(
            ATTACK_RATE,
            whenever(Condition.HpBelow(0.5) and noCoreAlive(), Effect.Run("spawn_core")),
            skipWhileBusy = false,
        )

        phase("combat") {
            weightedSelectorRandom {
                +random(stomp, weight = 5, requires = WithinMeleeRange)
                +random(weak, weight = 5)
                +random(powered, weight = 2)
                +random(split, weight = 2)
            }
        }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = {
                if (!piercesCorp(hit)) {
                    hit.damage /= 2
                    hit.sourceUid
                        ?.let { PlayerUid(it).resolve(deps.playerList) }
                        ?.mes("The Corporeal Beast resists your weapon.")
                }
            },
        )
    }

    private val lastCoreJump = HashMap<Npc, Int>()

    private fun coreTick(corp: Npc, fallback: Player) {
        val cores =
            deps.npcRepo
                .findAll(ZoneKey.from(corp.coords), CORE_SCAN_RADIUS)
                .filter { it.isType(CORE) && it.isSlotAssigned }
        for (core in cores) {
            if (core.hitpoints <= 0) {
                lastCoreJump.remove(core)
                continue
            }
            coreTick(corp, core, fallback)
        }
    }

    private fun coreTick(corp: Npc, core: Npc, fallback: Player) {
        val victims =
            deps.playerList.filter {
                it.coords.level == core.coords.level &&
                    it.coords.chebyshevDistance(core.coords) <= CORE_DRAIN_RANGE &&
                    it.hitpoints > 0
            }
        if (victims.isNotEmpty()) {
            var healed = 0
            for (victim in victims) {
                val damage = deps.random.of(CORE_DRAIN_MIN, CORE_DRAIN_MAX)
                victim.finishNpcHit(core, 1, HitType.Typeless, damage, deps.playerHitModifier)
                victim.mes(
                    "The dark energy core drains life from you and transfers it to its master!"
                )
                healed += damage / 2
            }
            if (healed > 0) corp.heal(healed, showHitsplat = true)
            return
        }
        val lastJump = lastCoreJump[core]
        if (lastJump != null && deps.mapClock.cycle - lastJump < CORE_JUMP_COOLDOWN) return
        jumpCore(corp, core, fallback)
    }

    private fun jumpCore(corp: Npc, core: Npc, fallback: Player) {
        val victim =
            deps.playerList
                .filter {
                    it.coords.level == corp.coords.level &&
                        it.coords.chebyshevDistance(corp.coords) <= CORE_JUMP_RANGE &&
                        it.hitpoints > 0
                }
                .ifEmpty { listOf(fallback) }
                // The real core chases the northernmost player, then the easternmost.
                .maxWithOrNull(compareBy({ it.coords.z }, { it.coords.x })) ?: return
        val dest = jumpTileNear(victim) ?: return
        lastCoreJump[core] = deps.mapClock.cycle
        deps.bossProjectile(
            spotanim = JUMP_SPOTANIM,
            src = core.coords,
            target = dest,
            startHeight = 20,
            endHeight = 30,
            delay = 0,
            travel = 2,
            curve = 8,
        )
        deps.worldQueues.add(1) {
            if (core.isSlotAssigned && core.hitpoints > 0) {
                core.telejump(deps.collision, dest)
            }
        }
    }

    private fun jumpTileNear(victim: Player): CoordGrid? {
        val tiles = mutableListOf<CoordGrid>()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val tile = victim.coords.translate(dx, dz)
                if (!deps.collision.isWalkBlocked(tile)) tiles += tile
            }
        }
        if (tiles.isEmpty()) return null
        return tiles[deps.random.of(tiles.size)]
    }

    private fun findCore(corp: Npc): Npc? =
        deps.npcRepo
            .findAll(ZoneKey.from(corp.coords), CORE_SCAN_RADIUS)
            .firstOrNull { it.isType(CORE) && it.hitpoints > 0 }

    private fun piercesCorp(hit: HitBuilder): Boolean {
        val weapon = hit.righthandType() ?: return false
        return weapon.weaponCategory == WeaponCategory.Spear ||
            weapon.weaponCategory == WeaponCategory.Polearm
    }

    private fun coreAlive(): Condition =
        Condition.Custom { npc, _ -> findCore(npc) != null }

    private fun noCoreAlive(): Condition = Condition.Not(coreAlive())

    private companion object {
        const val ATTACK_RATE = 6
        const val DAMAGE_CAP = 100
        const val CORE_SPAWN_HIT = 32
        const val CORE_SPAWN_ONE_IN = 8
        const val CORE_SPAWN_RESET_TICKS = 4
        const val CORE_TICK_RATE = 2
        const val CORE_DRAIN_MIN = 5
        const val CORE_DRAIN_MAX = 13
        const val CORE_DRAIN_RANGE = 1
        const val CORE_JUMP_RANGE = 20
        const val CORE_JUMP_COOLDOWN = 4
        const val CORE_SCAN_RADIUS = 24
        const val CORE = "npc.dark_core"
        const val CORE_TICK = "corp.core_tick"

        val JUMP_SPOTANIM = "spotanim.dark_core_jump".asRSCM(RSCMType.SPOTANIM)
    }
}
