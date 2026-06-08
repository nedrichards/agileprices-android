package com.nedrichards.agileprices

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface OctopusClient {
    suspend fun discoverLatestAgileProduct(): ProductDetailDto

    suspend fun standardUnitRates(
        productCode: String,
        tariffCode: String,
        periodFrom: Instant,
        periodTo: Instant,
    ): List<PriceWindow>
}

class OctopusApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.octopus.energy/v1/",
) : OctopusClient {
    override suspend fun discoverLatestAgileProduct(): ProductDetailDto {
        val latest = selectLatestActiveAgileProduct(fetchAllProducts(), Instant.now())
            ?: throw OctopusApiException("No active Agile import tariff was found.")

        return get("${baseUrl}products/${latest.code}/")
    }

    override suspend fun standardUnitRates(
        productCode: String,
        tariffCode: String,
        periodFrom: Instant,
        periodTo: Instant,
    ): List<PriceWindow> {
        val base = (
            "${baseUrl}products/$productCode" +
                "/electricity-tariffs/$tariffCode/standard-unit-rates/"
            ).toHttpUrl()
                .newBuilder()
                .addQueryParameter("period_from", periodFrom.toString())
                .addQueryParameter("period_to", periodTo.toString())
                .build()
                .toString()

        val rates = mutableListOf<RateDto>()
        var nextUrl: String? = base
        while (nextUrl != null) {
            val page = get<RatePageDto>(nextUrl)
            rates += page.results
            nextUrl = page.next
        }

        return rates.mapNotNull { rate ->
            val validFrom = parseInstantOrNull(rate.validFrom)
            val validTo = parseInstantOrNull(rate.validTo)
            val value = rate.valueIncVat
            if (validFrom == null || validTo == null || value == null) {
                null
            } else {
                PriceWindow(validFrom, validTo, value)
            }
        }.sortedBy { it.validFrom }
    }

    private suspend fun fetchAllProducts(): List<ProductSummaryDto> {
        val products = mutableListOf<ProductSummaryDto>()
        var nextUrl: String? = "${baseUrl}products/"
        while (nextUrl != null) {
            val page = get<ProductListDto>(nextUrl)
            products += page.results
            nextUrl = page.next
        }
        return products
    }

    private suspend inline fun <reified T> get(url: String): T =
        withContext(Dispatchers.IO) {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw OctopusApiException(
                        message = "Octopus API returned HTTP ${it.code}.",
                        statusCode = it.code,
                    )
                }
                val body = it.body.string()
                if (body.isBlank()) throw IOException("Octopus API returned an empty response.")
                json.decodeFromString<T>(body)
            }
        }
}

fun selectLatestActiveAgileProduct(
    products: List<ProductSummaryDto>,
    now: Instant,
): ProductSummaryDto? =
    products
        .filter { product ->
            val code = product.code.uppercase()
            code.contains("AGILE") &&
                !code.contains("EXPORT") &&
                product.isBusiness != true &&
                (product.availableTo == null || (parseInstantOrNull(product.availableTo)?.isAfter(now) == true))
        }
        .sortedWith(
            compareByDescending<ProductSummaryDto> { parseInstantOrNull(it.availableFrom) }
                .thenByDescending { it.code },
        )
        .firstOrNull()

fun buildRegionToTariffsMap(
    productData: ProductDetailDto,
    regionCodeToName: Map<String, String>,
): Map<String, List<TariffOption>> {
    val regionToTariffs = regionCodeToName.keys.associateWith { mutableListOf<TariffOption>() }
    val productName = productData.fullName ?: "Agile Tariff"

    productData.singleRegisterElectricityTariffs.forEach { (regionCode, paymentMethods) ->
        val regionName = regionCodeToName[regionCode] ?: return@forEach
        val tariffCode = paymentMethods["direct_debit_monthly"]?.code
            ?: paymentMethods.values.firstNotNullOfOrNull { it.code }
            ?: return@forEach

        regionToTariffs[regionCode]?.add(TariffOption(tariffCode, "$productName ($regionName)"))
    }

    return regionToTariffs
}

class OctopusApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

@Serializable
data class ProductListDto(
    val next: String? = null,
    val results: List<ProductSummaryDto> = emptyList(),
)

@Serializable
data class ProductSummaryDto(
    val code: String,
    @SerialName("available_from")
    val availableFrom: String? = null,
    @SerialName("available_to")
    val availableTo: String? = null,
    @SerialName("is_business")
    val isBusiness: Boolean? = null,
)

@Serializable
data class ProductDetailDto(
    val code: String,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("single_register_electricity_tariffs")
    val singleRegisterElectricityTariffs: Map<String, Map<String, TariffCodeDto>> = emptyMap(),
)

@Serializable
data class TariffCodeDto(
    val code: String? = null,
)

@Serializable
data class RatePageDto(
    val next: String? = null,
    val results: List<RateDto> = emptyList(),
)

@Serializable
data class RateDto(
    @SerialName("valid_from")
    val validFrom: String? = null,
    @SerialName("valid_to")
    val validTo: String? = null,
    @SerialName("value_inc_vat")
    val valueIncVat: Double? = null,
)

private fun parseInstantOrNull(value: String?): Instant? =
    runCatching {
        value?.let(Instant::parse)
    }.getOrNull()
