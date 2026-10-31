package com.streetspot.app.data.model

import java.util.Locale

object NameNormalizer {
    private val repeatedWhitespace = Regex("\\s+")

    fun displayName(value: String): String = value.trim().replace(repeatedWhitespace, " ")

    fun normalizedName(value: String): String = displayName(value).lowercase(Locale.ROOT)

    fun displayGeneration(value: String?): String? = value
        ?.let(::displayName)
        ?.takeIf(String::isNotEmpty)

    fun normalizedGeneration(value: String?): String = displayGeneration(value)
        ?.lowercase(Locale.ROOT)
        .orEmpty()
}
