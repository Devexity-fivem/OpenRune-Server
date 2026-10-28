package org.rsmod.api.combat.formulas.accuracy

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Scopes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.accuracy.melee.PvPMeleeAccuracy
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.testing.GameTestState

class DefensivePrayerBonusTest {
    @Test
    fun `deadeye lowers attacker hit chance`(state: GameTestState) =
        state.runInjectedGameTest(
            TestDependencies::class,
            TestModule,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            val attacker = registerPlayer()
            val defender = registerPlayer()
            attacker.setBaseLevel("stat.attack", 80)
            attacker.setCurrentLevel("stat.attack", 80)
            defender.setBaseLevel("stat.defence", 80)
            defender.setCurrentLevel("stat.defence", 80)

            fun hitChance() =
                deps.meleeAccuracy.getHitChance(
                    attacker,
                    defender,
                    MeleeAttackType.Slash,
                    MeleeAttackStyle.Accurate,
                    MeleeAttackType.Slash,
                    specialMultiplier = 1.0,
                )

            val baseline = hitChance()

            defender.setVarBit("varbit.prayer_eagleeye", 1)
            defender.setVarBit("varbit.prayer_deadeye_unlocked", 1)
            val withDeadeye = hitChance()
            assertTrue(withDeadeye < baseline) {
                "Deadeye +5% defence should lower attacker hit chance: $withDeadeye !< $baseline"
            }

            defender.setVarBit("varbit.prayer_eagleeye", 0)
            defender.setVarBit("varbit.prayer_hawkeye", 1)
            assertEquals(baseline, hitChance())

            defender.setVarBit("varbit.prayer_hawkeye", 0)
            defender.setVarBit("varbit.prayer_eagleeye", 1)
            defender.setVarBit("varbit.prayer_deadeye_unlocked", 0)
            assertEquals(baseline, hitChance())

            defender.setVarBit("varbit.prayer_eagleeye", 0)
            defender.setVarBit("varbit.prayer_piety", 1)
            val withPiety = hitChance()
            assertTrue(withPiety < withDeadeye) {
                "Piety +25% defence should beat Deadeye +5%: $withPiety !< $withDeadeye"
            }
        }

    private class TestDependencies @Inject constructor(val meleeAccuracy: PvPMeleeAccuracy)

    private object TestModule : AbstractModule() {
        override fun configure() {
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }
}
