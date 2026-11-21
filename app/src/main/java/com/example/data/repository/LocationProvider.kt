package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

data class DetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val nameEn: String,
    val nameAr: String,
    val countryEn: String,
    val countryAr: String,
    val localizedNames: Map<String, String> = emptyMap(),
    val localizedCountries: Map<String, String> = emptyMap()
)

sealed interface LocationFetchResult {
    data class Success(val location: DetectedLocation) : LocationFetchResult
    data object PermissionMissing : LocationFetchResult
    data class ResolutionRequired(val exception: ResolvableApiException) : LocationFetchResult
    data object LocationServicesDisabled : LocationFetchResult
    data object Unavailable : LocationFetchResult
}

class LocationProvider(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val settingsClient = LocationServices.getSettingsClient(context)

    fun hasLocationPermission(): Boolean {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarseGranted || fineGranted
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(activeLanguageCode: String = "en"): LocationFetchResult {
        if (!hasLocationPermission()) {
            return LocationFetchResult.PermissionMissing
        }

        val settingsCheck = checkLocationSettings()
        if (settingsCheck is SettingsCheckResult.ResolutionNeeded) {
            return LocationFetchResult.ResolutionRequired(settingsCheck.exception)
        }
        if (settingsCheck is SettingsCheckResult.Disabled && !isAnySystemLocationProviderEnabled()) {
            return LocationFetchResult.LocationServicesDisabled
        }

        val coords = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            fetchCoordinatesFromFused()
        } ?: fetchLastKnownFallback()
            ?: return if (!isAnySystemLocationProviderEnabled()) {
                LocationFetchResult.LocationServicesDisabled
            } else {
                LocationFetchResult.Unavailable
            }

        return withContext(Dispatchers.IO) {
            val (cityEn, countryEn) = reverseGeocode(coords.first, coords.second, Locale.ENGLISH)
            val (cityAr, countryAr) = reverseGeocode(coords.first, coords.second, Locale.forLanguageTag("ar"))
            val coordsLabel = formatCoordinates(coords.first, coords.second)

            val localizedNames = mutableMapOf<String, String>()
            val localizedCountries = mutableMapOf<String, String>()
            if (cityEn != null) localizedNames["en"] = cityEn
            if (cityAr != null) localizedNames["ar"] = cityAr
            if (countryEn != null) localizedCountries["en"] = countryEn
            if (countryAr != null) localizedCountries["ar"] = countryAr

            val lang = activeLanguageCode.lowercase()
            if (lang != "en" && lang != "ar") {
                val (cityLocal, countryLocal) = reverseGeocode(
                    coords.first,
                    coords.second,
                    Locale.forLanguageTag(lang)
                )
                if (cityLocal != null) localizedNames[lang] = cityLocal
                if (countryLocal != null) localizedCountries[lang] = countryLocal
            }

            LocationFetchResult.Success(
                DetectedLocation(
                    latitude = coords.first,
                    longitude = coords.second,
                    nameEn = cityEn ?: "Current Location",
                    nameAr = cityAr ?: "الموقع الحالي",
                    countryEn = countryEn ?: coordsLabel,
                    countryAr = countryAr ?: coordsLabel,
                    localizedNames = localizedNames,
                    localizedCountries = localizedCountries
                )
            )
        }
    }

    private sealed interface SettingsCheckResult {
        data object Satisfied : SettingsCheckResult
        data class ResolutionNeeded(val exception: ResolvableApiException) : SettingsCheckResult
        data object Disabled : SettingsCheckResult
    }

    private suspend fun checkLocationSettings(): SettingsCheckResult =
        suspendCancellableCoroutine { cont ->
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                10_000L
            ).build()
            val settingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(false)
                .build()

            settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(SettingsCheckResult.Satisfied)
                }
                .addOnFailureListener { exception ->
                    if (!cont.isActive) return@addOnFailureListener
                    if (exception is ResolvableApiException) {
                        cont.resume(SettingsCheckResult.ResolutionNeeded(exception))
                    } else {
                        cont.resume(SettingsCheckResult.Disabled)
                    }
                }
        }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCoordinatesFromFused(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            if (cont.isActive) cont.resume(loc.latitude to loc.longitude)
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last ->
                                    if (cont.isActive) {
                                        cont.resume(last?.let { it.latitude to it.longitude })
                                    }
                                }
                                .addOnFailureListener {
                                    if (cont.isActive) cont.resume(null)
                                }
                        }
                    }
                    .addOnFailureListener {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (cont.isActive) {
                                    cont.resume(last?.let { it.latitude to it.longitude })
                                }
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                    }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastKnownFallback(): Pair<Double, Double>? {
        val fusedLast: Location? = suspendCancellableCoroutine { cont ->
            try {
                fusedClient.lastLocation
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
        if (fusedLast != null) return fusedLast.latitude to fusedLast.longitude

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun isAnySystemLocationProviderEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lon: Double, locale: Locale): Pair<String?, String?> {
        return try {
            if (!Geocoder.isPresent()) return null to null
            val geocoder = Geocoder(context, locale)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            val country = addr?.countryName
            city to country
        } catch (_: Exception) {
            null to null
        }
    }

    private fun formatCoordinates(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s, %.2f°%s", abs(lat), latDir, abs(lon), lonDir)
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
