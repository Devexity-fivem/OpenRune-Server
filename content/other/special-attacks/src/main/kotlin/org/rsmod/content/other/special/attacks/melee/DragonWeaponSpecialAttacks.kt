package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.lockOverheads
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.routefinder.collision.CollisionFlagMap

class DragonWeaponSpecialAttacks @Inject constructor(private val collision: CollisionFlagMap) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val puncture = Puncture(manager)
        for (obj in DRAGON_DAGGERS) {
            registerMelee(obj, puncture)
        }

        val claws = SliceAndDice(manager)
        for (obj in DRAGON_CLAWS) {
            registerMelee(obj, claws)
        }

        val sever = Sever(manager)
        for (obj in DRAGON_SCIMITARS) {
            registerMelee(obj, sever)
        }

        val shove = Shove(manager, collision)
        for (obj in SHOVE_SPEARS) {
            registerMelee(obj, shove)
        }

        val smash =
            DefenceDrainer(
                manager,
                drainPercent = 30,
                accuracyMultiplier = 1.0,
                anim = "seq.dragon_warhammer_sa_player",
                spot = "spotanim.dragon_warhammer_sa_spotanim",
            )
        registerMelee("obj.dragon_warhammer", smash)
        registerMelee("obj.dragon_warhammer_ornament", smash)

        val statiusSmash =
            DefenceDrainer(
                manager,
                drainPercent = 70,
                accuracyMultiplier = 1.0,
                anim = "seq.dragon_warhammer_sa_player",
                spot = "spotanim.dragon_warhammer_sa_spotanim",
            )
        registerMelee("obj.statius_warhammer", statiusSmash)

        val pulverize =
            DefenceDrainer(
                manager,
                drainPercent = 35,
                accuracyMultiplier = 1.25,
                anim = "seq.vfx_elder_maul_special",
                spot = "spotanim.spotanim_elder_maul_special",
                targetSpot = "spotanim.spotanim_elder_maul_special_impact",
            )
        registerMelee("obj.elder_maul", pulverize)
        registerMelee("obj.dragon_mace", Shatter(manager))
        registerMelee("obj.granite_maul", QuickSmash(manager))
        registerMelee("obj.granite_maul_pretty", QuickSmash(manager))
        registerMelee("obj.granite_maul_plus", QuickSmash(manager))

        val backstab = Backstab(manager)
        for (obj in BONE_DAGGERS) {
            registerMelee(obj, backstab)
        }

        val feint = Feint(manager)
        registerMelee("obj.vestas_longsword", feint)
        registerMelee("obj.vestas_longsword_bh", feint)
    }

    private class Puncture(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            puncture(target, attack)
            return true
        }

        private fun ProtectedAccess.puncture(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.puncture")
            spotanim(
                "spotanim.sp_attack_puncture_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            var total = 0
            repeat(2) {
                val damage =
                    manager.rollMeleeDamage(
                        source = this,
                        target = target,
                        attack = attack,
                        accuracyMultiplier = 1.15,
                        maxHitMultiplier = 1.15,
                        blockType = MeleeAttackType.Slash,
                    )
                manager.queueMeleeHit(this, target, damage)
                total += damage
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }
    }

    private class SliceAndDice(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            sliceAndDice(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            sliceAndDice(target, attack)
            return true
        }

        private fun ProtectedAccess.sliceAndDice(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.human_dragon_claws_spec")
            target.spotanim("spotanim.dragon_claws_spot", height = 96)

            val hits = rollSequence(target, attack)
            val total = hits.sum()
            for (hit in hits) {
                manager.queueMeleeHit(this, target, hit)
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.rollSequence(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): List<Int> {
            val maxHit =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            var firstSuccess = -1
            for (i in 0 until 4) {
                val success =
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = MeleeAttackType.Slash,
                        multiplier = 1.0,
                    )
                if (success) {
                    firstSuccess = i
                    break
                }
            }
            if (firstSuccess < 0) {
                return if (random.of(0 until 3) < 2) {
                    listOf(0, 0, 1, 1)
                } else {
                    listOf(0, 0, 0, 0)
                }
            }
            val roll = random.of(0..maxHit)
            return when (firstSuccess) {
                0 -> listOf(roll, roll / 2, roll / 4, roll / 4)
                1 -> listOf(0, roll, roll / 2, roll / 2)
                2 -> listOf(0, 0, roll, roll)
                else -> listOf(0, 0, 0, roll)
            }
        }
    }

    private class Sever(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            sever(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            sever(target, attack)
            return true
        }

        private fun ProtectedAccess.sever(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.sp_attack_dragon_scimitar")
            spotanim(
                "spotanim.sp_attack_dragon_scimitar_trail_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0 && target is Player) {
                target.lockOverheads(8)
            }
            manager.continueCombat(this, target)
        }
    }

    private class DefenceDrainer(
        private val manager: SpecialAttackManager,
        private val drainPercent: Int,
        private val accuracyMultiplier: Double,
        private val anim: String,
        private val spot: String,
        private val targetSpot: String? = null,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            smash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            smash(target, attack)
            return true
        }

        private fun ProtectedAccess.smash(target: PathingEntity, attack: CombatAttack.Melee) {
            anim(anim)
            spotanim(spot, slot = constants.spotanim_slot_combat, height = 96)
            targetSpot?.let { target.spotanim(it, height = 96) }
            val accurate =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = MeleeAttackType.Crush,
                    multiplier = accuracyMultiplier,
                )
            val maxHit =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.5,
                )
            val damage = if (accurate) random.of(0..maxHit) else 0
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (accurate) {
                drainDefence(target, drainPercent)
            }
            manager.continueCombat(this, target)
        }

        private fun drainDefence(target: PathingEntity, percent: Int) {
            when (target) {
                is Player -> {
                    val drain = (target.stat("stat.defence") * percent) / 100
                    target.statSub("stat.defence", drain, 0)
                }
                is Npc -> {
                    val drain = (target.defenceLvl * percent) / 100
                    target.defenceLvl = (target.defenceLvl - drain).coerceAtLeast(0)
                }
            }
        }
    }

    private class Shatter(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            shatter(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            shatter(target, attack)
            return true
        }

        private fun ProtectedAccess.shatter(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.shatter")
            spotanim(
                "spotanim.sp_attack_shatter_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.25,
                    maxHitMultiplier = 1.5,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    private class Shove(
        private val manager: SpecialAttackManager,
        private val collision: CollisionFlagMap,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            shove(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            shove(target, attack)
            return true
        }

        private fun ProtectedAccess.shove(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.shove")
            spotanim(
                "spotanim.sp_attack_shove_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val dx = (target.coords.x - player.coords.x).coerceIn(-1, 1)
            val dz = (target.coords.z - player.coords.z).coerceIn(-1, 1)
            if (target is Npc) {
                val dest = target.coords.translate(dx, dz)
                target.teleport(collision, dest)
                CombatEffects.freeze(target, 5)
            } else if (target is Player) {
                CombatEffects.freeze(target, 5)
            }
            manager.continueCombat(this, target)
        }
    }

    private class QuickSmash(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            quickSmash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            quickSmash(target, attack)
            return true
        }

        private fun ProtectedAccess.quickSmash(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.slayer_granite_maul_special_attack")
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
            manager.setNextAttackDelay(this, 1)
            manager.continueCombat(this, target)
        }
    }

    private class Backstab(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            backstab(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            backstab(target, attack)
            return true
        }

        private fun ProtectedAccess.backstab(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.dttd_player_stab_bone_dagger")
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.0,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                drainCombatStats(target, damage, listOf("defence"))
            }
            manager.continueCombat(this, target)
        }
    }

    private class Feint(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            feint(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            feint(target, attack)
            return true
        }

        private fun ProtectedAccess.feint(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.sp_attack_dragon_scimitar")
            val maxHit =
                manager.calculateMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.2,
                )
            val accurate =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = MeleeAttackType.Slash,
                    multiplier = 2.0,
                )
            val damage =
                if (accurate) {
                    random.of(0..maxHit).coerceAtLeast((maxHit + 4) / 5)
                } else {
                    0
                }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    private companion object {
        val DRAGON_DAGGERS =
            listOf(
                "obj.dragon_dagger",
                "obj.dragon_dagger_p",
                "obj.dragon_dagger_p+",
                "obj.dragon_dagger_p++",
            )
        val DRAGON_CLAWS = listOf("obj.dragon_claws", "obj.dragon_claws_ornament")
        val DRAGON_SCIMITARS = listOf("obj.dragon_scimitar", "obj.dragon_scimitar_ornament")
        val SHOVE_SPEARS =
            listOf(
                "obj.dragon_spear",
                "obj.dragon_spear_p",
                "obj.zamorak_spear",
                "obj.zamorak_hasta",
            )
        val BONE_DAGGERS =
            listOf(
                "obj.dttd_bone_dagger",
                "obj.dttd_bone_dagger_p",
                "obj.dttd_bone_dagger_p+",
                "obj.dttd_bone_dagger_p++",
            )
    }
}
