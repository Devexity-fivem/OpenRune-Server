package org.rsmod.content.other.special.attacks

import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.content.other.special.attacks.boost.StatBoostSpecialAttacks
import org.rsmod.content.other.special.attacks.melee.AbyssalSpecialAttacks
import org.rsmod.content.other.special.attacks.melee.DragonLongswordSpecialAttack
import org.rsmod.content.other.special.attacks.melee.DragonWeaponSpecialAttacks
import org.rsmod.content.other.special.attacks.melee.GodswordSpecialAttacks
import org.rsmod.content.other.special.attacks.melee.HalberdSpecialAttack
import org.rsmod.content.other.special.attacks.melee.MiscMeleeSpecialAttacks
import org.rsmod.content.other.special.attacks.ranged.BowSpecialAttacks
import org.rsmod.content.other.special.attacks.ranged.DarkBowSpecialAttack
import org.rsmod.plugin.module.PluginModule

class SpecialAttackModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(StatBoostSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(DarkBowSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(DragonLongswordSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(HalberdSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(AbyssalSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(DragonWeaponSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(GodswordSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(MiscMeleeSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(BowSpecialAttacks::class.java)
    }
}
