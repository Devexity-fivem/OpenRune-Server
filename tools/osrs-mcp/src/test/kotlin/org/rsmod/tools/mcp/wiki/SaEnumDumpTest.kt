package org.rsmod.tools.mcp.wiki

import dev.openrune.OsrsCacheProvider
import dev.openrune.definition.util.CacheVarLiteral
import dev.openrune.filesystem.Cache
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test

class SaEnumDumpTest {
    @Test
    fun dump() {
        val root = Path.of("").toAbsolutePath()
        var dir = root
        while (!Files.isDirectory(dir.resolve(".data/cache/SERVER"))) {
            dir = dir.parent ?: error("no .data/cache/SERVER found above $root")
        }
        CacheVarLiteral.registerExternal(253, '[', name = "PROJANIM")
        CacheVarLiteral.registerExternal(254, ']', name = "VARBIT")
        val cache = Cache.load(dir.resolve(".data/cache/SERVER"))
        try {
            val provider = OsrsCacheProvider(cache, 241)
            provider.init()
            val enum = provider.enums[906] ?: error("enum 906 missing")
            val keys = enum.values.keys.sorted()
            println("SA_ENUM_906 size=${keys.size}")
            println("SA_ENUM_KEYS=" + keys.joinToString(","))
        } finally {
            cache.close()
        }
    }
}
