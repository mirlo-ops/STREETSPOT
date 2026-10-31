package com.streetspot.app.data.repository

import android.content.res.AssetManager
import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.Brand
import com.streetspot.app.data.model.CarCategory
import com.streetspot.app.data.model.CarModel
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.CatalogMetadataCodec
import com.streetspot.app.data.model.NameNormalizer
import com.streetspot.app.data.model.RecordSource
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.UUID
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

class VehiclesDbCatalogSeeder(
    private val database: StreetSpotDatabase,
    private val assets: AssetManager,
) {
    suspend fun seed() {
        val catalog = assets.open(CATALOG_ASSET).use { source ->
            GZIPInputStream(source).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseCatalog(JSONObject(reader.readText()))
            }
        }

        database.withTransaction {
            val brandDao = database.brandDao()
            val modelDao = database.carModelDao()
            if (modelDao.countVehiclesDbModels() == catalog.models.size &&
                brandDao.getAll().count { it.vehiclesDbId != null } == catalog.makes.size
            ) {
                return@withTransaction
            }

            val now = System.currentTimeMillis()
            val existingBrands = brandDao.getAll().toMutableList()
            val brandsToInsert = mutableListOf<Brand>()
            catalog.makes.forEach { make ->
                val normalizedMakeName = matchingName(make.name)
                val normalizedAliases = make.aliases.map { matchingName(it) }.toSet()
                val existing = existingBrands.firstOrNull { it.vehiclesDbId == make.id }
                    ?: existingBrands.firstOrNull { matchingName(it.name) == normalizedMakeName }
                    ?: existingBrands.firstOrNull { brand ->
                        matchingName(brand.name) in normalizedAliases ||
                            CatalogMetadataCodec.decode(brand.aliases)
                                .map { matchingName(it) }
                                .any { it == normalizedMakeName || it in normalizedAliases }
                    }
                if (existing == null) {
                    val brand = Brand(
                        id = stableId("brand", make.id),
                        name = NameNormalizer.displayName(make.name),
                        normalizedName = NameNormalizer.normalizedName(make.name),
                        source = RecordSource.DEFAULT,
                        createdAt = now,
                        vehiclesDbId = make.id,
                        aliases = CatalogMetadataCodec.encode(make.aliases),
                    )
                    brandsToInsert += brand
                    existingBrands += brand
                } else {
                    val updated = existing.copy(
                        vehiclesDbId = make.id,
                        aliases = CatalogMetadataCodec.encode(
                            CatalogMetadataCodec.decode(existing.aliases) + make.aliases +
                                make.name.takeIf { existing.normalizedName != NameNormalizer.normalizedName(make.name) }.orEmpty(),
                        ),
                    )
                    if (updated != existing) brandDao.update(updated)
                    if (updated != existing) {
                        existingBrands[existingBrands.indexOfFirst { it.id == existing.id }] = updated
                    }
                }
            }
            if (brandsToInsert.isNotEmpty()) brandDao.insertAllIgnoringConflict(brandsToInsert)

            val brandsByVehiclesDbId = brandDao.getAll()
                .mapNotNull { brand -> brand.vehiclesDbId?.let { it to brand } }
                .toMap()
            val currentModels = modelDao.getAll()
            val modelsByVehiclesDbId = currentModels.mapNotNull { model ->
                model.vehiclesDbId?.let { it to model }
            }.toMap()
            val modelsByIdentity = currentModels.associateBy {
                modelIdentity(it.brandId, matchingName(it.name), it.normalizedGeneration)
            }
            val modelsToInsert = mutableListOf<CarModel>()
            val modelsToUpdate = mutableListOf<CarModel>()

            catalog.models.forEach { entry ->
                val brand = brandsByVehiclesDbId[entry.makeId]
                    ?: error("VehiclesDB make is missing: ${entry.makeId}")
                val normalizedName = NameNormalizer.normalizedName(entry.name)
                val modelNames = (listOf(entry.name) + entry.aliases).map { matchingName(it) }
                val existing = modelsByVehiclesDbId[entry.id]
                    ?: modelNames.firstNotNullOfOrNull { candidateName ->
                        modelsByIdentity[modelIdentity(brand.id, candidateName, "")]
                    }
                if (existing == null) {
                    modelsToInsert += CarModel(
                        id = stableId("model", entry.id),
                        brandId = brand.id,
                        name = NameNormalizer.displayName(entry.name),
                        normalizedName = normalizedName,
                        generation = null,
                        normalizedGeneration = "",
                        countryCode = null,
                        category = CarCategory.OTHER,
                        rarity = CarRarity.COMMON,
                        source = RecordSource.DEFAULT,
                        createdAt = now,
                        updatedAt = now,
                        vehiclesDbId = entry.id,
                        aliases = CatalogMetadataCodec.encode(entry.aliases),
                        bodyTypes = CatalogMetadataCodec.encode(entry.bodyTypes),
                        globalPopularityDecile = entry.globalPopularityDecile,
                        massPopularityDecile = entry.massPopularityDecile,
                    )
                } else {
                    val updated = existing.copy(
                        vehiclesDbId = entry.id,
                        aliases = CatalogMetadataCodec.encode(
                            CatalogMetadataCodec.decode(existing.aliases) + entry.aliases +
                                entry.name.takeIf { existing.normalizedName != normalizedName }.orEmpty(),
                        ),
                        bodyTypes = CatalogMetadataCodec.encode(entry.bodyTypes),
                        globalPopularityDecile = entry.globalPopularityDecile,
                        massPopularityDecile = entry.massPopularityDecile,
                        updatedAt = now,
                    )
                    if (updated != existing) modelsToUpdate += updated
                }
            }

            if (modelsToInsert.isNotEmpty()) modelDao.insertAllIgnoringConflict(modelsToInsert)
            for (model in modelsToUpdate) modelDao.update(model)
        }
    }

    private fun parseCatalog(root: JSONObject): VehiclesDbCatalog {
        require(root.getString("version") == CATALOG_VERSION) { "Unsupported VehiclesDB catalog version" }
        require(root.getString("kind") == "car") { "Only passenger cars are supported" }
        val makes = root.getJSONArray("makes").parseMakes()
        val models = root.getJSONArray("models").parseModels()
        require(root.getInt("brandCount") == makes.size && root.getInt("modelCount") == models.size) {
            "VehiclesDB catalog counts do not match its manifest"
        }
        require(makes.map(VehiclesDbMake::id).toSet().size == makes.size) { "Duplicate VehiclesDB make IDs" }
        require(makes.map { matchingName(it.name) }.toSet().size == makes.size) {
            "VehiclesDB contains duplicate make names"
        }
        require(models.map(VehiclesDbModel::id).toSet().size == models.size) { "Duplicate VehiclesDB model IDs" }
        val makeIds = makes.mapTo(hashSetOf(), VehiclesDbMake::id)
        require(models.all { it.makeId in makeIds }) { "VehiclesDB model refers to an unknown make" }
        require(models.map { "${it.makeId}\u0000${matchingName(it.name)}\u0000" }.toSet().size == models.size) {
            "VehiclesDB contains duplicate model identities"
        }
        return VehiclesDbCatalog(makes, models)
    }

    private fun JSONArray.parseMakes(): List<VehiclesDbMake> = List(length()) { index ->
        val make = getJSONObject(index)
        VehiclesDbMake(
            id = make.getString("id"),
            name = make.getString("name"),
            aliases = make.getJSONArray("aliases").parseStringList(),
        ).also { require(it.id.isNotBlank() && it.name.isNotBlank()) }
    }

    private fun JSONArray.parseModels(): List<VehiclesDbModel> = List(length()) { index ->
        val model = getJSONObject(index)
        VehiclesDbModel(
            id = model.getString("id"),
            makeId = model.getString("makeId"),
            name = model.getString("name"),
            aliases = model.getJSONArray("aliases").parseStringList(),
            bodyTypes = model.getJSONArray("bodyTypes").parseStringList(),
            globalPopularityDecile = model.nullableDecile("globalPopularityDecile"),
            massPopularityDecile = model.nullableDecile("massPopularityDecile"),
        ).also { require(it.id.isNotBlank() && it.makeId.isNotBlank() && it.name.isNotBlank()) }
    }

    private fun JSONObject.nullableDecile(key: String): Int? =
        if (isNull(key)) null else getInt(key).also { require(it in 1..10) }

    private fun JSONArray.parseStringList(): List<String> = List(length()) { index ->
        getString(index).also { require(it.isNotBlank()) }
    }

    private fun stableId(type: String, sourceId: String): String = UUID.nameUUIDFromBytes(
        "vehiclesdb:$type:$sourceId".toByteArray(StandardCharsets.UTF_8),
    ).toString()

    private fun modelIdentity(brandId: String, normalizedName: String, normalizedGeneration: String): String =
        "$brandId\u0000$normalizedName\u0000$normalizedGeneration"

    private fun matchingName(value: String): String = Normalizer
        .normalize(NameNormalizer.normalizedName(value), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private data class VehiclesDbCatalog(
        val makes: List<VehiclesDbMake>,
        val models: List<VehiclesDbModel>,
    )

    private data class VehiclesDbMake(
        val id: String,
        val name: String,
        val aliases: List<String>,
    )

    private data class VehiclesDbModel(
        val id: String,
        val makeId: String,
        val name: String,
        val aliases: List<String>,
        val bodyTypes: List<String>,
        val globalPopularityDecile: Int?,
        val massPopularityDecile: Int?,
    )

    private companion object {
        const val CATALOG_VERSION = "2026.10.4"
        const val CATALOG_ASSET = "vehiclesdb/catalog-v2026.10.4.json.gz"
    }
}
