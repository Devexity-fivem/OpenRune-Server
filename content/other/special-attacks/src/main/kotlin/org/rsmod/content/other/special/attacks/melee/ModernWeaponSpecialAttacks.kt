package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.instant.InstantSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.getInvObj

class ModernWeaponSpecialAttacks @Inject constructor(private val npcList: NpcList) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val bindingTentacle = BindingTentacle(manager)
        registerMelee("obj.abyssal_tentacle", bindingTentacle)
        registerMelee("obj.br_abyssal_tentacle", bindingTentacle)
        registerMelee("obj.league_3_whip_tentacle", bindingTentacle)

        registerMelee("obj.rune_claws", Impale(manager))
        registerMelee("obj.granite_hammer", HammerBlow(manager))
        registerMelee("obj.olaf2_brine_sabre", Liquify(manager))

        val powerstab = Powerstab(manager, npcList)
        registerMelee("obj.dragon_2h_sword", powerstab)
        registerMelee("obj.br_dragon_2h", powerstab)
        registerMelee("obj.bh_dragon_2h_sword_corrupted", powerstab)

        val shieldBash = ShieldBash(manager, npcList)
        registerMelee("obj.dinhs_bulwark", shieldBash)
        registerMelee("obj.dinhs_bulwark_ornament", shieldBash)

        val bloodInfusion = BloodInfusion(manager)
        registerMelee("obj.dual_macuahuitl", bloodInfusion)
        registerMelee("obj.br_dual_macuahuitl", bloodInfusion)

        val scatterAshes = ScatterAshes(manager, npcList)
        registerMelee("obj.purging_staff", scatterAshes)
        registerMelee("obj.br_purging_staff", scatterAshes)

        registerMelee("obj.keris_partisan_corruption", WrathOfAmascut(manager))

        registerInstant("obj.keris_partisan_sun", TumekensLight())
        registerMelee("obj.verzik_special_weapon", VerzikWeapon(manager))

        val condemn = Condemn(manager)
        registerMagic("obj.wild_cave_accursed_charged", condemn)
        registerMagic("obj.wild_cave_accursed_charged_recol", condemn)

        val immolate = Immolate(manager)
        registerMagic("obj.nightmare_staff_volatile", immolate)
        registerMagic("obj.br_nightmare_staff_volatile", immolate)
        registerMagic("obj.deadman_blighted_volatile_staff", immolate)
        registerMagic("obj.deadman_nightmare_staff_volatile", immolate)

        registerMagic("obj.nightmare_staff_eldritch", Invocate(manager))
    }

    private class BindingTentacle(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            bindingTentacle(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            bindingTentacle(target, attack)
            return true
        }

        private fun ProtectedAccess.bindingTentacle(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                )
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            if (damage > 0) {
                when (target) {
                    is Player -> CombatEffects.freeze(target, 17)
                    is Npc -> CombatEffects.freeze(target, 17)
                }
            }
            manager.continueCombat(this, target)
        }
    }

    private class Impale(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            impale(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            impale(target, attack)
            return true
        }

        private fun ProtectedAccess.impale(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.1,
                    maxHitMultiplier = 1.1,
                )
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
        }
    }

    private class HammerBlow(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            hammerBlow(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            hammerBlow(target, attack)
            return true
        }

        private fun ProtectedAccess.hammerBlow(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                ) + 5
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
        }
    }

    private class Liquify(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            liquify(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            liquify(target, attack)
            return true
        }

        private fun ProtectedAccess.liquify(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.0,
                )
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            if (damage > 0) {
                val boost = damage / 4
                if (boost > 0) {
                    player.statAdd("stat.attack", boost, 0)
                    player.statAdd("stat.strength", boost, 0)
                    player.statAdd("stat.defence", boost, 0)
                }
            }
            manager.continueCombat(this, target)
        }
    }

    private class Powerstab(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            powerstab(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            powerstab(target, attack)
            return true
        }

        private fun ProtectedAccess.powerstab(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage = roll(target, attack)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)

            var hitCount = 1
            for (npc in npcList) {
                if (hitCount >= 14) break
                if (npc === target || !npc.isValidTarget()) continue
                if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                if (npc.coords.chebyshevDistance(player.coords) > 1) continue
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
            )
    }

    private class ShieldBash(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            shieldBash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            val damage = roll(target, attack)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.shieldBash(target: PathingEntity, attack: CombatAttack.Melee) {
            hitTwice(target, attack)
            for (npc in npcList) {
                if (npc === target || !npc.isValidTarget()) continue
                if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                if (npc.coords.chebyshevDistance(player.coords) > 5) continue
                hitTwice(npc, attack)
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.hitTwice(target: PathingEntity, attack: CombatAttack.Melee) {
            repeat(2) {
                val damage = roll(target, attack)
                manager.queueMeleeHit(this, target, damage)
                manager.giveCombatXp(this, target, attack, damage)
            }
        }

        private fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 1.0,
                maxHitMultiplier = 1.0,
            )
    }

    private class BloodInfusion(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            bloodInfusion(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            bloodInfusion(target, attack)
            return true
        }

        private fun ProtectedAccess.bloodInfusion(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            val maxHit =
                manager.rollMeleeMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.25,
                )
            val damage = random.of(0..maxHit).coerceAtLeast(ceil(maxHit * 0.25).toInt())
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            val selfDamage = player.stat("stat.hitpoints") / 4
            if (selfDamage > 0) {
                player.statSub("stat.hitpoints", selfDamage, 0)
            }
            manager.continueCombat(this, target)
        }
    }

    private class ScatterAshes(
        private val manager: SpecialAttackManager,
        private val npcList: NpcList,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            scatterAshes(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            val damage = roll(target, attack)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.scatterAshes(target: PathingEntity, attack: CombatAttack.Melee) {
            hit(target, attack)
            for (npc in npcList) {
                if (npc === target || !npc.isValidTarget()) continue
                if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                if (npc.visType.paramOrNull(params.demon) == null) continue
                if (npc.coords.chebyshevDistance(player.coords) > 1) continue
                hit(npc, attack)
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.hit(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage = roll(target, attack)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
        }

        private fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 1.0,
                maxHitMultiplier = 1.0,
            )
    }

    private class WrathOfAmascut(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            wrath(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            wrath(target, attack)
            return true
        }

        private fun ProtectedAccess.wrath(target: PathingEntity, attack: CombatAttack.Melee) {
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.25,
                )
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
        }
    }

    private class TumekensLight : InstantSpecialAttack {
        override suspend fun ProtectedAccess.activate(): Boolean {
            player.statSub("stat.prayer", 50, 0)
            val overheal = max(0, player.stat("stat.hitpoints") / 5)
            if (overheal > 0) {
                player.statAdd("stat.hitpoints", overheal, 0)
            }
            return true
        }
    }

    private class VerzikWeapon(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            val damage = random.of(75..150)
            manager.queueMeleeHit(this, target, damage)
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = false
    }

    private class Condemn(private val manager: SpecialAttackManager) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            condemn(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            condemn(target, attack)
            return true
        }

        private fun ProtectedAccess.condemn(target: PathingEntity, attack: CombatAttack.Staff) {
            val damage = roll(target, attack)
            manager.queueMagicHit(
                source = this,
                target = target,
                damage = damage,
                clientDelay = 40,
            )
            manager.giveCombatXp(this, target, attack, damage)
            if (damage > 0) {
                when (target) {
                    is Player -> {
                        for (statName in listOf("defence", "magic")) {
                            val drain = (target.stat("stat.$statName") * 15) / 100 + 1
                            target.statSub("stat.$statName", drain, 0)
                        }
                    }
                    is Npc -> {
                        for (statName in listOf("defence", "magic")) {
                            val drain = (npcStat(target, statName) * 15) / 100 + 1
                            setNpcStat(
                                target,
                                statName,
                                (npcStat(target, statName) - drain).coerceAtLeast(0),
                            )
                        }
                    }
                }
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Staff): Int {
            val baseMaxHit =
                getInvObj(attack.weapon)?.paramOrNull(params.spell_maxhit) ?: DEFAULT_BASE_MAX_HIT
            val accurate =
                manager.rollStaffAccuracy(
                    source = this,
                    target = target,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            return if (accurate) {
                manager.rollStaffMaxHit(
                    source = this,
                    target = target,
                    baseMaxHit = baseMaxHit,
                    multiplier = 1.0,
                )
            } else {
                0
            }
        }
    }

    private class Immolate(private val manager: SpecialAttackManager) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            immolate(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            immolate(target, attack)
            return true
        }

        private fun ProtectedAccess.immolate(target: PathingEntity, attack: CombatAttack.Staff) {
            val baseMaxHit =
                getInvObj(attack.weapon)?.paramOrNull(params.spell_maxhit) ?: DEFAULT_BASE_MAX_HIT
            val accurate =
                manager.rollStaffAccuracy(
                    source = this,
                    target = target,
                    attackStyle = attack.style,
                    multiplier = 1.5,
                )
            val damage =
                if (accurate) {
                    val maxHit =
                        manager.calculateStaffMaxHit(
                            source = this,
                            target = target,
                            baseMaxHit = baseMaxHit,
                            multiplier = 1.0,
                        )
                    ceil(maxHit * (0.49 + random.of(0..48) / 100.0)).toInt()
                } else {
                    0
                }
            manager.queueMagicHit(
                source = this,
                target = target,
                damage = damage,
                clientDelay = 40,
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.continueCombat(this, target)
        }
    }

    private class Invocate(private val manager: SpecialAttackManager) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            invocate(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            invocate(target, attack)
            return true
        }

        private fun ProtectedAccess.invocate(target: PathingEntity, attack: CombatAttack.Staff) {
            val baseMaxHit =
                getInvObj(attack.weapon)?.paramOrNull(params.spell_maxhit) ?: DEFAULT_BASE_MAX_HIT
            val accurate =
                manager.rollStaffAccuracy(
                    source = this,
                    target = target,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                )
            val damage =
                if (accurate) {
                    manager.rollStaffMaxHit(
                        source = this,
                        target = target,
                        baseMaxHit = baseMaxHit,
                        multiplier = 1.0,
                    )
                } else {
                    0
                }
            manager.queueMagicHit(
                source = this,
                target = target,
                damage = damage,
                clientDelay = 40,
            )
            manager.giveCombatXp(this, target, attack, damage)
            if (damage > 0) {
                player.statAdd("stat.prayer", minOf(damage / 2, 120), 0)
            }
            manager.continueCombat(this, target)
        }
    }

    private companion object {
        private const val DEFAULT_BASE_MAX_HIT = 44
    }
}
