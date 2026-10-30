package org.rsmod.content.skills.magic.arceuus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.ownedBy
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.inv.isType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

internal var Player.thrallRecastCooldown by boolVarBit("varbit.arceuus_thrall_recast")

class ThrallScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
    private val collision: CollisionFlagMap,
    private val worldRepo: WorldRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val hitModifier: NpcHitModifier,
) : PluginScript() {
    private val thrallNpcIds: Set<Int> by lazy {
        ThrallSpell.entries.mapTo(HashSet()) { it.npc.asRSCM(RSCMType.NPC) }
    }

    override fun ScriptContext.startup() {
        for (spell in ThrallSpell.entries) {
            onIfOverlayButton(spell.component) { cast(spell) }
        }
        onPlayerQueue(RECAST_QUEUE) { player.thrallRecastCooldown = false }
    }

    private suspend fun ProtectedAccess.cast(spell: ThrallSpell) {
        if (!hasBookOfTheDead()) {
            mes("You need the Book of the Dead to cast this spell.")
            return
        }
        if (stat("stat.prayer") < spell.prayerCost) {
            mes("You need at least ${spell.prayerCost} Prayer points to cast this spell.")
            return
        }
        if (player.thrallRecastCooldown) {
            return
        }
        val spellObj = ServerCacheManager.getItem(spell.obj.asRSCM(RSCMType.OBJ)) ?: return
        val magicSpell = spells.getObjSpell(spellObj) ?: return
        if (runes.attemptCast(player, magicSpell).isFailure()) {
            return
        }
        val tile = freeAdjacentTile(player.coords) ?: return
        statSub("stat.prayer", spell.prayerCost, 0)
        statAdvance("stat.magic", magicSpell.castXp)
        anim(CAST_ANIM)
        spotanim(spell.castSpotanim)

        npcList.ownedBy(player).filter { it.id in thrallNpcIds }.forEach { npcRepo.del(it, 0) }

        val thrall = Npc(spell.npc, tile)
        thrall.respawns = false
        npcRepo.add(thrall, stat("stat.magic").coerceAtLeast(1))
        thrall.assignSpawnOwner(player, mapClock)
        thrall.facePlayer(player)

        player.thrallRecastCooldown = true
        queue(RECAST_QUEUE, RECAST_TICKS)

        protectedAccess.launch(player) { thrallCombatLoop(thrall, spell) }
    }

    private suspend fun ProtectedAccess.thrallCombatLoop(thrall: Npc, spell: ThrallSpell) {
        while (npcList.ownedBy(player).any { it === thrall }) {
            delay(ATTACK_SPEED)
            val interaction = player.interaction as? InteractionNpc ?: continue
            val target = interaction.target
            if (target.hitpoints <= 0) {
                continue
            }
            val distance = thrall.coords.chebyshevDistance(target.coords)
            if (distance > spell.attackRange) {
                thrall.walk(target.coords)
                continue
            }
            attack(thrall, target, spell)
        }
    }

    private fun ProtectedAccess.attack(thrall: Npc, target: Npc, spell: ThrallSpell) {
        thrall.faceNpc(target)
        thrall.visType.paramOrNull(params.attack_anim)?.let {
            thrall.anim(RSCM.getReverseMapping(RSCMType.SEQ, it.id), 0, 0)
        }
        val hitDelay = spawnProjectile(thrall, target)
        target.queueHit(
            source = player,
            delay = hitDelay,
            type = spell.style,
            damage = random.of(0, spell.maxHit),
            modifier = hitModifier,
        )
    }

    private fun spawnProjectile(thrall: Npc, target: Npc): Int {
        val travelSpot = thrall.visType.paramOrNull(params.proj_travel) ?: return 1
        val projType = thrall.visType.paramOrNull(params.proj_type) ?: return 1
        val proj = ProjAnim.fromNpcToNpc(thrall, target, travelSpot.id, projType)
        worldRepo.projAnim(proj)
        return proj.serverCycles
    }

    private fun ProtectedAccess.hasBookOfTheDead(): Boolean =
        inv.count(BOOK_OF_THE_DEAD) > 0 ||
            Wearpos.entries.any { player.worn[it.slot]?.isType(BOOK_OF_THE_DEAD) == true }

    private fun freeAdjacentTile(near: CoordGrid): CoordGrid? {
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val candidate = near.translate(dx, dz)
                if (collision[candidate.x, candidate.z, candidate.level] and BLOCKED_FLAGS == 0) {
                    return candidate
                }
            }
        }
        return null
    }

    internal enum class ThrallSpell(
        val obj: String,
        val component: String,
        val npc: String,
        val prayerCost: Int,
        val maxHit: Int,
        val style: HitType,
        val attackRange: Int,
        val castSpotanim: String,
    ) {
        LesserGhost(
            obj = "obj.osb8_monkey_cow",
            component = "component.magic_spellbook:resurrect_lesser_ghost",
            npc = "npc.arceuus_thrall_ghost_lesser",
            prayerCost = 2,
            maxHit = 1,
            style = HitType.Magic,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_ghost_cast_spotanim",
        ),
        LesserSkeleton(
            obj = "obj.osb8_monkey_bob",
            component = "component.magic_spellbook:resurrect_lesser_skeleton",
            npc = "npc.arceuus_thrall_skeleton_lesser",
            prayerCost = 2,
            maxHit = 1,
            style = HitType.Ranged,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_skeleton_cast_spotanim",
        ),
        LesserZombie(
            obj = "obj.osb8_monkey_donie",
            component = "component.magic_spellbook:resurrect_lesser_zombie",
            npc = "npc.arceuus_thrall_zombie_lesser",
            prayerCost = 2,
            maxHit = 1,
            style = HitType.Melee,
            attackRange = 2,
            castSpotanim = "spotanim.resurrect_zombie_cast_spotanim",
        ),
        SuperiorGhost(
            obj = "obj.osb8_bamboo",
            component = "component.magic_spellbook:resurrect_superior_ghost",
            npc = "npc.arceuus_thrall_ghost_superior",
            prayerCost = 4,
            maxHit = 2,
            style = HitType.Magic,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_ghost_cast_spotanim",
        ),
        SuperiorSkeleton(
            obj = "obj.osb8_monkey_unicorn",
            component = "component.magic_spellbook:resurrect_superior_skeleton",
            npc = "npc.arceuus_thrall_skeleton_superior",
            prayerCost = 4,
            maxHit = 2,
            style = HitType.Ranged,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_skeleton_cast_spotanim",
        ),
        SuperiorZombie(
            obj = "obj.osb8_monkey_sheep",
            component = "component.magic_spellbook:resurrect_superior_zombie",
            npc = "npc.arceuus_thrall_zombie_superior",
            prayerCost = 4,
            maxHit = 2,
            style = HitType.Melee,
            attackRange = 2,
            castSpotanim = "spotanim.resurrect_zombie_cast_spotanim",
        ),
        GreaterGhost(
            obj = "obj.osb8_monkey_trap",
            component = "component.magic_spellbook:resurrect_greater_ghost",
            npc = "npc.arceuus_thrall_ghost_greater",
            prayerCost = 6,
            maxHit = 3,
            style = HitType.Magic,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_ghost_cast_spotanim",
        ),
        GreaterSkeleton(
            obj = "obj.osb8_monkey_aereck",
            component = "component.magic_spellbook:resurrect_greater_skeleton",
            npc = "npc.arceuus_thrall_skeleton_greater",
            prayerCost = 6,
            maxHit = 3,
            style = HitType.Ranged,
            attackRange = 6,
            castSpotanim = "spotanim.resurrect_skeleton_cast_spotanim",
        ),
        GreaterZombie(
            obj = "obj.osb8_unicorn_horn",
            component = "component.magic_spellbook:resurrect_greater_zombie",
            npc = "npc.arceuus_thrall_zombie_greater",
            prayerCost = 6,
            maxHit = 3,
            style = HitType.Melee,
            attackRange = 2,
            castSpotanim = "spotanim.resurrect_zombie_cast_spotanim",
        ),
    }

    private companion object {
        const val CAST_ANIM = "seq.human_spellcast_resurrect"
        const val RECAST_QUEUE = "queue.thrall_recast"
        const val RECAST_TICKS = 16
        const val ATTACK_SPEED = 4
        const val BOOK_OF_THE_DEAD = "obj.book_of_the_dead"
        const val BLOCKED_FLAGS =
            CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR
    }
}
