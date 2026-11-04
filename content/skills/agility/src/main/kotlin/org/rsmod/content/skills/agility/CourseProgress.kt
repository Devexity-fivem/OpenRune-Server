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

internal var Player.wildernessCourseProgress by intVarp("varp.wilderness_course_progress")
internal var Player.wildernessTagPending by intVarp("varp.wilderness_agility_tag_pending")
internal var Player.wildernessLootPaid by intVarp("varp.wilderness_agility_loot")

/**
 * Wilderness course progress is forgiving: any obstacle that is next-in-sequence or earlier
 * moves progress to its index, so failed obstacles can be retried freely, while an
 * out-of-sequence obstacle drags progress back to it. Returns true once the final index is
 * reached, resetting progress for the next lap.
 */
internal fun Player.advanceWilderness(index: Int): Boolean {
    if (wildernessCourseProgress + 1 >= index) {
        wildernessCourseProgress = index
    }
    if (wildernessCourseProgress != WILDERNESS_FINAL_INDEX) {
        return false
    }
    wildernessCourseProgress = 0
    return true
}

private const val WILDERNESS_FINAL_INDEX = 5
