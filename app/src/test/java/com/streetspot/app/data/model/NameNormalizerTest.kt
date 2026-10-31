package com.streetspot.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizerTest {
    @Test
    fun normalizesCaseAndRepeatedWhitespaceWithoutChangingDisplayName() {
        val displayName = NameNormalizer.displayName("  Mercedes-Benz   300   SL ")

        assertEquals("Mercedes-Benz 300 SL", displayName)
        assertEquals("mercedes-benz 300 sl", NameNormalizer.normalizedName(displayName))
    }

    @Test
    fun emptyGenerationUsesAnEmptyUniqueKey() {
        assertEquals(null, NameNormalizer.displayGeneration("   "))
        assertEquals("", NameNormalizer.normalizedGeneration(null))
        assertEquals("mk viii", NameNormalizer.normalizedGeneration(" MK   VIII "))
    }
}
