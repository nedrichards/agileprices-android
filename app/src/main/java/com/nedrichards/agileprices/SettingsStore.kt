package com.nedrichards.agileprices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.agilePreferences: DataStore<Preferences> by preferencesDataStore(name = "agile_prices")

interface SettingsDataSource {
    val settings: Flow<AgileSettings>

    suspend fun saveSelectedTariff(regionCode: String, tariffCode: String)

    suspend fun saveLoadDurationMinutes(value: Int)

    suspend fun saveSearchHorizonMinutes(value: Int)

    suspend fun saveCache(prices: List<PriceWindow>, fetchedAt: Instant, message: String?)

    suspend fun saveRefreshMessage(message: String)
}

class SettingsStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SettingsDataSource {
    constructor(
        context: Context,
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(context.agilePreferences, json)

    override val settings: Flow<AgileSettings> = dataStore.data.map { preferences ->
        AgileSettings(
            selectedRegionCode = preferences[Keys.selectedRegionCode],
            selectedTariffCode = preferences[Keys.selectedTariffCode],
            loadDurationMinutes = preferences[Keys.loadDurationMinutes] ?: defaultLoadDurationMinutes,
            searchHorizonMinutes = preferences[Keys.searchHorizonMinutes] ?: defaultSearchHorizonMinutes,
            cachedPrices = decodePrices(preferences[Keys.cachedPrices]),
            fetchedAt = preferences[Keys.fetchedAt]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            lastRefreshMessage = preferences[Keys.lastRefreshMessage],
        )
    }

    override suspend fun saveSelectedTariff(regionCode: String, tariffCode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.selectedRegionCode] = regionCode
            preferences[Keys.selectedTariffCode] = tariffCode
            preferences.remove(Keys.cachedPrices)
            preferences.remove(Keys.fetchedAt)
            preferences.remove(Keys.lastRefreshMessage)
        }
    }

    override suspend fun saveLoadDurationMinutes(value: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.loadDurationMinutes] = value.coerceIn(30, 480)
        }
    }

    override suspend fun saveSearchHorizonMinutes(value: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.searchHorizonMinutes] = value.coerceIn(60, 1440)
        }
    }

    override suspend fun saveCache(prices: List<PriceWindow>, fetchedAt: Instant, message: String?) {
        dataStore.edit { preferences ->
            preferences[Keys.cachedPrices] = encodePrices(prices.sortedByValidFrom())
            preferences[Keys.fetchedAt] = fetchedAt.toString()
            if (message == null) {
                preferences.remove(Keys.lastRefreshMessage)
            } else {
                preferences[Keys.lastRefreshMessage] = message
            }
        }
    }

    override suspend fun saveRefreshMessage(message: String) {
        dataStore.edit { preferences ->
            preferences[Keys.lastRefreshMessage] = message
        }
    }

    private fun encodePrices(prices: List<PriceWindow>): String =
        json.encodeToString(
            ListSerializer(CachedPriceWindow.serializer()),
            prices.map { CachedPriceWindow.from(it) },
        )

    private fun decodePrices(raw: String?): List<PriceWindow> =
        runCatching {
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                json.decodeFromString(ListSerializer(CachedPriceWindow.serializer()), raw)
                    .mapNotNull { it.toPriceWindowOrNull() }
                    .sortedByValidFrom()
            }
        }.getOrDefault(emptyList())

    private object Keys {
        val selectedRegionCode = stringPreferencesKey("selectedRegionCode")
        val selectedTariffCode = stringPreferencesKey("selectedTariffCode")
        val loadDurationMinutes = intPreferencesKey("loadDurationMinutes")
        val searchHorizonMinutes = intPreferencesKey("searchHorizonMinutes")
        val cachedPrices = stringPreferencesKey("cachedPriceWindows")
        val fetchedAt = stringPreferencesKey("fetchedAt")
        val lastRefreshMessage = stringPreferencesKey("lastRefreshMessage")
    }

    companion object {
        const val defaultLoadDurationMinutes = 60
        const val defaultSearchHorizonMinutes = 480
    }
}

@Serializable
private data class CachedPriceWindow(
    @SerialName("validFrom")
    val validFrom: String,
    @SerialName("validTo")
    val validTo: String,
    @SerialName("pricePencePerKwh")
    val pricePencePerKwh: Double,
) {
    fun toPriceWindowOrNull(): PriceWindow? =
        runCatching {
            PriceWindow(
                validFrom = Instant.parse(validFrom),
                validTo = Instant.parse(validTo),
                pricePencePerKwh = pricePencePerKwh,
            )
        }.getOrNull()

    companion object {
        fun from(price: PriceWindow): CachedPriceWindow =
            CachedPriceWindow(
                validFrom = price.validFrom.toString(),
                validTo = price.validTo.toString(),
                pricePencePerKwh = price.pricePencePerKwh,
            )
    }
}
