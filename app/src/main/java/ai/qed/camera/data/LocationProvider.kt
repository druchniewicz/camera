package ai.qed.camera.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@SuppressLint("MissingPermission")
class LocationProvider(
    context: Context,
    private val intervalInMilliseconds: Long
): DefaultLifecycleObserver {
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    var lastKnownLocation: Location? = null
        private set

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                lastKnownLocation = location
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        setup(intervalInMilliseconds)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        locationClient.removeLocationUpdates(locationCallback)
        super.onDestroy(owner)
    }

    fun updateInterval(intervalInMilliseconds: Long) {
        setup(intervalInMilliseconds)
    }

    private fun setup(intervalInMilliseconds: Long) {
        locationClient.removeLocationUpdates(locationCallback)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalInMilliseconds
        ).build()

        locationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
}
