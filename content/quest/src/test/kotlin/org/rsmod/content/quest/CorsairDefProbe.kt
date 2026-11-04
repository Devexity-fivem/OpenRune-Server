package org.rsmod.content.quest

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CorsairDefProbe {

    @Test
    fun dump() {
        val out = StringBuilder()
        for (id in 6071..6076) {
            val vb = ServerCacheManager.getVarbit(id)
            out.append("varbit $id -> ${vb?.let { "varp=${it.varp} bits=${it.startBit}-${it.endBit}" }}\n")
        }
        for (id in listOf(7956, 7957, 7958, 7959, 7960, 7962, 7963, 7964, 7966, 7967, 7968,
                7971, 7972, 7973, 7974, 7975, 7977, 7978, 7981, 7982, 7983, 7993, 7994, 1064, 7980)) {
            val n = ServerCacheManager.getNpc(id)
            out.append(
                "npc $id '${n?.name}' ops=${n?.actions} " +
                    "multiVarp=${n?.multiVarp} multiVarBit=${n?.multiVarBit} " +
                    "multiDefault=${n?.multiDefault} transforms=${n?.transforms?.toList()?.take(70)}\n"
            )
        }
        for (id in listOf(31718, 31719, 31720, 31721, 31722, 31723, 31724, 31632, 11394, 31726)) {
            val l = ServerCacheManager.getObject(id)
            out.append(
                "loc $id '${l?.name}' ops=${l?.actions} " +
                    "multiVarp=${l?.multiVarp} multiVarBit=${l?.multiVarBit} " +
                    "multiDefault=${l?.multiDefault} transforms=${l?.transforms?.toList()?.take(70)}\n"
            )
        }
        println(out)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
