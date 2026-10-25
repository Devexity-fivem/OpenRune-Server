package org.rsmod.content.skills.fletching

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.fletching.FletchingRecipesRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FletchingEvents @Inject constructor(
    private val xpMods: XpModifiers,
) : PluginScript() {

    private val recipes = FletchingRecipesRow.all().map(::FletchingRecipe)
    private val pairOrder = LinkedHashMap<Set<String>, Pair<String, String>>()
    private val recipesByPair =
        LinkedHashMap<Set<String>, MutableList<FletchingRecipe>>().apply {
            for (recipe in recipes) {
                for ((first, second) in recipe.clickPairs()) {
                    val key = setOf(first, second)
                    pairOrder.putIfAbsent(key, first to second)
                    getOrPut(key) { mutableListOf() }.add(recipe)
                }
            }
        }

    override fun ScriptContext.startup() {
        for ((key, products) in recipesByPair) {
            val (first, second) = pairOrder.getValue(key)
            onOpHeldU(first, second) { startFromClick(products) }
        }
        onPlayerQueueWithArgs<FletchTask>("queue.fletching_craft") { processTick(it.args) }
    }

    suspend fun ProtectedAccess.useItemOnItem(first: String, second: String) {
        startFromClick(recipesByPair[setOf(first, second)] ?: return)
    }

    private fun FletchingRecipe.clickPairs(): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val ingredients = inputs.map { it.internal }.distinct()
        if (tool != null && (kind == FletchingKind.CARVE || kind == FletchingKind.CHISEL)) {
            for (equivalent in toolEquivalents(tool)) {
                for (ingredient in ingredients) {
                    pairs += ingredient to equivalent
                }
            }
            return pairs
        }
        for (i in ingredients.indices) {
            for (j in i + 1 until ingredients.size) {
                for (a in inputVariants(ingredients[i])) {
                    for (b in inputVariants(ingredients[j])) {
                        pairs += a to b
                    }
                }
            }
        }
        return pairs
    }

    private fun inputVariants(internal: String): List<String> =
        if (internal == FEATHER) FEATHERS else listOf(internal)

    private fun invCount(inv: Inventory, internal: String): Int =
        if (internal == FEATHER) FEATHERS.sumOf(inv::count) else inv.count(internal)

    private fun ProtectedAccess.hasTool(tool: String): Boolean =
        toolEquivalents(tool).any { inv.contains(it) || it in player.worn }

    private fun ProtectedAccess.invDelIngredient(inv: Inventory, material: Material) {
        if (material.internal != FEATHER) {
            invDel(inv, material.internal, material.count)
            return
        }
        var remaining = material.count
        for (feather in FEATHERS) {
            if (remaining <= 0) return
            val take = minOf(remaining, inv.count(feather))
            if (take > 0) {
                invDel(inv, feather, take)
                remaining -= take
            }
        }
    }

    private fun maxCraftable(inv: Inventory, recipe: FletchingRecipe): Int {
        val perItem = recipe.inputs.minOfOrNull { invCount(inv, it.internal) / it.count } ?: 0
        if (recipe.kind != FletchingKind.ATTACH) return perItem
        // attach rows consume `count` materials per batch of `outputCount`; report items.
        return perItem * recipe.outputCount
    }

    private fun ProtectedAccess.awardXp(recipe: FletchingRecipe, products: Int) {
        val xpTenths =
            if (recipe.kind == FletchingKind.ATTACH) recipe.xp * products else recipe.xp
        if (xpTenths > 0) {
            statAdvance(STAT_FLETCHING, xpTenths / 10.0 * xpMods.get(player, STAT_FLETCHING))
        }
    }

    private suspend fun ProtectedAccess.startFromClick(candidates: List<FletchingRecipe>) {
        val recipeByEntry = candidates.associateBy { recipe ->
            SkillMultiEntry(
                internal = recipe.output,
                materials = recipe.inputs.map { Material(it.internal, it.count) },
            )
        }
        val actionType = when (candidates.first().kind) {
            FletchingKind.CARVE -> SkillingActionType.CARVE
            FletchingKind.STRING -> SkillingActionType.STRING
            FletchingKind.CHISEL -> SkillingActionType.CUT
            else -> SkillingActionType.MAKE
        }
        val verb = when (candidates.first().kind) {
            FletchingKind.STRING -> "string"
            FletchingKind.CHISEL -> "cut"
            else -> "make"
        }
        openSkillMulti(
            SkillMultiConfig(
                actionType = actionType,
                verb = verb,
                entries = recipeByEntry.keys.toList(),
                maxCountProvider = { inv, entry ->
                    recipeByEntry[entry]?.let { maxCraftable(inv, it) } ?: 0
                },
            ),
        ) { selection ->
            val recipe = recipeByEntry[selection.entry] ?: return@openSkillMulti
            startCraft(recipe, selection.amount)
        }
    }

    private fun ProtectedAccess.startCraft(recipe: FletchingRecipe, amount: Int) {
        if (player.fletchingLvl < recipe.level) {
            mes("You need a Fletching level of ${recipe.level} to make this.")
            return
        }
        if (recipe.tool != null && !hasTool(recipe.tool)) {
            mes("You need a ${toolName(recipe.tool)} to do that.")
            return
        }
        if (maxCraftable(inv, recipe) <= 0) return
        anim(recipe.anim)
        val delay = maxOf(recipe.firstTicks, MIN_REQUEUE_TICKS)
        weakQueue("queue.fletching_craft", delay, FletchTask(recipe, amount, 0))
    }

    private fun ProtectedAccess.processTick(task: FletchTask) {
        val recipe = task.recipe
        if (player.fletchingLvl < recipe.level) return
        if (recipe.tool != null && !hasTool(recipe.tool)) return
        if (maxCraftable(inv, recipe) <= 0) return

        val made = applyCraft(inv, recipe, task.remaining)
        if (made <= 0) return

        val remaining = task.remaining - made
        if (remaining > 0 && maxCraftable(inv, recipe) > 0) {
            anim(recipe.anim)
            weakQueue("queue.fletching_craft", maxOf(recipe.ticks, MIN_REQUEUE_TICKS),
                task.copy(remaining = remaining))
        }
    }

    /**
     * Performs one craft. Returns the units deducted from the task's remaining
     * amount: 1 craft for most kinds, or the number of items produced for ATTACH
     * rows whose batch amount is counted in products.
     */
    private fun ProtectedAccess.applyCraft(
        inv: Inventory,
        recipe: FletchingRecipe,
        remaining: Int,
    ): Int {
        if (recipe.kind == FletchingKind.ATTACH) {
            val perBatch = recipe.inputs.minOf { invCount(inv, it.internal) / it.count }
            val toProduce = minOf(remaining, perBatch * recipe.outputCount, recipe.outputCount)
            if (toProduce <= 0) return 0
            recipe.inputs.forEach { mat ->
                invDelIngredient(inv, Material(mat.internal, mat.count * toProduce / recipe.outputCount))
            }
            invAdd(inv, recipe.output, toProduce)
            awardXp(recipe, toProduce)
            return toProduce
        }
        recipe.inputs.forEach { invDelIngredient(inv, it) }
        invAdd(inv, recipe.output, recipe.outputCount)
        awardXp(recipe, 1)
        return 1
    }

    private fun toolName(tool: String): String =
        tool.removePrefix("obj.").replace('_', ' ')

    private data class FletchTask(
        val recipe: FletchingRecipe,
        val remaining: Int,
        val crafted: Int = 0,
    )

    private companion object {
        const val STAT_FLETCHING = "stat.fletching"
        const val FEATHER = "obj.feather"

        val FEATHERS = listOf(
            "obj.feather",
            "obj.hunting_jungle_feather",
            "obj.hunting_polar_feather",
            "obj.hunting_desert_feather",
            "obj.hunting_woodland_feather",
            "obj.hunting_stripy_bird_feather",
            "obj.grim_griffin_feather",
        )

        val TOOL_EQUIVALENTS = mapOf(
            "obj.knife" to listOf("obj.knife", "obj.fletching_knife"),
            "obj.hammer" to listOf(
                "obj.hammer",
                "obj.imcando_hammer",
                "obj.imcando_hammer_offhand",
            ),
        )

        // A queue added mid-processing is decremented on the same tick, so delay 1 re-fires instantly.
        const val MIN_REQUEUE_TICKS = 2

        fun toolEquivalents(tool: String): List<String> = TOOL_EQUIVALENTS[tool] ?: listOf(tool)
    }
}

enum class FletchingKind {
    CARVE, STRING, ASSEMBLE, ATTACH, CHISEL;

    companion object {
        fun byName(value: String): FletchingKind =
            entries.firstOrNull { it.name.lowercase() == value } ?: CARVE
    }
}

class FletchingRecipe(row: FletchingRecipesRow) {
    val kind = FletchingKind.byName(row.kind)
    val level = row.statReq.firstOrNull()?.t1 ?: 1
    val xp = row.xp
    val inputs: List<Material> =
        row.input.mapIndexed { i, obj -> Material(obj.internalName, row.inputAmount.getOrElse(i) { 1 }) }
    val output = row.output.internalName
    val outputCount = row.outputAmount
    val tool = row.tool?.internalName
    val anim = row.anim
    val ticks = row.ticks
    val firstTicks = row.firstTicks ?: ticks
    val featherInput = row.featherInput != null
}
