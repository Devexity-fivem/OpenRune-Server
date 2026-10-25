package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

// Marks of Grace spawn mechanics per OSRS Wiki / Mod statements:
// - A roll happens on each completed course lap, gated by a 3-minute cooldown that is indexed to
//   whole game minutes and persists across logout (hence a Perm varp).
// - If the player's unboosted level is >= 20 over the course requirement the chance is 80% lower.
// - Marks are player-specific, stack on the same tile (refreshing the timer), and despawn after
//   10 minutes. Base roll is 2/6 (the documented rate for courses without a special rate; the
//   Gnome Stronghold figure is unpublished, and 2/6 reproduces the wiki's 10-12 marks/hour band
//   at ~40s laps). Gnome has a single spawn tile: the platform before the branch climb-down.
@Singleton
class MarkOfGrace @Inject constructor(
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) {
    fun onLapCompleted(
        player: Player,
        spawn: CoordGrid,
        levelRequirement: Int,
        denominator: Int = BASE_DENOMINATOR,
    ): Boolean {
        if (player.observerUUID == null) {
            return false
        }
        val nowMinute = player.currentMapClock / TICKS_PER_MINUTE
        if (nowMinute < player.markCooldownMinute) {
            return false
        }
        var roll = denominator
        if (player.baseAgilityLvl >= levelRequirement + LEVEL_PENALTY_LEVELS) {
            roll *= LEVEL_PENALTY_DIVISOR
        }
        if (random.of(roll) >= MARK_NUMERATOR) {
            return false
        }
        objRepo.add(
            MARK_OBJ,
            spawn,
            duration = DESPAWN_TICKS,
            receiver = player,
            reveal = DESPAWN_TICKS,
        )
        player.markCooldownMinute = nowMinute + COOLDOWN_MINUTES
        return true
    }

    private companion object {
        const val MARK_OBJ = "obj.grace"
        const val MARK_NUMERATOR = 2
        const val BASE_DENOMINATOR = 6
        const val LEVEL_PENALTY_LEVELS = 20
        const val LEVEL_PENALTY_DIVISOR = 5

        const val TICKS_PER_MINUTE = 100
        const val COOLDOWN_MINUTES = 3
        const val DESPAWN_TICKS = 10 * TICKS_PER_MINUTE
    }
}

private var Player.markCooldownMinute by intVarp("varp.agility_mark_last_minute")
