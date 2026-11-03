package org.rsmod.content.skills.agility

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.courseProgress by intVarp("varp.agility_course_progress")

/**
 * Progress counts only consecutive in-order obstacles; anything else restarts the lap. The first
 * obstacle always sets progress to 1 so a fresh lap begins cleanly.
 */
internal fun Player.advanceCourse(index: Int) {
    courseProgress =
        when {
            index == 1 -> 1
            courseProgress == index - 1 -> index
            else -> 0
        }
}

/** Advances to the final obstacle and returns whether the whole lap was completed in order. */
internal fun Player.completeLap(finalIndex: Int): Boolean {
    advanceCourse(finalIndex)
    if (courseProgress != finalIndex) {
        return false
    }
    courseProgress = 0
    return true
}
