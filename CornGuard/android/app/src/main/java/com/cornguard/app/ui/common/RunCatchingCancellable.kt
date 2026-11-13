package com.cornguard.app.ui.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for coroutines: rethrows [CancellationException] instead of turning it into a
 * failure. Plain `runCatching` around a suspend call swallows the cancellation that fires when a
 * fragment's view is destroyed, so the code after it keeps running and touches a null binding.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
