package org.rsmod.api.player.hook

public enum class TeleportType {
    /** Standard spell, tablet, or similar teleport - works up to level 20 Wilderness. */
    Standard,

    /** Member jewellery and items that work up to level 30 Wilderness. */
    MemberLevel30,

    /** Chronicle - cannot be used anywhere in the Wilderness. */
    Chronicle,

    /** Minigame teleports - cannot be used anywhere in the Wilderness. */
    Minigame,

    /**
     * Physical movement such as ladders, stairs, and cave passages - not a teleport, so it is not
     * affected by Tele Block or Wilderness level restrictions.
     */
    Movement,

    /**
     * Bypasses the Wilderness level restriction but is still blocked by Tele Block (e.g. levers,
     * obelisks, admin commands).
     */
    Exempt,
}
