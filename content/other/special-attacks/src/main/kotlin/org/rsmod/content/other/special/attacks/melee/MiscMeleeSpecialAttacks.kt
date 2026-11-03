package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

class MiscMeleeSpecialAttacks @Inject constructor(private val npcList: NpcList) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.saradomin_sword", SaradominSword(manager, "seq.saradomin_sword_special_player"))
        val blessedSs = SaradominSword(manager, "seq.blessed_saradomin_sword_special_player")
        for (obj in listOf("obj.blessed_saradomin_sword", "obj.blessed_saradomin_sword_degraded")) {
            registerMelee(obj, blessedSs)
        }

        val disrupt = Disrupt(manager, "seq.human_special_voidwaker")
        for (obj in listOf("obj.voidwaker", "obj.br_voidwaker")) {
            registerMelee(obj, disrupt)
        }
        val deadmanDisrupt = Disrupt(manager, "seq.human_special02_voidwaker")
        for (obj in listOf("obj.deadman_voidwaker", "obj.deadman_blighted_voidwaker")) {
            registerMelee(obj, deadmanDisrupt)
        }
        val eviscerate = Eviscerate(manager)
        for (obj in listOf("obj.osmumtens_fang", "obj.osmumtens_fang_ornament", "obj.br_osmumtens_fang")) {
            registerMelee(obj, eviscerate)
        }
        registerMelee("obj.ancient_goblin_mace", FavourOfTheWarGod(manager))
        val sunder = Sunder(manager)
        for (obj in listOf("obj.brain_anchor", "obj.bh_brain_anchor_imbue")) {
            registerMelee(obj, sunder)
        }

        val weaken = Weaken(manager)
        for (obj in DEMONBLADES) {
            registerMelee(obj, weaken)
        }

        val spearWall = SpearWall(manager, npcList)
        for (obj in listOf("obj.vestas_spear", "obj.vestas_spear_bh")) {
            registerMelee(obj, spearWall)
        }
        registerMelee("obj.wild_cave_ursine_charged", BearDown(manager))
    }

    private class SaradominSword(
        private val manager: SpecialAttackManager,
        private val anim: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            lightning(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            lightning(target, attack)
            return true
        }

        private fun ProtectedAccess.lightning(target: PathingEntity, attack: CombatAttack.Melee) {
            anim(anim)
            spotanim(
                "spotanim.godwars_saradomin_sword_attack_spot",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.1,
                    blockType = MeleeAttackType.Slash,
                )
            val magicDamage = random.of(1..16)
            manager.giveCombatXp(this, target, attack, damage + magicDamage)
            manager.queueMeleeHit(this, target, damage)
            manager.queueMagicHit(this, target, magicDamage, clientDelay = 2)
            manager.continueCombat(this, target)
        }
    }

    private class Disrupt(
        private val manager: SpecialAttackManager,
        private val specAnim: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            disrupt(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            disrupt(target, attack)
            return true
        }

        private fun ProtectedAccess.disrupt(target: PathingEntity, attack: CombatAttack.Melee) {
            anim(specAnim)
            spotanim(
                "spotanim.fx_voidwaker02_special",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val maxHit =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            val damage = random.of(maxHit / 2..maxHit + maxHit / 2)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 2)
            manager.continueCombat(this, target)
        }
    }

    private class Eviscerate(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            eviscerate(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            eviscerate(target, attack)
            return true
        }

        private fun ProtectedAccess.eviscerate(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.weapon_sword_osmumten03_special")
            spotanim(
                "spotanim.spotanim_weapon_sword_osmumten_special",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val accurate =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = MeleeAttackType.Stab,
                    multiplier = 1.5,
                )
            val trueMax =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            val damage = if (accurate) random.of(0..trueMax) else 0
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    private class FavourOfTheWarGod(private val manager: SpecialAttackManager) :
        MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            favour(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            favour(target, attack)
            return true
        }

        private fun ProtectedAccess.favour(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.slice_player_mace_special_attack")
            target.spotanim("spotanim.slice_player_mace_special_attack_spotanim", height = 96)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                val drained =
                    if (target is Player) {
                        minOf(damage, target.stat("stat.prayer")).also {
                            target.statSub("stat.prayer", it, 0)
                        }
                    } else {
                        damage
                    }
                player.statAdd("stat.prayer", drained, 0)
            }
            manager.continueCombat(this, target)
        }
    }

    private class Sunder(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            sunder(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            sunder(target, attack)
            return true
        }

        private fun ProtectedAccess.sunder(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.brain_player_anchor_special_attack")
            spotanim(
                "spotanim.brain_anchor_special_attack_spot",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.1,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                drainCombatStats(
                    target,
                    damage / 10,
                    listOf("defence", "attack", "ranged", "magic"),
                )
            }
            manager.continueCombat(this, target)
        }
    }

    private class Weaken(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            weaken(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            weaken(target, attack)
            return true
        }

        private fun ProtectedAccess.weaken(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.sp_attack_dragon_scimitar")
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                weakenStats(target)
            }
            manager.continueCombat(this, target)
        }

        private fun weakenStats(target: PathingEntity) {
            val demon = target is Npc && target.visType.paramOrNull(params.demon) != null
            val percent = if (demon) 10 else 5
            when (target) {
                is Player -> {
                    for (stat in listOf("attack", "strength", "defence")) {
                        val drain = (target.stat("stat.$stat") * percent) / 100 + 1
                        target.statSub("stat.$stat", drain, 0)
                    }
                }
                is Npc -> {
                    for (stat in listOf("attack", "strength", "defence")) {
                        val drain = (npcStat(target, stat) * percent) / 100 + 1
                        setNpcStat(target, stat, (npcStat(target, stat) - drain).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    private class SpearWall(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            spearWall(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            spearWall(target, attack)
            return true
        }

        private fun ProtectedAccess.spearWall(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.human_spear_wall_spec")
            spotanim(
                "spotanim.vestas_spear_sa_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage = roll(target, attack)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)

            var hitCount = 1
            for (npc in npcList) {
                if (hitCount >= 16) break
                if (npc === target || !npc.isValidTarget()) continue
                if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                if (npc.coords.chebyshevDistance(player.coords) > 8) continue
                val neighbourDamage = roll(npc, attack)
                manager.queueMeleeHit(this, npc, neighbourDamage)
                manager.giveCombatXp(this, npc, attack, neighbourDamage)
                hitCount++
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 1.0,
                maxHitMultiplier = 1.0,
                blockType = MeleeAttackType.Slash,
            )
    }

    private class BearDown(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            bearDown(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            bearDown(target, attack)
            return true
        }

        private fun ProtectedAccess.bearDown(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.human_special_ursine")
            spotanim(
                "spotanim.fx_ursine01_special",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0 && target is Player) {
                target.statSub("stat.agility", 20, 0)
            }
            manager.continueCombat(this, target)
        }
    }

    private companion object {
        val DEMONBLADES =
            listOf(
                "obj.darklight",
                "obj.arclight",
                "obj.arclight_inactive",
                "obj.emberlight",
            )
    }
}
