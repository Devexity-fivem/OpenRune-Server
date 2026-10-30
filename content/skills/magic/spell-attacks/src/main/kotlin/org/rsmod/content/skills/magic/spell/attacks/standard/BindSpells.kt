package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.api.spells.attack.SpellCastFx
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.getOrNull

internal enum class BindSpell(
    val obj: String,
    val maxHit: Int,
    val bindTicks: Int,
    val impact: String,
    val castSound: String,
    val hitSound: String?,
    val hitXpBonus: Double,
    val splashXpDelta: Double,
) {
    Bind(
        obj = "obj.20_bind",
        maxHit = 0,
        bindTicks = 8,
        impact = "spotanim.bind_impact",
        castSound = "synth.bind_cast",
        hitSound = "synth.bind_impact",
        hitXpBonus = 1.0,
        splashXpDelta = 0.0,
    ),
    Snare(
        obj = "obj.50_snare",
        maxHit = 2,
        bindTicks = 16,
        impact = "spotanim.snare_impact",
        castSound = "synth.snare_all",
        hitSound = null,
        hitXpBonus = 1.0,
        splashXpDelta = 0.0,
    ),
    Entangle(
        obj = "obj.79_entangle",
        maxHit = 5,
        bindTicks = 24,
        impact = "spotanim.entangle_impact",
        castSound = "synth.entangle_cast_and_fire",
        hitSound = "synth.entangle_hit",
        hitXpBonus = 0.0,
        splashXpDelta = -19.0,
    ),
}

internal class BindSpellEffects(private val random: GameRandom) {
    fun bindTicks(spell: BindSpell, caster: Player): Int =
        spell.bindTicks + swampbarkBonus(caster)

    fun swampbarkBonus(caster: Player): Int =
        SWAMPBARK_PIECES.count { caster.worn.count(it) > 0 } * SWAMPBARK_BONUS_TICKS

    fun bind(target: PathingEntity, ticks: Int): Boolean =
        when (target) {
            is Npc -> {
                val resistance = target.visType.paramOrNull(params.freeze_resistance) ?: 0
                val resisted = resistance > 0 && random.of(100) < resistance
                !resisted && CombatEffects.freeze(target, ticks)
            }
            is Player -> CombatEffects.freeze(target, ticks)
        }

    private companion object {
        private const val SWAMPBARK_BONUS_TICKS = 2
        private val SWAMPBARK_PIECES =
            listOf("obj.swampbark_helm", "obj.swampbark_body", "obj.swampbark_legs")
    }
}

class BindSpells
@Inject
constructor(private val random: GameRandom, private val worldQueues: WorldQueueList) :
    SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        val effects = BindSpellEffects(random)
        for (spell in BindSpell.entries) {
            register(spell.obj, BindSpellAttack(manager, spell, effects))
        }
    }

    private inner class BindSpellAttack(
        private val manager: SpellAttackManager,
        private val spell: BindSpell,
        private val effects: BindSpellEffects,
    ) : SpellAttack, SpellCastFx {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override fun ProtectedAccess.playCast(
            target: PathingEntity,
            weapon: ItemServerType?,
        ): ProjAnim {
            player.anim(weapon.castAnim(), priority = 6)
            spotanim(LAUNCH_SPOTANIM, height = 92)
            return manager.spawnProjectile(this, target, TRAVEL_SPOTANIM, PROJANIM)
        }

        override fun ProtectedAccess.playImpact(target: PathingEntity, clientDelay: Int) {
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = spell.castSound,
                soundRadius = 8,
                hitSpot = spell.impact,
                hitSpotHeight = 0,
                hitSound = spell.hitSound,
            )
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }

            val proj = playCast(target, getOrNull(attack.weapon))
            val (serverDelay, clientDelay) = proj.durations
            val spellObj = attack.spell.obj

            if (manager.rollSplash(this, target, attack, castResult)) {
                applySplashXpDelta()
                manager.playSplashFx(this, target, clientDelay, spell.castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spellObj, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val damage = manager.rollMaxHit(this, target, attack, castResult, spell.maxHit)
            playImpact(target, clientDelay)
            if (spell.hitXpBonus != 0.0) {
                statAdvance("stat.magic", spell.hitXpBonus)
            }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spellObj, damage, clientDelay, serverDelay)
            queueBind(target, serverDelay)
            manager.continueCombatIfAutocast(this, target)
        }

        // Cache castxp is the splash amount for Bind/Snare but the hit amount
        // for Entangle, so only the Entangle splash needs a corrective delta.
        @OptIn(InternalApi::class)
        private fun ProtectedAccess.applySplashXpDelta() {
            val delta = spell.splashXpDelta
            if (delta == 0.0) {
                return
            }
            if (delta > 0) {
                statAdvance("stat.magic", delta)
                return
            }
            val stat = "stat.magic"
            val scaled = PlayerStatMap.toFineXP(delta * player.xpRate * player.globalXpRate)
            val xp = player.statMap.getFineXP(stat)
            player.statMap.setFineXP(stat, maxOf(0, xp + scaled.toInt()))
            player.markStatUpdate(stat)
        }

        private fun ProtectedAccess.queueBind(target: PathingEntity, serverDelay: Int) {
            val ticks = effects.bindTicks(spell, player)
            val caster = player
            val stillValid = target.validityCheck()
            worldQueues.add(serverDelay) {
                if (!stillValid() || !caster.isSlotAssigned) {
                    return@add
                }
                if (effects.bind(target, ticks) && target is Player) {
                    queueFadeWarning(target, ticks)
                }
            }
        }

        private fun queueFadeWarning(target: Player, ticks: Int) {
            val delay = ticks - FADE_WARNING_TICKS
            if (delay <= 0) {
                return
            }
            worldQueues.add(delay) {
                if (target.isValidTarget() && target.isFrozen) {
                    target.mes(FADE_WARNING_MESSAGE, ChatType.Spam)
                }
            }
        }

        private fun ItemServerType?.castAnim(): String =
            if (this != null && isCategoryType("category.staff")) {
                STAFF_CAST_ANIM
            } else {
                CAST_ANIM
            }

        private fun PathingEntity.validityCheck(): () -> Boolean =
            when (this) {
                is Npc -> {
                    val castUid = uid
                    val check = { uid == castUid && hitpoints > 0 }
                    check
                }
                is Player -> ::isValidTarget
            }
    }

    private companion object {
        private const val CAST_ANIM = "seq.human_castentangle"
        private const val STAFF_CAST_ANIM = "seq.human_castentangle_staff"
        private const val LAUNCH_SPOTANIM = "spotanim.entangle_casting"
        private const val TRAVEL_SPOTANIM = "spotanim.entangle_travel"
        private const val PROJANIM = "projanim.bind"
        private const val FADE_WARNING_TICKS = 3
        private const val FADE_WARNING_MESSAGE = "The holding effect is beginning to fade."
    }
}
