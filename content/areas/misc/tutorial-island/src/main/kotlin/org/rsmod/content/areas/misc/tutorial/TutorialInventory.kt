package org.rsmod.content.areas.misc.tutorial

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal object TutorialInventory {
    fun supply(player: Player, vararg items: InvObj): Boolean {
        val missing = items.mapNotNull { item ->
            val held = (player.inv.asSequence() + player.worn.asSequence())
                .filterNotNull()
                .filter { it.id == item.id }
                .sumOf { it.count.toLong() }
            val count = (item.count.toLong() - held).coerceAtLeast(0).toInt()
            if (count == 0) null else item.copy(count = count)
        }
        return exchange(player, emptyList(), missing)
    }

    fun exchange(player: Player, inputs: List<InvObj>, outputs: List<InvObj>): Boolean =
        player.invTransaction(player.inv) {
            val backpack = select(player.inv)
            for (item in inputs) {
                delete {
                    from = backpack
                    obj = item.id
                    strictCount = item.count
                }
            }
            for (item in outputs) {
                insert {
                    into = backpack
                    obj = item.id
                    strictCount = item.count
                    vars = item.vars
                }
            }
        }.success
}
