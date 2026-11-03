package org.rsmod.content.other.special.attacks.melee

import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

class AbyssalSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val energyDrain = EnergyDrain(manager)
        for (obj in ABYSSAL_WHIPS) {
            registerMelee(obj, energyDrain)
        }

        val puncture = AbyssalPuncture(manager)
        for (obj in ABYSSAL_DAGGERS) {
            registerMelee(obj, puncture)
        }

        registerMelee("obj.abyssal_bludgeon", Penance(manager))
    }

    private class EnergyDrain(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            energyDrain(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            energyDrain(target, attack)
            return true
        }

        private fun ProtectedAccess.energyDrain(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.slayer_whip_sp_attack")
            spotanim(
                "spotanim.sp_attack_abyssal_whip",
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
                val stolen = (target.runEnergy * 10) / 100
                target.runEnergy -= stolen
                player.runEnergy = min(1000, player.runEnergy + stolen)
            }
            manager.continueCombat(this, target)
        }
    }

    private class AbyssalPuncture(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
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
            anim("seq.abyssal_dagger_special")
            spotanim(
                "spotanim.abyssal_dagger_special_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val accurate =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = MeleeAttackType.Slash,
                    multiplier = 1.25,
                )
            var total = 0
            if (accurate) {
                repeat(2) {
                    val maxHit =
                        manager.calculateMeleeMaxHit(
                            source = this,
                            target = target,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            multiplier = 0.85,
                        )
                    val damage = random.of(0..maxHit)
                    manager.queueMeleeHit(this, target, damage)
                    total += damage
                }
            } else {
                manager.queueMeleeHit(this, target, 0)
                manager.queueMeleeHit(this, target, 0)
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }
    }

    private class Penance(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            penance(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            penance(target, attack)
            return true
        }

        private fun ProtectedAccess.penance(target: PathingEntity, attack: CombatAttack.Melee) {
            anim("seq.abyssal_bludgeon_special_attack")
            spotanim(
                "spotanim.abyssal_miasma_spotanim_bludgeon",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val missingPrayer =
                (player.statBase("stat.prayer") - player.stat("stat.prayer")).coerceAtLeast(0)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0 + (missingPrayer * 0.005),
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
        }
    }

    private companion object {
        val ABYSSAL_WHIPS =
            listOf(
                "obj.abyssal_whip",
                "obj.abyssal_whip_ice",
                "obj.abyssal_whip_lava",
                "obj.br_abyssal_whip",
                "obj.league_3_whip",
            )
        val ABYSSAL_DAGGERS =
            listOf(
                "obj.abyssal_dagger",
                "obj.abyssal_dagger_p",
                "obj.abyssal_dagger_p+",
                "obj.abyssal_dagger_p++",
                "obj.bh_abyssal_dagger_imbue",
                "obj.bh_abyssal_dagger_p_imbue",
                "obj.bh_abyssal_dagger_p+_imbue",
                "obj.bh_abyssal_dagger_p++_imbue",
            )
    }
}
