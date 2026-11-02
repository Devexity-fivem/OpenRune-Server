package dev.openrune.tools

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcConfigBlockIdentityTest {
    @Test
    fun `every npc source block has an identity before its nested params`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val source = File(root, ".data/raw-cache/server/npcs.toml").readLines()
        val starts = source.indices.filter { source[it].trim() == "[[npc]]" }
        val missing = starts.mapIndexedNotNull { index, start ->
            val end = starts.getOrElse(index + 1) { source.size }
            val fields = source.subList(start + 1, end)
                .map(String::trim)
                .takeWhile { !it.startsWith("[") }
            if (fields.any { ID_FIELD.containsMatchIn(it) }) null else start + 1
        }
        assertTrue(missing.isEmpty(), "Anonymous NPC source blocks at lines: $missing")
    }

    private companion object {
        val ID_FIELD = Regex("^id\\s*=")
    }
}
