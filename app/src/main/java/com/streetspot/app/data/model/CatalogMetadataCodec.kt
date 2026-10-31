package com.streetspot.app.data.model

object CatalogMetadataCodec {
    private const val SEPARATOR = '\u001f'

    fun encode(values: Iterable<String>): String? {
        val distinctValues = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return distinctValues.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR.toString())
    }

    fun decode(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
