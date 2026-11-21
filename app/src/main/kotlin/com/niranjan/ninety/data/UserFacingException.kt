package com.niranjan.ninety.data

/** A message written for the user with no implementation or provider details. */
open class UserFacingException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

internal inline fun requireUserRule(condition: Boolean, message: () -> String) {
    if (!condition) throw UserFacingException(message())
}
