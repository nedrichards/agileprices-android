package com.nedrichards.agileprices

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OctopusApiTest {
    @Test
    fun discoverLatestAgileProductFollowsPaginationAndSkipsExportBusinessAndExpiredProducts() = runTest {
        val interceptor = JsonResponseInterceptor(
            JsonRoute(
                matches = { it.encodedPath == "/v1/products/" && it.queryParameter("page") == null },
                body = """
                    {
                      "next": "https://octopus.test/v1/products/?page=2",
                      "results": [
                        {
                          "code": "AGILE-24-10-01",
                          "available_from": "2024-10-01T00:00:00Z"
                        },
                        {
                          "code": "AGILE-EXPORT-26-01-01",
                          "available_from": "2026-01-01T00:00:00Z"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            JsonRoute(
                matches = { it.encodedPath == "/v1/products/" && it.queryParameter("page") == "2" },
                body = """
                    {
                      "next": null,
                      "results": [
                        {
                          "code": "AGILE-BUSINESS-26-05-01",
                          "available_from": "2026-05-01T00:00:00Z",
                          "is_business": true
                        },
                        {
                          "code": "AGILE-EXPIRED",
                          "available_from": "2025-01-01T00:00:00Z",
                          "available_to": "2026-01-01T00:00:00Z"
                        },
                        {
                          "code": "AGILE-26-05-01",
                          "available_from": "2026-05-01T00:00:00Z"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            JsonRoute(
                matches = { it.encodedPath == "/v1/products/AGILE-26-05-01/" },
                body = agileProductDetailJson,
            ),
        )
        val api = api(interceptor)

        val product = api.discoverLatestAgileProduct()

        assertEquals("AGILE-26-05-01", product.code)
        assertEquals("Agile May 2026", product.fullName)
        assertEquals(3, interceptor.requests.size)
        assertTrue(interceptor.requests[0].endsWith("/v1/products/"))
        assertTrue(interceptor.requests[1].endsWith("/v1/products/?page=2"))
        assertTrue(interceptor.requests[2].endsWith("/v1/products/AGILE-26-05-01/"))
    }

    @Test
    fun tariffMappingUsesDirectDebitFromMockedProductJson() = runTest {
        val interceptor = JsonResponseInterceptor(
            JsonRoute(
                matches = { it.encodedPath == "/v1/products/" },
                body = """
                    {
                      "next": null,
                      "results": [
                        {
                          "code": "AGILE-26-05-01",
                          "available_from": "2026-05-01T00:00:00Z"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            JsonRoute(
                matches = { it.encodedPath == "/v1/products/AGILE-26-05-01/" },
                body = agileProductDetailJson,
            ),
        )
        val product = api(interceptor).discoverLatestAgileProduct()

        val tariffs = buildRegionToTariffsMap(product, mapOf("_C" to "London", "_D" to "Merseyside"))

        assertEquals("E-1R-AGILE-26-05-01-C", tariffs["_C"]!!.single().code)
        assertEquals("Agile May 2026 (London)", tariffs["_C"]!!.single().fullName)
        assertEquals("E-1R-AGILE-26-05-01-D-PREPAY", tariffs["_D"]!!.single().code)
        assertEquals("Agile May 2026 (Merseyside)", tariffs["_D"]!!.single().fullName)
    }

    @Test
    fun standardUnitRatesFollowsPaginationFiltersMalformedRowsAndSortsWindows() = runTest {
        val interceptor = JsonResponseInterceptor(
            JsonRoute(
                matches = {
                    it.encodedPath == "/v1/products/AGILE-26-05-01/electricity-tariffs/E-1R-AGILE-26-05-01-C/standard-unit-rates/" &&
                        it.queryParameter("page") == null
                },
                body = """
                    {
                      "next": "https://octopus.test/v1/products/AGILE-26-05-01/electricity-tariffs/E-1R-AGILE-26-05-01-C/standard-unit-rates/?page=2",
                      "results": [
                        {
                          "valid_from": "2026-06-05T12:30:00Z",
                          "valid_to": "2026-06-05T13:00:00Z",
                          "value_inc_vat": 8.2
                        },
                        {
                          "valid_from": "not-a-date",
                          "valid_to": "2026-06-05T13:30:00Z",
                          "value_inc_vat": 99.0
                        }
                      ]
                    }
                """.trimIndent(),
            ),
            JsonRoute(
                matches = {
                    it.encodedPath == "/v1/products/AGILE-26-05-01/electricity-tariffs/E-1R-AGILE-26-05-01-C/standard-unit-rates/" &&
                        it.queryParameter("page") == "2"
                },
                body = """
                    {
                      "next": null,
                      "results": [
                        {
                          "valid_from": "2026-06-05T12:00:00Z",
                          "valid_to": "2026-06-05T12:30:00Z",
                          "value_inc_vat": -1.5
                        },
                        {
                          "valid_from": "2026-06-05T13:00:00Z",
                          "valid_to": "2026-06-05T13:30:00Z"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val prices = api(interceptor).standardUnitRates(
            productCode = "AGILE-26-05-01",
            tariffCode = "E-1R-AGILE-26-05-01-C",
            periodFrom = Instant.parse("2026-06-05T12:00:00Z"),
            periodTo = Instant.parse("2026-06-05T14:00:00Z"),
        )

        assertEquals(2, prices.size)
        assertEquals(Instant.parse("2026-06-05T12:00:00Z"), prices[0].validFrom)
        assertEquals(-1.5, prices[0].pricePencePerKwh, 0.0001)
        assertEquals(Instant.parse("2026-06-05T12:30:00Z"), prices[1].validFrom)
        assertEquals(8.2, prices[1].pricePencePerKwh, 0.0001)
        assertEquals(2, interceptor.requests.size)
        assertTrue(interceptor.requests.first().contains("period_from=2026-06-05T12%3A00%3A00Z"))
        assertTrue(interceptor.requests.first().contains("period_to=2026-06-05T14%3A00%3A00Z"))
    }

    @Test
    fun standardUnitRatesThrowsOctopusApiExceptionForHttpErrors() = runTest {
        val interceptor = JsonResponseInterceptor(
            JsonRoute(
                matches = { it.encodedPath.endsWith("/standard-unit-rates/") },
                code = 500,
                body = """{"detail":"upstream failed"}""",
            ),
        )

        val error = runCatching {
            api(interceptor).standardUnitRates(
                productCode = "AGILE-26-05-01",
                tariffCode = "E-1R-AGILE-26-05-01-C",
                periodFrom = Instant.parse("2026-06-05T12:00:00Z"),
                periodTo = Instant.parse("2026-06-05T14:00:00Z"),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is OctopusApiException)
        assertEquals(500, (error as OctopusApiException).statusCode)
        assertEquals("Octopus API returned HTTP 500.", error.message)
    }

    @Test
    fun standardUnitRatesThrowsIoExceptionForEmptyResponses() = runTest {
        val interceptor = JsonResponseInterceptor(
            JsonRoute(
                matches = { it.encodedPath.endsWith("/standard-unit-rates/") },
                body = "",
            ),
        )

        val error = runCatching {
            api(interceptor).standardUnitRates(
                productCode = "AGILE-26-05-01",
                tariffCode = "E-1R-AGILE-26-05-01-C",
                periodFrom = Instant.parse("2026-06-05T12:00:00Z"),
                periodTo = Instant.parse("2026-06-05T14:00:00Z"),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IOException)
        assertEquals("Octopus API returned an empty response.", error!!.message)
    }

    private fun api(interceptor: JsonResponseInterceptor): OctopusApi =
        OctopusApi(
            client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build(),
            baseUrl = "https://octopus.test/v1/",
        )

    private class JsonResponseInterceptor(
        private vararg val routes: JsonRoute,
    ) : Interceptor {
        val requests = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request.url.toString()
            val route = routes.firstOrNull { it.matches(request.url) }
            val code = route?.code ?: 404
            val body = route?.body ?: """{"detail":"not found"}"""
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private class JsonRoute(
        val matches: (HttpUrl) -> Boolean,
        val code: Int = 200,
        val body: String,
    )

    private companion object {
        val agileProductDetailJson = """
            {
              "code": "AGILE-26-05-01",
              "full_name": "Agile May 2026",
              "single_register_electricity_tariffs": {
                "_C": {
                  "prepay": {
                    "code": "E-1R-AGILE-26-05-01-C-PREPAY"
                  },
                  "direct_debit_monthly": {
                    "code": "E-1R-AGILE-26-05-01-C"
                  }
                },
                "_D": {
                  "prepay": {
                    "code": "E-1R-AGILE-26-05-01-D-PREPAY"
                  }
                },
                "_Z": {
                  "direct_debit_monthly": {
                    "code": "E-1R-AGILE-26-05-01-Z"
                  }
                }
              }
            }
        """.trimIndent()
    }
}
