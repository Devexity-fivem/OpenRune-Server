package org.rsmod.api.bosses.spec

/** Which npc(s) [Effect.AsNpc] acts as, among the ones the encounter owns. */
sealed interface NpcExpr {
    /** Every owned npc of type [npcType], or every owned npc when null. */
    data class Owned(val npcType: String? = null) : NpcExpr

    /** The owned npc of type [npcType] nearest to [of], if within [radius] tiles of it. */
    data class NearestOwned(
        val npcType: String,
        val of: TargetExpr.Single,
        val radius: Int,
    ) : NpcExpr
}
