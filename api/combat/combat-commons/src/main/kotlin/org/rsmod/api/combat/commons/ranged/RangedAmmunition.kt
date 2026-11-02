package org.rsmod.api.combat.commons.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.CategoryType
import dev.openrune.util.Wearpos
import kotlin.contracts.contract
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.back
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public object RangedAmmunition {
    /** The default chance for standard ammunition to drop on the ground instead of disappearing. */
    public const val DEFAULT_AMMO_DROP_RATE: Int = 5

    /** The obj spawn duration when an ammunition is dropped on the ground after being fired. */
    public const val DEFAULT_AMMO_DROP_DURATION: Int = 200

    /**
     * The dedicated inventory backing Dizana's quiver's extra ammunition slot, as shipped in the
     * cache. Its contents persist with the player's saved inventories.
     */
    public const val QUIVER_AMMO_INV: String = "inv.dizanas_quiver_ammo"

    /**
     * Verifies that [weapon] can use [ammo] as valid ammunition and sends an appropriate error
     * message to the [player] if it cannot.
     *
     * **Note:** This function only performs validation and messaging. It does **not** remove
     * ammunition or attempt to drop it - use [detractAmmo] and [attemptAmmoDrop] for that behavior.
     *
     * @return `true` if the [ammo] is valid for the given [weapon], or if the [weapon] does not
     *   require specific ammunition
     */
    public fun attemptAmmoUsage(
        player: Player,
        weapon: ItemServerType,
        ammo: ItemServerType?,
    ): Boolean {
        val crossbow = weapon.isCategoryType("category.crossbow")
        if (crossbow) {
            if (ammo == null) {
                player.mes("There is no ammo left in your quiver.")
                return false
            }

            val ammoValidation = validateBolts(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid.IncorrectAmmo -> {
                    player.mes("You can't use that ammo with your crossbow.")
                    false
                }
                is Validation.Invalid.BoneWeaponIncorrectAmmo -> {
                    player.mes("You can't use that ammo with your bone crossbow.")
                    false
                }
                is Validation.Invalid.ExpectedBoneWeapon -> {
                    player.mes("Bone bolts are only usable with a bone crossbow.")
                    false
                }
                is Validation.Invalid.LevelTooHigh -> {
                    player.mes("Your crossbow isn't powerful enough for those bolts.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        val bow = weapon.isCategoryType("category.bow")
        if (bow) {
            if (ammo == null) {
                player.mes("There is no ammo left in your quiver.")
                return false
            }

            val ammoValidation = validateArrows(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid.Ammo -> {
                    player.mes("You can't use that ammo with your bow.")
                    false
                }
                is Validation.Invalid.LevelTooHigh -> {
                    player.mes("Your bow isn't powerful enough for those arrows.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        val ballista = weapon.isCategoryType("category.ballista")
        if (ballista) {
            if (ammo == null) {
                player.mes("There are no javelins in your quiver.")
                return false
            }

            val ammoValidation = validateJavelins(weapon, ammo)
            return when (ammoValidation) {
                is Validation.Invalid -> {
                    player.mes("You can't use that ammo with your ballista.")
                    false
                }
                is Validation.Valid -> true
            }
        }

        return true
    }

    /**
     * The [Wearpos.Back] obj types that provide Dizana's quiver's extra ammunition slot. The max
     * cape variant does not provide the slot; neither do broken or mangled states.
     */
    private val quiverBackTypes: List<String> =
        listOf(
            "obj.dizanas_quiver_charged",
            "obj.dizanas_quiver_charged_trouver",
            "obj.dizanas_quiver_uncharged",
            "obj.dizanas_quiver_uncharged_trouver",
            "obj.dizanas_quiver_infinite",
            "obj.dizanas_quiver_infinite_trouver",
        )

    private val chargedQuiverBackTypes: List<String> =
        listOf(
            "obj.dizanas_quiver_charged",
            "obj.dizanas_quiver_charged_trouver",
            "obj.dizanas_quiver_infinite",
            "obj.dizanas_quiver_infinite_trouver",
        )

    public fun isQuiverCape(obj: InvObj?): Boolean {
        contract { returns(true) implies (obj != null) }
        return obj != null && quiverBackTypes.any { obj.isType(it) }
    }

    public fun isChargedQuiverCape(obj: InvObj?): Boolean {
        contract { returns(true) implies (obj != null) }
        return obj != null && chargedQuiverBackTypes.any { obj.isType(it) }
    }

    /** Ammunition types that the quiver's extra slot accepts: arrows and bolts only. */
    public fun isQuiverAmmoType(type: ItemServerType): Boolean =
        type.isCategoryType("category.arrows") ||
            type.isCategoryType("category.dragon_arrow") ||
            type.isCategoryType("category.arrows_training") ||
            type.isCategoryType("category.ogre_arrows") ||
            type.isCategoryType("category.crossbow_bolt") ||
            type.isCategoryType("category.rack_bolts") ||
            type.isCategoryType("category.kebbit_bolts")

    /**
     * The player-owned inventory holding Dizana's quiver's stored ammunition, creating and
     * transmitting it on first use so the client can render the extra ammo slot.
     */
    public fun quiverAmmoInv(player: Player): Inventory {
        val existing = player.invMap[QUIVER_AMMO_INV]
        if (existing != null) {
            return existing
        }
        val inv = Inventory.create(QUIVER_AMMO_INV)
        inv.owner = player
        player.startInvTransmit(inv)
        return inv
    }

    public fun quiverAmmoInvOrNull(player: Player): Inventory? = player.invMap[QUIVER_AMMO_INV]

    /**
     * The ammo stack stored inside the player's Dizana's quiver slot, if any. Returns the stored
     * ammo's [ItemServerType] and its remaining count, or `null` when no quiver is worn or the
     * slot is empty.
     */
    public fun storedQuiverAmmo(player: Player): Pair<ItemServerType, Int>? {
        if (!isQuiverCape(player.back)) {
            return null
        }
        val obj = quiverAmmoInvOrNull(player)?.get(0) ?: return null
        val type = getOrNull(obj) ?: return null
        return type to obj.count
    }

    /**
     * Resolves which ammunition a ranged attack should use: the worn ammo slot first when its type
     * is valid for [weapon], otherwise the quiver's stored stack when its type is valid. Falls
     * back to the worn slot type so [attemptAmmoUsage] can produce the correct error message.
     */
    public fun resolveAmmo(
        player: Player,
        weapon: ItemServerType,
        wornAmmo: ItemServerType?,
    ): Pair<ItemServerType?, Boolean> {
        val stored = storedQuiverAmmo(player) ?: return wornAmmo to false
        if (wornAmmo != null && validateAmmo(weapon, wornAmmo) is Validation.Valid) {
            return wornAmmo to false
        }
        val (storedType, _) = stored
        if (validateAmmo(weapon, storedType) is Validation.Valid) {
            return storedType to true
        }
        return (wornAmmo ?: storedType) to (wornAmmo == null)
    }

    /** Deducts [detract] ammo from the quiver's stored stack, clearing it when emptied. */
    public fun detractStoredQuiverAmmo(player: Player, detract: Int) {
        val inv = quiverAmmoInvOrNull(player) ?: return
        val obj = inv[0] ?: return
        val remaining = obj.count - detract
        inv[0] = if (remaining > 0) obj.copy(count = remaining) else null
        syncQuiverClient(player)
    }

    /**
     * The ranged-attack/ranged-strength deltas that Dizana's quiver applies for a fired shot:
     * stored ammo substitutes its own stats for the ammo slot's when the stored stack is what
     * would actually be fired, and a sunfire-charged quiver grants the fired arrow or bolt +10
     * ranged accuracy and +1 ranged strength.
     */
    public fun quiverCombatStatDelta(player: Player): Pair<Int, Int> {
        val back = player.back
        val weapon = getOrNull(player.righthand)
        if (!isQuiverCape(back) || weapon == null || !weaponUsesAmmoSlot(weapon)) {
            return 0 to 0
        }
        val worn = getOrNull(player.quiver)
        val stored = storedQuiverAmmo(player)?.first
        val fired =
            when {
                worn != null && validateAmmo(weapon, worn) is Validation.Valid -> worn
                stored != null && validateAmmo(weapon, stored) is Validation.Valid -> stored
                else -> null
            } ?: return 0 to 0
        var attackDelta = 0
        var strengthDelta = 0
        if (fired !== worn) {
            attackDelta += fired.param(params.attack_ranged) - (worn?.param(params.attack_ranged) ?: 0)
            val firedStr = fired.param(params.ranged_strength) + fired.param(params.additive_ranged_strength)
            val wornStr = (worn?.param(params.ranged_strength) ?: 0) + (worn?.param(params.additive_ranged_strength) ?: 0)
            strengthDelta += firedStr - wornStr
        }
        if (isChargedQuiverCape(back) && isQuiverAmmoType(fired)) {
            attackDelta += DIZANA_SUNFIRE_ATTACK
            strengthDelta += DIZANA_SUNFIRE_STRENGTH
        }
        return attackDelta to strengthDelta
    }

    /**
     * Mirrors the stored stack into the client's `dizanas_quiver_temp_ammo` varps so the official
     * worn-tab slot can draw it. No-op if the temp varps are not packed.
     */
    public fun syncQuiverClient(player: Player) {
        val stored = quiverAmmoInvOrNull(player)?.get(0)
        val ammo = RSCM.getRSCMOrNull(TEMP_AMMO_VARP, RSCMType.VARP)
        val amount = RSCM.getRSCMOrNull(TEMP_AMMO_AMOUNT_VARP, RSCMType.VARP)
        if (ammo != null) {
            VarPlayerIntMapSetter.set(player, TEMP_AMMO_VARP, stored?.id ?: 0)
        }
        if (amount != null) {
            VarPlayerIntMapSetter.set(player, TEMP_AMMO_AMOUNT_VARP, stored?.count ?: 0)
        }
    }

    private const val TEMP_AMMO_VARP: String = "varp.dizanas_quiver_temp_ammo"
    private const val TEMP_AMMO_AMOUNT_VARP: String = "varp.dizanas_quiver_temp_ammo_amount"

    private const val DIZANA_SUNFIRE_ATTACK: Int = 10
    private const val DIZANA_SUNFIRE_STRENGTH: Int = 1

    private fun weaponUsesAmmoSlot(weapon: ItemServerType): Boolean =
        weapon.isCategoryType("category.crossbow") ||
            weapon.isCategoryType("category.bow") ||
            weapon.isCategoryType("category.ballista")

    private fun validateAmmo(weapon: ItemServerType, ammo: ItemServerType): Validation =
        when {
            weapon.isCategoryType("category.crossbow") -> validateBolts(weapon, ammo)
            weapon.isCategoryType("category.bow") -> validateArrows(weapon, ammo)
            weapon.isCategoryType("category.ballista") -> validateJavelins(weapon, ammo)
            else -> Validation.Valid
        }

    public fun conserveAmmo(player: Player, random: GameRandom): Boolean {
        val cape = getOrNull(player.back)
        if (cape != null) {
            val recoveryRate = cape.paramOrNull(params.ammo_recovery_rate) ?: return false

            val body = getOrNull(player.torso)
            if (body != null && body.param(params.metallic_interference)) {
                return false
            }

            return recoveryRate > random.of(maxExclusive = 100)
        }
        return false
    }

    public fun detractAmmo(
        player: Player,
        wearpos: Wearpos,
        wornType: ItemServerType,
        detract: Int,
        eventBus: EventBus,
    ) {
        val startObj = player.worn[wearpos.slot]
        check(startObj.isType(wornType)) {
            "Expected worn obj to match `wornType`: wearpos=$wearpos, obj=$startObj, type=$wornType"
        }

        val oldCount = startObj.count
        check(oldCount >= detract) { "Unexpected low worn count: $oldCount (expected=$detract)" }

        player.worn[wearpos.slot] = startObj.copy(count = oldCount - detract)

        val outOfAmmo = oldCount - detract == 0
        if (outOfAmmo) {
            player.worn[wearpos.slot] = null

            // Official behavior: manually unequipping the quiver slot triggers an appearance
            // rebuild, but running out of ammo does not.
            val rebuildAppearance = wearpos == Wearpos.RightHand
            WornUnequipOp.notifyWornUnequip(
                player = player,
                wearpos = wearpos,
                objType = wornType,
                eventBus = eventBus,
                rebuildAppearance = rebuildAppearance,
            )
        }
    }

    public fun attemptAmmoDrop(
        player: Player,
        delay: Int,
        ammoType: ItemServerType,
        ammoCount: Int,
        dropCoord: CoordGrid,
        dropDuration: Int,
        collision: CollisionFlagMap,
        worldQueues: WorldQueueList,
        objRepo: ObjRepository,
    ) {
        // Note: This might not be the "official" behavior - ideally, this check would happen
        // after the `delay` has passed. However, collision flags are unlikely to change in
        // that short time frame, and performing the check here prevents adding unnecessary
        // entries to the world queue. This is a micro-optimization, but practically free
        // and safe.
        if (collision.isWalkBlocked(dropCoord)) {
            return
        }

        val obj = Obj.fromOwner(player, dropCoord, ammoType, ammoCount)
        worldQueues.add(delay) { objRepo.add(obj, dropDuration) }
    }

    public val defualtArrows: CategoryType = CategoryType("category.arrows".asRSCM(RSCMType.CATEGORY))

    public fun validateArrows(weapon: ItemServerType, ammo: ItemServerType): Validation {

        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: defualtArrows

        // Dragon arrows have a separate category from standard arrows, but any bow that accepts
        // regular arrows can also use dragon arrows, provided the `levelrequire` threshold is met.
        val isAlternativeAmmo =
            requiredAmmo.isType("category.arrows") && ammo.isCategoryType("category.dragon_arrow")

        if (!ammo.isCategory(requiredAmmo) && !isAlternativeAmmo) {
            return Validation.Invalid.IncorrectAmmo
        }

        if (ammo.param(params.levelrequire) > weapon.param(params.levelrequire)) {
            return Validation.Invalid.LevelTooHigh
        }

        return Validation.Valid
    }

    public fun validateBolts(weapon: ItemServerType, ammo: ItemServerType): Validation {
        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: CategoryType("category.crossbow_bolt".asRSCM(RSCMType.CATEGORY))

        if (!ammo.isCategory(requiredAmmo)) {
            return if (weapon.param(params.bone_weapon) != 0) {
                Validation.Invalid.BoneWeaponIncorrectAmmo
            } else {
                Validation.Invalid.IncorrectAmmo
            }
        }

        if (ammo.param(params.bone_weapon) != 0 && weapon.param(params.bone_weapon) == 0) {
            return Validation.Invalid.ExpectedBoneWeapon
        }

        if (ammo.param(params.levelrequire) > weapon.param(params.levelrequire)) {
            return Validation.Invalid.LevelTooHigh
        }

        return Validation.Valid
    }

    public fun validateJavelins(weapon: ItemServerType, ammo: ItemServerType): Validation {
        val requiredAmmo = weapon.paramOrNull(params.required_ammo) ?: CategoryType("category.javelin".asRSCM(RSCMType.CATEGORY))
        return if (!ammo.isCategory(requiredAmmo)) {
            Validation.Invalid.IncorrectAmmo
        } else {
            return Validation.Valid
        }
    }

    public sealed class Validation {
        public data object Valid : Validation()

        public sealed class Invalid : Validation() {
            public data object LevelTooHigh : Invalid()

            public sealed class Ammo : Invalid()

            public data object IncorrectAmmo : Ammo()

            public data object BoneWeaponIncorrectAmmo : Ammo()

            public data object ExpectedBoneWeapon : Ammo()
        }
    }
}
