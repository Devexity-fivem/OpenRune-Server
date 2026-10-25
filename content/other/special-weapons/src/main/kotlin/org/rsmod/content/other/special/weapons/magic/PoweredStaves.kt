package org.rsmod.content.other.special.weapons.magic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/** Powered staves: built-in-spell weapons (tridents, sanguinesti, sceptres, etc.). */
class PoweredStaves
@Inject
constructor(
    private val charges: CombatChargeManager,
    private val objCharges: ObjChargeManager,
    private val areaChecker: AreaChecker,
    private val attackManager: PlayerAttackManager,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.tots", PoweredStaff(manager, SEAS_TRIDENT, full = true))
        register("obj.tots_charged", PoweredStaff(manager, SEAS_TRIDENT))
        register(
            "obj.tots_i_charged",
            PoweredStaff(manager, SEAS_TRIDENT.copy(maxCharges = 20_000)),
        )
        register("obj.toxic_tots_charged", PoweredStaff(manager, SWAMP_TRIDENT))
        register(
            "obj.toxic_tots_i_charged",
            PoweredStaff(manager, SWAMP_TRIDENT.copy(maxCharges = 20_000)),
        )
        register("obj.sanguinesti_staff", PoweredStaff(manager, SANGUINESTI))
        register("obj.sanguinesti_staff_or", PoweredStaff(manager, SANGUINESTI_OR))
        register("obj.warped_sceptre", PoweredStaff(manager, WARPED_SCEPTRE))
        register("obj.wild_cave_sceptre_charged", PoweredStaff(manager, THAMMARON))
        register("obj.wild_cave_accursed_charged", PoweredStaff(manager, ACCURSED))
        register("obj.rat_bone_staff", PoweredStaff(manager, BONE_STAFF))
        register("obj.eye_of_ayak", PoweredStaff(manager, EYE_OF_AYAK))
        register("obj.deadman_starter_staff", PoweredStaff(manager, STARTER_STAFF))
        register("obj.verzik_special_weapon", PoweredStaff(manager, DAWNBRINGER))
        register("obj.deadman_blighted_tumekens_shadow", PoweredStaff(manager, BLIGHTED_SHADOW))

        val uncharged = UnchargedPoweredStaff(manager)
        for (obj in UNCHARGED_STAVES) {
            register(obj, uncharged)
        }
    }

    private inner class PoweredStaff(
        private val manager: WeaponAttackManager,
        private val spec: Spec,
        private val full: Boolean = false,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (spec.ratsOnly && !target.isRat()) {
                mes("You can only use this weapon against rats.")
                manager.stopCombat(this)
                return true
            }
            cast(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (spec.ratsOnly) {
                mes("You can only use this weapon against rats.")
                manager.stopCombat(this)
                return true
            }
            // Built-in powered-staff spells cannot be cast on players in the Wilderness;
            // the wilderness sceptres are the exception.
            if (!spec.wildernessSceptre && target.coords.isInWilderness(areaChecker)) {
                manager.stopCombat(this)
                return true
            }
            cast(target, attack)
            return true
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Staff) {
            val detract = detractCharge()
            if (detract == Detract.Empty) {
                if (spec.activationDeposit > 0) {
                    mes(spec.dryMessage)
                }
                manager.stopCombat(this)
                return
            }

            manager.setNextAttackDelay(this, attackDelayFor(target))
            anim(spec.castSeq)
            spec.castSpot?.let { spotanim(it, height = spec.castSpotHeight) }

            val proj =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = spec.travelSpot,
                    projanim = spec.projanim,
                )
            val (serverDelay, clientDelay) = proj.durations

            // The wilderness sceptres gain +50% magic accuracy and damage against npcs inside
            // the Wilderness.
            val wildNpcBonus =
                spec.wildernessSceptre &&
                    target is Npc &&
                    target.coords.isInWilderness(areaChecker)
            val multiplier = if (wildNpcBonus) WILDERNESS_BONUS else 1.0

            val splash =
                if (multiplier == 1.0) {
                    manager.rollStaffSplash(this, target, attack)
                } else {
                    !attackManager.rollStaffAccuracy(player, target, attack.style, multiplier)
                }

            if (splash) {
                manager.playSplashFx(this, target, clientDelay, spec.castSound, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                // `flatBonus` covers the bone staff's +10 vs rats; `ratsOnly` guarantees the
                // target is a rat by the time we reach this branch.
                val baseMaxHit =
                    max(1, (spec.maxHit(player.magicLvl) * multiplier).toInt() + spec.flatBonus)
                val damage = manager.rollStaffMaxHit(this, target, baseMaxHit)
                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = spec.castSound,
                    soundRadius = 10,
                    hitSpot = spec.impactSpot,
                    hitSpotHeight = spec.impactHeight,
                    hitSound = spec.hitSound,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)
                if (damage > 0) {
                    spec.onHit(this, target, damage)
                }
            }

            if (detract == Detract.RanDry) {
                manager.stopCombat(this)
                mes(spec.dryMessage)
                return
            }
            manager.continueCombat(this, target)
        }

        /**
         * Consumes one charge for this cast. `full` staves (`tots`) treat `vars == 0` as a full
         * charge pool and convert into the partially-charged variant on their first cast.
         * Wilderness sceptres cannot attack once only their locked activation deposit remains.
         */
        private fun ProtectedAccess.detractCharge(): Detract {
            if (spec.chargeVarobjs.isEmpty()) {
                return Detract.Ok
            }
            // Weapons with multiple charge pools (Eye of Ayak: tears or runes) consume from
            // whichever pool currently holds charges; the two can never be filled at once.
            val varobj =
                spec.chargeVarobjs.firstOrNull { charges.getWeaponCharges(player, it) > 0 }
                    ?: spec.chargeVarobjs.first()
            val current = charges.getWeaponCharges(player, varobj)

            return when {
                full && current == 0 -> {
                    objCharges.addCharges(
                        player.worn,
                        Wearpos.RightHand.slot,
                        spec.maxCharges - 1,
                        varobj,
                        spec.maxCharges,
                    )
                    Detract.Ok
                }
                current == 0 -> Detract.Empty
                spec.activationDeposit > 0 && current <= spec.activationDeposit -> Detract.Empty
                else -> {
                    val result = charges.attemptDetractWeapon(player, varobj)
                    when {
                        result.isFailure() -> Detract.Empty
                        result.fullyUncharged -> Detract.RanDry
                        else -> Detract.Ok
                    }
                }
            }
        }

        private fun attackDelayFor(target: PathingEntity): Int {
            // Rat bone weapons have no attack cooldown against Scurrius' summoned giant rats.
            if (spec.ratsOnly && target is Npc && target.type.id in SCURRIUS_RAT_IDS) {
                return 0
            }
            return spec.attackRate
        }

        private fun Npc.isRat(): Boolean =
            type.id in RAT_BOSS_IDS || name.contains("rat", ignoreCase = true)
    }

    private enum class Detract {
        Ok,
        Empty,
        RanDry,
    }

    private class UnchargedPoweredStaff(private val manager: WeaponAttackManager) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminate()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminate()
            return true
        }

        private fun ProtectedAccess.terminate() {
            val name = player.righthand?.let { getInvObj(it).name } ?: "weapon"
            mes("Your $name has no charges.")
            manager.stopCombat(this)
        }
    }

    internal data class Spec(
        val maxHit: (Int) -> Int,
        val flatBonus: Int = 0,
        val chargeVarobjs: List<String> = emptyList(),
        val maxCharges: Int = 0,
        val activationDeposit: Int = 0,
        val castSeq: String = "seq.human_castwave_staff",
        val castSpot: String? = null,
        val castSpotHeight: Int = 0,
        val travelSpot: String,
        val impactSpot: String? = null,
        val impactHeight: Int = 0,
        val castSound: String? = "synth.shadow_cast",
        val hitSound: String? = null,
        val projanim: String = "projanim.magic_spell",
        val attackRate: Int = 4,
        val wildernessSceptre: Boolean = false,
        val ratsOnly: Boolean = false,
        val dryMessage: String = "Your staff has run out of charges.",
        val onHit: ProtectedAccess.(target: PathingEntity, damage: Int) -> Unit = { _, _ -> },
    )

    companion object {
        private const val WILDERNESS_BONUS = 1.5

        private val SCURRIUS_RAT_IDS =
            setOf(
                "npc.rat_boss_giant_rat".asRSCM(RSCMType.NPC),
                "npc.rat_boss_giant_rat_patrol".asRSCM(RSCMType.NPC),
            )

        private val RAT_BOSS_IDS =
            setOf(
                "npc.rat_boss_normal".asRSCM(RSCMType.NPC),
                "npc.rat_boss_instance".asRSCM(RSCMType.NPC),
            ) + SCURRIUS_RAT_IDS

        private val UNCHARGED_STAVES =
            listOf(
                "obj.tots_uncharged",
                "obj.tots_i_uncharged",
                "obj.toxic_tots_uncharged",
                "obj.toxic_tots_i_uncharged",
                "obj.sanguinesti_staff_uncharged",
                "obj.sanguinesti_staff_uncharged_or",
                "obj.warped_sceptre_uncharged",
                "obj.wild_cave_sceptre_uncharged",
                "obj.wild_cave_accursed_uncharged",
                "obj.eye_of_ayak_uncharged",
            )

        internal val SEAS_TRIDENT =
            Spec(
                maxHit = { lvl -> lvl / 3 - 5 },
                chargeVarobjs = listOf("varobj.trident_charges"),
                maxCharges = 2_500,
                castSpot = "spotanim.slayer_tots_casting",
                travelSpot = "spotanim.slayer_tots_projectile",
                impactSpot = "spotanim.slayer_tots_impact",
                impactHeight = 124,
                dryMessage = "Your trident has run out of charges.",
            )

        internal val SWAMP_TRIDENT =
            SEAS_TRIDENT.copy(
                maxHit = { lvl -> lvl / 3 - 2 },
                castSpot = "spotanim.toxic_tots_casting",
                travelSpot = "spotanim.toxic_tots_projectile",
                impactSpot = "spotanim.toxic_tots_impact",
            )

        internal val SANGUINESTI =
            Spec(
                maxHit = { lvl -> lvl / 3 - 1 },
                chargeVarobjs = listOf("varobj.sanguinesti_charges"),
                maxCharges = 20_000,
                castSpot = "spotanim.sanguinesti_staff_casting",
                travelSpot = "spotanim.sanguinesti_staff_travel",
                impactSpot = "spotanim.sanguinesti_staff_impact",
                impactHeight = 124,
                dryMessage = "Your Sanguinesti staff has run out of charges.",
                onHit = { target, damage ->
                    // Rev-240 passive: 1/6 chance to heal for half the damage dealt.
                    if (target is Npc && random.of(0, 5) == 0) {
                        statHeal("stat.hitpoints", constant = damage / 2, percent = 0)
                        target.spotanim("spotanim.sanguinesti_staff_heal", height = 124)
                    }
                },
            )

        internal val SANGUINESTI_OR =
            SANGUINESTI.copy(
                castSpot = "spotanim.sanguinesti_staff_casting_justiciar",
                travelSpot = "spotanim.sanguinesti_staff_travel_justiciar",
                impactSpot = "spotanim.sanguinesti_staff_impact_justiciar",
            )

        internal val WARPED_SCEPTRE =
            Spec(
                maxHit = { lvl -> (8 * lvl + 96) / 37 },
                chargeVarobjs = listOf("varobj.warped_sceptre_charges"),
                maxCharges = 20_000,
                castSeq = "seq.pog_warped_sceptre_attack",
                castSpot = "spotanim.vfx_warped_sceptre_cast",
                travelSpot = "spotanim.vfx_warped_sceptre_projectile_projectile",
                impactSpot = "spotanim.vfx_warped_sceptre_projectile_impact",
                impactHeight = 124,
                dryMessage = "Your warped sceptre has run out of charges.",
            )

        internal val THAMMARON =
            Spec(
                maxHit = { lvl -> lvl / 3 - 8 },
                chargeVarobjs = listOf("varobj.wilderness_ether_charges"),
                maxCharges = 17_000,
                activationDeposit = 1_000,
                travelSpot = "spotanim.spells_thammaron01_travel01",
                wildernessSceptre = true,
                dryMessage = "Your sceptre has run out of revenant ether.",
            )

        internal val ACCURSED =
            THAMMARON.copy(
                maxHit = { lvl -> lvl / 3 - 6 },
                travelSpot = "spotanim.spells_vetion01_travel",
            )

        internal val BONE_STAFF =
            Spec(
                maxHit = { lvl -> lvl / 3 + 5 },
                flatBonus = 10,
                chargeVarobjs = listOf("varobj.rat_bone_charges"),
                maxCharges = 20_000,
                castSpot = "spotanim.vfx_weapon_staff_rat01_spotanim",
                travelSpot = "spotanim.spells_ratbone01_travel01",
                impactSpot = "spotanim.vfx_weapon_staff_rat01_impact_01",
                impactHeight = 124,
                ratsOnly = true,
                dryMessage = "Your bone staff has run out of charges.",
                onHit = { _, _ ->
                    // The bone staff's built-in spell grants 35 base Magic xp on a hit.
                    statAdvance("stat.magic", 35.0)
                },
            )

        internal val EYE_OF_AYAK =
            Spec(
                maxHit = { lvl -> lvl / 3 - 6 },
                chargeVarobjs =
                    listOf("varobj.eye_of_ayak_tear_charges", "varobj.eye_of_ayak_rune_charges"),
                maxCharges = 50_000,
                attackRate = 3,
                castSeq = "seq.human_eye_of_ayak_normal",
                castSpot = "spotanim.vfx_ayak_player_normal_spotanim",
                travelSpot = "spotanim.vfx_ayak_normal_projectile",
                impactSpot = "spotanim.vfx_ayak_normal_impact",
                impactHeight = 124,
                dryMessage = "Your Eye of Ayak has run out of charges.",
            )

        internal val STARTER_STAFF =
            Spec(
                maxHit = { 8 },
                attackRate = 5,
                castSpot = "spotanim.windstrike_casting",
                travelSpot = "spotanim.windstrike_travel",
                impactSpot = "spotanim.windstrike_impact",
                impactHeight = 124,
            )

        internal val DAWNBRINGER =
            Spec(
                maxHit = { lvl -> lvl / 3 - 1 },
                castSpot = "spotanim.dawnbringer_casting",
                travelSpot = "spotanim.dawnbringer_projectile",
                impactSpot = "spotanim.dawnbringer_impact",
                impactHeight = 124,
            )

        internal val BLIGHTED_SHADOW =
            Spec(
                maxHit = { lvl -> lvl / 3 + 1 },
                chargeVarobjs = listOf("varobj.tumeken_charges"),
                attackRate = 5,
                castSeq = "seq.toa_sot_cast_b",
                castSpot = "spotanim.tumekens_shadow_casting",
                travelSpot = "spotanim.tumekens_shadow_travel",
                impactSpot = "spotanim.tumekens_shadow_impact",
                impactHeight = 124,
                projanim = "projanim.tumekens_shadow",
                castSound = "synth.toa_shadow_weapon_cast_fire_01",
                hitSound = "synth.contact_darkness_impact",
                dryMessage = "Tumeken's shadow has run out of charges.",
            )
    }
}
