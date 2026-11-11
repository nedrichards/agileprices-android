package com.nedrichards.agileprices

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient

internal fun AgileSettings.needsWearConnectionRecovery(now: Instant): Boolean =
    !selectedTariffCode.isNullOrBlank() &&
        cachedPrices.none { it.validFrom <= now && now < it.validTo }

/** Keep the radio request only for this foreground recovery, including the fetch. */
@android.annotation.SuppressLint("MissingPermission") // Permissions are declared by the Wear flavor only.
internal suspend fun recoverWearPrices(context: Context) {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val available = CompletableDeferred<Network>()
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                available.complete(network)
            }
        }
    }
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    manager.requestNetwork(request, callback)
    try {
        withTimeoutOrNull(30_000) {
            val network = available.await()
            // Route only this refresh through the acquired network, including DNS.
            val client = OkHttpClient.Builder()
                .socketFactory(network.socketFactory)
                .dns(Dns { hostname -> network.getAllByName(hostname).toList() })
                .callTimeout(java.time.Duration.ofSeconds(15))
                .build()
            try {
                AgileRepository(
                    settingsStore = SettingsStore(context.applicationContext),
                    octopusApi = OctopusApi(client),
                    surfaceUpdater = WearPriceSurfaceUpdater(context.applicationContext),
                ).refresh()
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
            true
        } ?: throw java.io.IOException("Connection timed out")
    } finally {
        manager.unregisterNetworkCallback(callback)
    }
}

// Called with the screen's activity context so Settings stays in the normal back stack.
internal fun openWearWifiSettings(context: Context) {
    val intent = Intent("com.google.android.clockwork.settings.connectivity.wifi.ADD_NETWORK_SETTINGS")

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
