package tv.own.owntv.features.sports

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** A retained view model is not evidence its screen is visible. Cancel its polling when covered. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> StateFlow<Boolean>.whileForeground(inactive: () -> T, work: () -> Flow<T>): Flow<T> =
    flatMapLatest { active -> if (active) work() else flowOf(inactive()) }
