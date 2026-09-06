package eu.emufii.app.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Kotlin's typed `combine` maxes at five flows and the vararg overload forces one type
 * across every source; the library screen combines fifteen heterogeneous flows, so a
 * typed helper takes the place of both. Written as three chained `combine` calls of
 * five so every source update still triggers a re-emission.
 */
private data class Fifth<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
)

@Suppress("LongParameterList")
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> combineAll(
    f1: Flow<T1>,
    f2: Flow<T2>,
    f3: Flow<T3>,
    f4: Flow<T4>,
    f5: Flow<T5>,
    f6: Flow<T6>,
    f7: Flow<T7>,
    f8: Flow<T8>,
    f9: Flow<T9>,
    f10: Flow<T10>,
    f11: Flow<T11>,
    f12: Flow<T12>,
    f13: Flow<T13>,
    f14: Flow<T14>,
    f15: Flow<T15>,
    transform: suspend (
        T1, T2, T3, T4, T5,
        T6, T7, T8, T9, T10,
        T11, T12, T13, T14, T15,
    ) -> R,
): Flow<R> = combine(
    combine(f1, f2, f3, f4, f5, ::Fifth),
    combine(f6, f7, f8, f9, f10, ::Fifth),
    combine(f11, f12, f13, f14, f15, ::Fifth),
) { g1, g2, g3 ->
    transform(
        g1.a, g1.b, g1.c, g1.d, g1.e,
        g2.a, g2.b, g2.c, g2.d, g2.e,
        g3.a, g3.b, g3.c, g3.d, g3.e,
    )
}

private data class Third<A, B, C>(val a: A, val b: B, val c: C)

@Suppress("LongParameterList")
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> combineAll(
    f1: Flow<T1>,
    f2: Flow<T2>,
    f3: Flow<T3>,
    f4: Flow<T4>,
    f5: Flow<T5>,
    f6: Flow<T6>,
    f7: Flow<T7>,
    f8: Flow<T8>,
    f9: Flow<T9>,
    f10: Flow<T10>,
    f11: Flow<T11>,
    f12: Flow<T12>,
    f13: Flow<T13>,
    transform: suspend (
        T1, T2, T3, T4, T5,
        T6, T7, T8, T9, T10,
        T11, T12, T13,
    ) -> R,
): Flow<R> = combine(
    combine(f1, f2, f3, f4, f5, ::Fifth),
    combine(f6, f7, f8, f9, f10, ::Fifth),
    combine(f11, f12, f13, ::Third),
) { g1, g2, g3 ->
    transform(
        g1.a, g1.b, g1.c, g1.d, g1.e,
        g2.a, g2.b, g2.c, g2.d, g2.e,
        g3.a, g3.b, g3.c,
    )
}
