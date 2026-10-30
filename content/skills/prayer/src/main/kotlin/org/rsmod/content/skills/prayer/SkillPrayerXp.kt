package org.rsmod.content.skills.prayer

import org.rsmod.api.table.prayer.SkillPrayerRow

/** Bury xp — the table stores tenths so half-xp values (4.5, 5.3, 22.5) survive. */
val SkillPrayerRow.xp: Double
    get() = expTenths / 10.0
