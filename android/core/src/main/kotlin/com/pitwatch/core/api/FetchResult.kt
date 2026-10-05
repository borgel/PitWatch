package com.pitwatch.core.api

sealed interface FetchResult<out T> {
    data class Data<T>(val value: T, val lastModified: String?) : FetchResult<T>
    data object NotModified : FetchResult<Nothing>
}

class TbaException(val statusCode: Int, message: String) : Exception(message)
