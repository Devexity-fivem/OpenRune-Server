package org.rsmod.content.raids.tfa.raid.reward

/**
 * A run's unique and who may take it. It leaves here at most once, either through the chest's
 * opening animation ([beginReveal] then [finishReveal]) or, if its owner leaves first, [settle].
 */
internal class TfaUniqueClaim(private val winner: Long?, val obj: String?) {
    private enum class State {
        Unclaimed,
        Opening,
        Claimed,
    }

    private var state = State.Unclaimed

    val hasUnique: Boolean
        get() = obj != null

    fun isWinner(key: Long?): Boolean = winner != null && winner == key

    /** Claims the unique for the opening animation; null if there is none or it was taken. */
    fun beginReveal(): String? {
        if (obj == null || state != State.Unclaimed) return null
        state = State.Opening
        return obj
    }

    /** The unique to hand over once the animation is done; null unless it was being opened. */
    fun finishReveal(): String? {
        if (state != State.Opening) return null
        state = State.Claimed
        return obj
    }

    /** The unique to hand over when its owner leaves with it unopened; null otherwise. */
    fun settle(key: Long?): String? {
        if (!isWinner(key) || state != State.Unclaimed) return null
        state = State.Claimed
        return obj
    }
}
