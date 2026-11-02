package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

class GodswordSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.ags", Armadyl(manager))
        registerMelee("obj.bgs", Bandos(manager))
        registerMelee("obj.sgs", Saradomin(manager))
        registerMelee("obj.zgs", Zamorak(manager))
        registerMelee("obj.ancient_godsword", AncientGodsword(manager))
    }

    private abstract class Godsword(protected val manager: SpecialAttackManager) :
        MeleeSpecialAttack {
        protected abstract val anim: String
        protected abstract val spot: String

        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            strike(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            strike(target, attack)
            return true
        }

        protected fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee) {
            anim(anim)
            spotanim(spot, slot = constants.spotanim_slot_combat, height = 96)
            soundSynth("synth.godwars_godsword_special_attack")
            val damage = roll(target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            effect(target, damage)
            manager.continueCombat(this, target)
        }

        protected open fun ProtectedAccess.roll(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.0,
                maxHitMultiplier = 1.0,
                blockType = MeleeAttackType.Slash,
            )

        protected open fun ProtectedAccess.effect(target: PathingEntity, damage: Int) {}
    }

    private class Armadyl(manager: SpecialAttackManager) : Godsword(manager) {
        override val anim = "seq.ags_special_player"
        override val spot = "spotanim.godwars_godsword_armadyl_spot"

        override fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.0,
                maxHitMultiplier = 1.375,
                blockType = MeleeAttackType.Slash,
            )
    }

    private class Bandos(manager: SpecialAttackManager) : Godsword(manager) {
        override val anim = "seq.bgs_special_player"
        override val spot = "spotanim.godwars_godsword_bandos_spot"

        override fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.0,
                maxHitMultiplier = 1.21,
                blockType = MeleeAttackType.Slash,
            )

        override fun ProtectedAccess.effect(target: PathingEntity, damage: Int) {
            if (damage > 0) {
                drainCombatStats(target, damage, listOf("defence", "strength", "attack", "magic", "ranged"))
            }
        }
    }

    private class Saradomin(manager: SpecialAttackManager) : Godsword(manager) {
        override val anim = "seq.sgs_special_player"
        override val spot = "spotanim.godwars_godsword_saradomin_spot"

        override fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.0,
                maxHitMultiplier = 1.10,
                blockType = MeleeAttackType.Slash,
            )

        override fun ProtectedAccess.effect(target: PathingEntity, damage: Int) {
            if (damage > 0) {
                statHeal("stat.hitpoints", constant = damage / 2, percent = 0)
                statHeal("stat.prayer", constant = damage / 4, percent = 0)
            }
        }
    }

    private class Zamorak(manager: SpecialAttackManager) : Godsword(manager) {
        override val anim = "seq.zgs_special_player"
        override val spot = "spotanim.godwars_godsword_zamorak_spot"

        override fun ProtectedAccess.roll(target: PathingEntity, attack: CombatAttack.Melee): Int =
            manager.rollMeleeDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = 2.0,
                maxHitMultiplier = 1.10,
                blockType = MeleeAttackType.Slash,
            )

        override fun ProtectedAccess.effect(target: PathingEntity, damage: Int) {
            if (damage > 0) {
                if (target is Npc) {
                    CombatEffects.freeze(target, 33)
                } else if (target is Player) {
                    CombatEffects.freeze(target, 33)
                }
            }
        }
    }

    private class AncientGodsword(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = sacrifice(target, attack, damageOnMark = 25, healOnMark = 15)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = sacrifice(target, attack, damageOnMark = 15, healOnMark = 10)

        private suspend fun ProtectedAccess.sacrifice(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            damageOnMark: Int,
            healOnMark: Int,
        ): Boolean {
            anim("seq.ngs_special_player")
            spotanim(
                "spotanim.ngs_special_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            soundSynth("synth.godwars_godsword_special_attack")

            val markTile = target.coords
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
            manager.continueCombat(this, target)

            if (damage > 0) {
                delay(8)
                val fled =
                    markTile.level != target.coords.level ||
                        markTile.chebyshevDistance(target.coords) >= 5
                val alive =
                    when (target) {
                        is Npc -> target.hitpoints > 0
                        is Player -> target.hitpoints > 0
                        else -> true
                    }
                if (!fled && alive) {
                    manager.queueMeleeHit(this, target, damageOnMark)
                    player.statAdd("stat.hitpoints", constant = healOnMark, percent = 0)
                }
            }
            return true
        }
    }
}

internal fun drainCombatStats(target: PathingEntity, damage: Int, order: List<String>) {
    var remaining = damage
    when (target) {
        is Player -> {
            val stats = mutableListOf("defence", "strength", "prayer")
            stats += order.drop(2)
            for (stat in stats) {
                if (remaining <= 0) break
                val level = target.stat("stat.$stat")
                val drained = minOf(remaining, level)
                target.statSub("stat.$stat", drained, 0)
                remaining -= drained
            }
        }
        is Npc -> {
            for (stat in order) {
                if (remaining <= 0) break
                val level = npcStat(target, stat)
                val drained = minOf(remaining, level)
                setNpcStat(target, stat, level - drained)
                remaining -= drained
            }
        }
    }
}

internal fun npcStat(npc: Npc, stat: String): Int =
    when (stat) {
        "attack" -> npc.attackLvl
        "strength" -> npc.strengthLvl
        "defence" -> npc.defenceLvl
        "ranged" -> npc.rangedLvl
        "magic" -> npc.magicLvl
        else -> 0
    }

internal fun setNpcStat(npc: Npc, stat: String, level: Int) {
    when (stat) {
        "attack" -> npc.attackLvl = level
        "strength" -> npc.strengthLvl = level
        "defence" -> npc.defenceLvl = level
        "ranged" -> npc.rangedLvl = level
        "magic" -> npc.magicLvl = level
    }
}
