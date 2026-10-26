package org.rsmod.content.areas.misc.tutorial

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal object TutorialIsland {
    const val VARP_STAGE = "varp.tutorial"
    const val VARP_PROGRESS = "varp.tutorial_progress_overlay"

    const val DONE = 1000

    const val FRESH = 0
    const val GUIDE_DONE = 10
    const val BRYNNA_NET = 20
    const val SHRIMP_CAUGHT = 30
    const val AXE_TINDER = 40
    const val LOGS = 50
    const val FIRE_LIT = 60
    const val SHRIMP_COOKED = 70
    const val LEV_FLOUR = 80
    const val DOUGH = 90
    const val BREAD = 100
    const val QUEST_DONE = 110
    const val DEZZICK_PICK = 120
    const val ORES = 130
    const val BAR = 140
    const val HAMMER = 141
    const val DAGGER = 150
    const val COMBAT_READY = 155
    const val MELEE_KILL = 160
    const val BOW_ARROWS = 170
    const val RANGED_KILL = 180
    const val BANK_DONE = 190
    const val ACCOUNT_DONE = 200
    const val BRACE_DONE = 210
    const val PAUL_DONE = 220
    const val RUNES = 230
    const val CHICKEN_KILL = 240

    const val MAX_STAGE = CHICKEN_KILL

    val START = CoordGrid(3094, 3107, 0)
    val MAINLAND = CoordGrid(3233, 3230, 0)
    const val UNDERGROUND_Z_OFFSET = 6400

    const val TUTORIAL_END_CLIENTSCRIPT = "clientscript.[clientscript,tutorial_end]"

    const val HINT_CLIENTSCRIPT = "clientscript.[clientscript,tutorial_overlay_hint]"

    fun overlayHintId(stage: Int): Int =
        when (stage) {
            GUIDE_DONE -> 3
            BRYNNA_NET -> 7
            AXE_TINDER -> 9
            FIRE_LIT -> 5
            LEV_FLOUR -> 10
            DEZZICK_PICK -> 12
            BAR -> 13
            DAGGER -> 14
            BOW_ARROWS -> 16
            BRACE_DONE -> 17
            RUNES -> 18
            CHICKEN_KILL -> 23
            else -> 0
        }

    val DOOR_STAGES =
        linkedMapOf(
            "loc.newbie_door1" to GUIDE_DONE,
            "loc.newbiegateclosedl2" to SHRIMP_COOKED,
            "loc.newbiegateclosedr2" to SHRIMP_COOKED,
            "loc.newbie_door2" to SHRIMP_COOKED,
            "loc.newbie_door3" to BREAD,
            "loc.newbie_door4" to BREAD,
            "loc.newbiedoor4l" to BREAD,
            "loc.newbiedoor4r" to BREAD,
            "loc.newbiedoor5_l" to COMBAT_READY,
            "loc.newbiedoor5_r" to COMBAT_READY,
            "loc.newbie_door6" to RANGED_KILL,
            "loc.newbie_door7" to ACCOUNT_DONE,
            "loc.newbie_door8" to BRACE_DONE,
            "loc.newbie_door9" to BRACE_DONE,
        )

    val DOOR_PAIRS =
        mapOf(
            "loc.newbiegateclosedl2" to Pair("loc.newbiegateclosedr2", true),
            "loc.newbiegateclosedr2" to Pair("loc.newbiegateclosedl2", false),
            "loc.newbiedoor4l" to Pair("loc.newbiedoor4r", true),
            "loc.newbiedoor4r" to Pair("loc.newbiedoor4l", false),
            "loc.newbiedoor5_l" to Pair("loc.newbiedoor5_r", true),
            "loc.newbiedoor5_r" to Pair("loc.newbiedoor5_l", false),
        )

    fun doorSound(symbol: String): String =
        if (DOOR_PAIRS.containsKey(symbol)) "synth.picketgate_open" else "synth.door_open"

    const val DOOR_OPEN_TICKS = 12

    const val XP_CAP_LEVEL = 3

    fun progressFor(stage: Int): Int = (stage * 16384 / MAX_STAGE).coerceIn(0, 16384)
}

internal fun CoordGrid.onTutorialIsland(): Boolean =
    (x in 3072..3170 && z in 3072..3160) || (x in 3072..3170 && z in 9472..9540)

internal var Player.tutorialStage: Int
    get() = vars[TutorialIsland.VARP_STAGE]
    set(value) {
        VarPlayerIntMapSetter.set(this, TutorialIsland.VARP_STAGE, value)
        VarPlayerIntMapSetter.set(
            this,
            TutorialIsland.VARP_PROGRESS,
            TutorialIsland.progressFor(value),
        )
        tutorialHint(value)
    }

internal fun Player.tutorialHint(stage: Int) {
    val hint = TutorialIsland.overlayHintId(stage)
    if (hint > 0) {
        runClientScript(
            TutorialIsland.HINT_CLIENTSCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
            hint,
        )
    }
}
