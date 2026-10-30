package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionContext
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Lowerniel Drakan's afterimage burst: he scatters short-lived vampyre
// silhouettes around the target. npc.myq6_lowerniel_hurt is used for the
// decoys — it's not part of his boss spec, so the copies can't register their
// own encounters.
class DrakanExtensions @Inject constructor(private val deps: BossDeps) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("drakan.afterimages", ::afterimages)
    }

    private fun afterimages(ctx: BossExtensionContext) {
        val player = ctx.target
        repeat(AFTERIMAGE_COUNT) {
            val tile =
                player.coords.translate(
                    deps.random.of(AFTERIMAGE_RADIUS * 2 + 1) - AFTERIMAGE_RADIUS,
                    deps.random.of(AFTERIMAGE_RADIUS * 2 + 1) - AFTERIMAGE_RADIUS,
                )
            val image = Npc("npc.myq6_lowerniel_hurt", tile)
            deps.npcRepo.add(image, AFTERIMAGE_TICKS)
        }
    }

    private companion object {
        private const val AFTERIMAGE_COUNT = 4
        private const val AFTERIMAGE_RADIUS = 3
        private const val AFTERIMAGE_TICKS = 8
    }
}
