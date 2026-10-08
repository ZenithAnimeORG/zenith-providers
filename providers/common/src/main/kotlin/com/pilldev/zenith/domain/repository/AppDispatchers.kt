package com.pilldev.zenith.domain.repository

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Interface representing the application's Coroutine dispatchers.
 */
interface AppDispatchers {
    /** Dispatcher for main thread operations. */
    val main: CoroutineDispatcher

    /** Dispatcher for I/O operations. */
    val io: CoroutineDispatcher

    /** Dispatcher for CPU-intensive operations. */
    val default: CoroutineDispatcher

    /** Unconfined dispatcher. */
    val unconfined: CoroutineDispatcher
}
