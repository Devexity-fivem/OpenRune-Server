package org.rsmod.api.bosses.runtime

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.game.entity.Npc

/** Lifecycle trace for boss encounters, written to `logs/boss-debug.log`. */
internal val bossLogger = InlineLogger("org.rsmod.api.bosses")

internal val Npc.bossTag: String
    get() {
        val form = if (visType.id != type.id) " as ${visType.name}(${visType.id})" else ""
        return "[boss ${type.name}(${type.id})$form #$slotId]"
    }
