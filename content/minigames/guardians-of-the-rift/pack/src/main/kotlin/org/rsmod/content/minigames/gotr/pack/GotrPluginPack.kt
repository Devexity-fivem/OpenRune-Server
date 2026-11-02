package org.rsmod.content.minigames.gotr.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class GotrPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(GotrRewardsTable.rewards())
}
