package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.content.other.special.attacks.melee.drainCombatStats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

class BowSpecialAttacks
@Inject
constructor(private val ammunition: RangedAmmoManager, private val npcList: NpcList) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val snapshot = Snapshot(manager, ammunition)
        for (obj in listOf("obj.magic_shortbow", "obj.magic_shortbow_i", "obj.br_magic_bow", "obj.daganoth_cave_magic_shortbow")) {
            registerRanged(obj, snapshot)
        }

        val powershot = Powershot(manager, ammunition)
        registerRanged("obj.magic_longbow", powershot)
        registerRanged("obj.trail_composite_bow_magic", powershot)

        val lightBallista = ConcentratedShot(manager, ammunition, "seq.ballista_special_attack", 10)
        for (obj in listOf("obj.light_ballista", "obj.br_light_ballista")) {
            registerRanged(obj, lightBallista)
        }
        val heavyBallista = ConcentratedShot(manager, ammunition, "seq.ballista02_special_attack", 11)
        for (obj in listOf("obj.heavy_ballista", "obj.heavy_ballista_ornament", "obj.br_heavy_ballista")) {
            registerRanged(obj, heavyBallista)
        }

        val armadylEye = ArmadylEye(manager, ammunition)
        for (obj in listOf("obj.acb", "obj.br_acb")) {
            registerRanged(obj, armadylEye)
        }
        val annihilate = Annihilate(manager, ammunition, npcList)
        for (obj in listOf("obj.xbows_crossbow_dragon", "obj.bh_xbows_crossbow_dragon_corrupted", "obj.br_xbows_crossbow_dragon")) {
            registerRanged(obj, annihilate)
        }
        registerRanged("obj.dttd_bone_crossbow", Snipe(manager, ammunition))
        val phantomStrike = PhantomStrike(manager, ammunition)
        for (obj in listOf("obj.morrigans_javelin", "obj.morrigans_javelin_bh", "obj.br_morrigans_javelin")) {
            registerRanged(obj, phantomStrike)
        }
        registerRanged("obj.wild_cave_webweaver_charged", Swarm(manager, ammunition))

        val momentumThrow = MomentumThrow(manager, ammunition)
        registerRanged("obj.dragon_thrownaxe", momentumThrow)
        registerRanged("obj.br_dragon_thrownaxe", momentumThrow)

        val hamstring = Hamstring(manager, ammunition)
        registerRanged("obj.morrigans_thrownaxe", hamstring)
        registerRanged("obj.morrigans_thrownaxe_bh", hamstring)

        registerRanged("obj.rune_thrownaxe", Chainhit(manager, ammunition, npcList))
    }

    private abstract class BowSpecial(
        protected val manager: SpecialAttackManager,
        protected val ammunition: RangedAmmoManager,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = fire(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = fire(target, attack)

        protected abstract fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean

        protected fun ProtectedAccess.resolveWeaponAmmo(
            attack: CombatAttack.Ranged,
        ): Triple<ItemServerType, ItemServerType?, Boolean>? {
            val righthandType = getInvObj(attack.weapon) ?: return null
            val wornAmmoType = getOrNull(player.quiver)
            val (ammoType, fromStored) =
                RangedAmmunition.resolveAmmo(player, righthandType, wornAmmoType)
            val canUseAmmo = ammunition.attemptAmmoUsage(player, righthandType, ammoType)
            if (!canUseAmmo) {
                manager.stopCombat(this)
                return null
            }
            return Triple(righthandType, ammoType, fromStored)
        }

        protected fun ProtectedAccess.fireProjectile(
            target: PathingEntity,
            righthandType: ItemServerType,
            ammoType: ItemServerType?,
            fromStored: Boolean,
            travelOverride: String? = null,
            launchOverride: String? = null,
        ): Pair<Int, Int>? {
            val usingThrown = righthandType.isCategoryType("category.throwing_weapon")
            val projSource = if (usingThrown) righthandType else ammoType ?: return null

            val projectileID = righthandType.paramOrNull(params.proj_type)?.id ?: return null
            val travelSpotanim =
                travelOverride ?: projSource.paramOrNull(params.proj_travel)?.let {
                    RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id)
                } ?: return null
            val projanimType = RSCM.getReverseMapping(RSCMType.PROJANIM, projectileID)

            val launchSpotanim =
                launchOverride
                    ?: projSource.paramOrNull(params.proj_launch)?.let {
                        RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id)
                    }
            if (launchSpotanim != null) {
                spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)
            }

            val projanim = manager.spawnProjectile(this, target, travelSpotanim, projanimType)
            val (serverDelay, clientDelay) = projanim.durations
            consumeAmmo(target, righthandType, ammoType, fromStored, serverDelay)
            return serverDelay to clientDelay
        }

        protected fun ProtectedAccess.consumeAmmo(
            target: PathingEntity,
            righthandType: ItemServerType,
            ammoType: ItemServerType?,
            fromStored: Boolean,
            dropDelay: Int,
            useCount: Int = 1,
        ) {
            val usingThrown = righthandType.isCategoryType("category.throwing_weapon")
            when {
                usingThrown ->
                    ammunition.useThrownWeapon(
                        player,
                        righthandType,
                        target.coords,
                        dropDelay = dropDelay,
                        useCount = useCount,
                    )
                ammoType != null && fromStored ->
                    ammunition.useStoredQuiverAmmo(
                        player,
                        ammoType,
                        target.coords,
                        dropDelay = dropDelay,
                        useCount = useCount,
                    )
                ammoType != null ->
                    ammunition.useQuiverAmmo(
                        player,
                        ammoType,
                        target.coords,
                        dropDelay = dropDelay,
                        useCount = useCount,
                    )
            }
        }

        protected fun ProtectedAccess.rollDamage(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            accuracyMultiplier: Double,
            maxHitMultiplier: Double = 1.0,
        ): Int =
            manager.rollRangedDamage(
                source = this,
                target = target,
                attack = attack,
                accuracyMultiplier = accuracyMultiplier,
                maxHitMultiplier = maxHitMultiplier,
                blockType = attack.type,
            )
    }

    private class Snapshot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            if (ammoType == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val ammoCount =
                if (fromStored) {
                    RangedAmmunition.storedQuiverAmmo(player)?.second ?: 0
                } else {
                    player.quiver?.count ?: 0
                }
            if (ammoCount < 2) {
                manager.stopCombat(this)
                mes("You need to have at least 2 arrows in your quiver for this special attack.")
                return false
            }
            anim("seq.snapshot")
            spotanim(
                "spotanim.sp_attack_snapshot_spotanim",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )

            val travelSpot =
                ammoType.paramOrNull(params.proj_travel)?.let {
                    RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id)
                } ?: run {
                    manager.stopCombat(this)
                    return false
                }

            var total = 0
            repeat(2) { index ->
                val delays = fireProjectile(target, righthandType, ammoType, fromStored, travelSpot)
                if (delays != null) {
                    val (serverDelay, clientDelay) = delays
                    val damage = rollDamage(target, attack, accuracyMultiplier = 1.43)
                    manager.giveCombatXp(this, target, attack, damage)
                    manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
                    total += damage
                }
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Powershot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.human_bow")
            val delays = fireProjectile(target, righthandType, ammoType, fromStored)
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val maxHit =
                manager.calculateRangedMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                    boltSpecDamage = 0,
                )
            val damage = random.of(0..maxHit)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
            return true
        }
    }

    private class ConcentratedShot(
        manager: SpecialAttackManager,
        ammunition: RangedAmmoManager,
        private val anim: String,
        private val nextAttackDelay: Int,
    ) : BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim(anim)
            spotanim(
                "spotanim.ballista_special",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val delays = fireProjectile(target, righthandType, ammoType, fromStored)
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val damage = rollDamage(target, attack, accuracyMultiplier = 1.25, maxHitMultiplier = 1.25)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
            manager.setNextAttackDelay(this, nextAttackDelay)
            manager.continueCombat(this, target)
            return true
        }
    }

    private class ArmadylEye(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.armadyl_special_attack")
            spotanim(
                "spotanim.acb_specialattack",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    launchOverride = "spotanim.acb_crossbowbolt_travel",
                )
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val damage = rollDamage(target, attack, accuracyMultiplier = 2.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Annihilate(
        manager: SpecialAttackManager,
        ammunition: RangedAmmoManager,
        private val npcList: NpcList,
    ) : BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.human_crossbow")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    launchOverride = "spotanim.dragon_crossbowbolt_travel",
                )
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val damage = rollDamage(target, attack, accuracyMultiplier = 1.0, maxHitMultiplier = 1.2)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)

            if (target is Npc && mapMultiway()) {
                var hitCount = 1
                for (npc in npcList) {
                    if (hitCount >= 9) break
                    if (npc === target || !npc.isValidTarget()) continue
                    if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                    if (
                        npc.coords.level != target.coords.level ||
                            npc.coords.chebyshevDistance(target.coords) > 1
                    ) {
                        continue
                    }
                    val splashDamage =
                        rollDamage(target = npc, attack = attack, accuracyMultiplier = 1.0, maxHitMultiplier = 0.8)
                    manager.giveCombatXp(this, npc, attack, splashDamage)
                    manager.queueRangedDamage(this, npc, ammoType, splashDamage, serverDelay)
                    hitCount++
                }
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Snipe(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.dttd_player_fire_bone_crossbow")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    travelOverride = "spotanim.dttd_bone_crossbowbolt_travel_sp_attack",
                )
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val damage = rollDamage(target, attack, accuracyMultiplier = 3.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
            if (damage > 0) {
                drainCombatStats(target, damage, listOf("defence"))
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class PhantomStrike(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, _, _) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.weapon_morrigans_javelin_special01")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType = righthandType,
                    fromStored = false,
                    travelOverride = "spotanim.morrigans_javelin_travel",
                )
            if (delays == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val (serverDelay, clientDelay) = delays
            val damage = rollDamage(target, attack, accuracyMultiplier = 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, righthandType, damage, clientDelay, serverDelay)

            if (damage > 0 && target is Player) {
                val bleedTotal = (damage * 75) / 100
                val bleedTicks = (bleedTotal + 9) / 10
                var dealt = 0
                for (tick in 1..bleedTicks) {
                    val bleed = minOf(10, bleedTotal - dealt)
                    if (bleed <= 0) break
                    manager.queueRangedDamage(this, target, righthandType, bleed, serverDelay + tick * 3)
                    dealt += bleed
                }
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Swarm(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            if (ammoType == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            val ammoCount =
                if (fromStored) {
                    RangedAmmunition.storedQuiverAmmo(player)?.second ?: 0
                } else {
                    player.quiver?.count ?: 0
                }
            if (ammoCount < 4) {
                manager.stopCombat(this)
                mes("You need to have at least 4 arrows in your quiver for this special attack.")
                return false
            }
            anim("seq.human_special01_webweaver")
            spotanim(
                "spotanim.fx_webweaver01_launch_spotanim",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )

            var total = 0
            var hitDamage = 0
            repeat(4) {
                val delays = fireProjectile(target, righthandType, ammoType, fromStored)
                if (delays != null) {
                    val (serverDelay, clientDelay) = delays
                    val damage = rollDamage(target, attack, accuracyMultiplier = 1.0)
                    manager.giveCombatXp(this, target, attack, damage)
                    manager.queueRangedHit(this, target, ammoType, damage, clientDelay, serverDelay)
                    total += damage
                    if (damage > 0) hitDamage++
                }
            }
            if (hitDamage > 0 && target is Player) {
                CombatEffects.poison(target, 4)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class MomentumThrow(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.human_dragon_taxe_spec")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    travelOverride = "spotanim.dragon_taxe_travel_spec",
                    launchOverride = "spotanim.dragon_taxe_launch_spec",
                )
            val (serverDelay, clientDelay) = delays ?: (1 to 0)
            val damage = rollDamage(target, attack, accuracyMultiplier = 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                this,
                target,
                righthandType,
                damage,
                clientDelay,
                serverDelay,
            )
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Hamstring(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.weapon_morrigans_throwingaxe_special01")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    travelOverride = "spotanim.morrigans_taxe_travel",
                )
            val (serverDelay, clientDelay) = delays ?: (1 to 0)
            val damage =
                rollDamage(target, attack, accuracyMultiplier = 1.0, maxHitMultiplier = 1.2)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                this,
                target,
                righthandType,
                damage,
                clientDelay,
                serverDelay,
            )
            if (damage > 0 && target is Player) {
                target.runEnergy = (target.runEnergy - damage * 600).coerceAtLeast(0)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Chainhit(
        manager: SpecialAttackManager,
        ammunition: RangedAmmoManager,
        private val npcList: NpcList,
    ) : BowSpecial(manager, ammunition) {
        override fun ProtectedAccess.fire(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val (righthandType, ammoType, fromStored) = resolveWeaponAmmo(attack) ?: return false
            anim("seq.chainhit")
            val delays =
                fireProjectile(
                    target,
                    righthandType,
                    ammoType,
                    fromStored,
                    travelOverride = "spotanim.sp_attack_chainhit_travel_spotanim",
                    launchOverride = "spotanim.sp_attack_chainhit_launch_spotanim",
                )
            val (serverDelay, clientDelay) = delays ?: (1 to 0)
            val damage = rollDamage(target, attack, accuracyMultiplier = 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(
                this,
                target,
                righthandType,
                damage,
                clientDelay,
                serverDelay,
            )

            if (damage > 0) {
                var hitCount = 0
                for (npc in npcList) {
                    if (hitCount >= 5) break
                    if (npc === target || !npc.isValidTarget()) continue
                    if (!npc.visType.hasOp(InteractionOp.Op2.slot)) continue
                    if (npc.coords.chebyshevDistance(target.coords) > 3) continue
                    val bounceDamage = rollDamage(npc, attack, accuracyMultiplier = 1.0)
                    manager.giveCombatXp(this, npc, attack, bounceDamage)
                    manager.queueRangedDamage(
                        this,
                        npc,
                        righthandType,
                        bounceDamage,
                        hitDelay = serverDelay + 1,
                    )
                    hitCount++
                }
            }
            manager.continueCombat(this, target)
            return true
        }
    }
}
