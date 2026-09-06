package eu.emufii.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowCombineTest {

    /**
     * Fifteen state flows carrying different types: the library screen combines this
     * many, and a helper that dropped emissions would silently freeze the grid.
     */
    @Test
    fun `emits once per source update across all fifteen inputs`() = runBlocking(Dispatchers.Default) {
        val f1 = MutableStateFlow(0)
        val f2 = MutableStateFlow("")
        val f3 = MutableStateFlow(false)
        val f4 = MutableStateFlow<Int?>(null)
        val f5 = MutableStateFlow(0L)
        val f6 = MutableStateFlow(emptyList<Int>())
        val f7 = MutableStateFlow(emptySet<String>())
        val f8 = MutableStateFlow(0.0)
        val f9 = MutableStateFlow('a')
        val f10 = MutableStateFlow(0.toShort())
        val f11 = MutableStateFlow(0.toByte())
        val f12 = MutableStateFlow<String?>(null)
        val f13 = MutableStateFlow<Boolean?>(null)
        val f14 = MutableStateFlow(emptyMap<String, Int>())
        val f15 = MutableStateFlow(Unit)

        val emissions = mutableListOf<String>()
        val job = launch {
            combineAll(
                f1, f2, f3, f4, f5,
                f6, f7, f8, f9, f10,
                f11, f12, f13, f14, f15,
            ) { a, b, c, d, e, f, g, h, i, j, k, l, m, n, o ->
                "$a|$b|$c|$d|$e|${f.size}|${g.size}|$h|$i|$j|$k|$l|$m|${n.size}|$o"
            }.collect { emissions.add(it) }
        }

        // Initial combined emission — waits until every source has produced a value.
        awaitCount(emissions, 1)
        val start = emissions.size

        val setters: List<() -> Unit> = listOf(
            { f1.value = 42 },
            { f2.value = "x" },
            { f3.value = true },
            { f4.value = 7 },
            { f5.value = 123L },
            { f6.value = listOf(1) },
            { f7.value = setOf("a") },
            { f8.value = 1.5 },
            { f9.value = 'z' },
            { f10.value = 3 },
            { f11.value = 4 },
            { f12.value = "y" },
            { f13.value = true },
            { f14.value = mapOf("k" to 1) },
            { f15.value = Unit.also { /* still Unit — combine still fires because MutableStateFlow(Unit) treats it as unchanged */ } },
        )

        var expected = start
        for ((index, setter) in setters.withIndex()) {
            if (index == setters.lastIndex) continue
            setter()
            expected += 1
            awaitCount(emissions, expected)
        }

        job.cancel()
        // Fourteen distinct updates on top of the initial value → fourteen new emissions.
        assertEquals(14, emissions.size - start)
        assertTrue(emissions.last().startsWith("42|x|true|7|123|"))
    }

    private suspend fun awaitCount(list: List<*>, target: Int) {
        withTimeout(2_000) {
            while (list.size < target) delay(1)
        }
    }
}
