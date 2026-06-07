package com.tapin.teacher.util
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Набљудува дали уредот има интернет конекција, како [Flow] од Boolean.
 *
 * Се користи за автоматска синхронизација: кога мрежата ќе се врати,
 * teacher app-от автоматски ги качува PENDING записите (спец. 3.1 —
 * "uploading them when a network connection is re-established").
 */
object NetworkMonitor {

    fun isOnlineFlow(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetworks = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                activeNetworks -= network
                trySend(activeNetworks.isNotEmpty())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        // Иницијална вредност според тековната активна мрежа.
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
