package com.streetspot.app.data.repository

import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCatalogTest {
    @Test
    fun everyCategoryHasAtLeastOneDefaultModel() {
        assertEquals(CarCategory.entries.toSet(), DefaultCatalog.models.map { it.category }.toSet())
    }

    @Test
    fun everyModelReferencesAKnownBrandAndUsesANonBlankIdentity() {
        val brandIds = DefaultCatalog.brands.map { it.id }.toSet()

        assertTrue(DefaultCatalog.models.all { it.brandId in brandIds })
        assertTrue(DefaultCatalog.models.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun defaultCatalogIncludesAllDocumentedRarities() {
        assertEquals(CarRarity.entries.toSet(), DefaultCatalog.models.map { it.rarity }.toSet())
    }
}
