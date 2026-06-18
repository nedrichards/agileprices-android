package com.nedrichards.agileprices

import kotlinx.coroutines.CancellationException

internal suspend fun <T> runCatchingPreservingCancellation(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
