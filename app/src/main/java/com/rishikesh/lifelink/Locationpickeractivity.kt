package com.rishikesh.lifelink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import java.util.Locale

/**
 * Fixed-center-pin map picker (Rapido/Uber style). The pin never moves —
 * the user drags the map underneath it. On camera-idle we reverse-geocode
 * the center point. A search bar and a "use current location" shortcut
 * are provided for cases where the user isn't physically at the venue.
 *
 * Returns via setResult(RESULT_OK, intent) with EXTRA_* below.
 */
class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_CITY = "extra_city"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_PINCODE = "extra_pincode"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        private const val DEFAULT_ZOOM = 16f
        private const val LOCATION_PERMISSION_REQUEST = 2001
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private lateinit var etSearch: EditText
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var tvAddress: TextView
    private lateinit var ivCenterPin: ImageView
    private lateinit var vPinShadow: View
    private lateinit var googleMap: GoogleMap

    private var resolvedLat = 0.0
    private var resolvedLng = 0.0
    private var resolvedAddress = ""
    private var resolvedCity = ""
    private var resolvedState = ""
    private var resolvedPincode = ""

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var suppressNextCameraIdle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        etSearch = findViewById(R.id.etPickerSearch)
        suggestionsContainer = findViewById(R.id.suggestionsContainer)
        tvAddress = findViewById(R.id.tvPickerAddress)
        ivCenterPin = findViewById(R.id.ivCenterPin)
        vPinShadow = findViewById(R.id.vPinShadow)

        findViewById<ImageView>(R.id.ivPickerBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnCurrentLocation).setOnClickListener {
            centerOnCurrentLocation(animate = true, zoom = DEFAULT_ZOOM)
        }
        findViewById<android.widget.Button>(R.id.btnConfirmLocation).setOnClickListener { confirmAndReturn() }

        // Push the search bar below the status bar without affecting the full-bleed map
        val topBar = findViewById<LinearLayout>(R.id.topBar)
        val topBarBasePadding = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = topBarBasePadding + statusBarInset)
            insets
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val query = s?.toString().orEmpty()
                if (query.length < 3) {
                    suggestionsContainer.visibility = android.view.View.GONE
                    return
                }
                searchRunnable = Runnable { runSearch(query) }
                searchHandler.postDelayed(searchRunnable!!, 400)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.pickerMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isCompassEnabled = false

        // Show something immediately, then smoothly glide to the user's
        // actual location once we have it — avoids a jarring instant jump.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 4.5f))
        centerOnCurrentLocation(animate = true, zoom = DEFAULT_ZOOM)

        map.setOnCameraMoveStartedListener {
            liftPin()
        }

        map.setOnCameraIdleListener {
            dropPin()
            if (suppressNextCameraIdle) {
                suppressNextCameraIdle = false
                return@setOnCameraIdleListener
            }
            val center = map.cameraPosition.target
            reverseGeocode(center.latitude, center.longitude)
        }

        // Tapping directly on a business/shop icon returns its real name and
        // exact coordinate — reverse-geocoding a dragged pin center can't do
        // this since Geocoder only knows street addresses, not POI listings.
        map.setOnPoiClickListener { poi -> onPoiSelected(poi) }

        if (hasLocationPermission()) {
            map.isMyLocationEnabled = false // we use our own fixed-pin UI, not the blue dot layer
        }
    }

    /** Lifts the pin off the map with a quick ease-out, shadow shrinking under it — mid-drag state. */
    private fun liftPin() {
        if (ivCenterPin.translationY != 0f) return // already lifted
        ivCenterPin.animate()
            .translationY((-14).dp.toFloat())
            .setDuration(120)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        vPinShadow.animate()
            .scaleX(0.6f).scaleY(0.6f).alpha(0.4f)
            .setDuration(120)
            .start()
    }

    /** Drops the pin back down with a slight overshoot/bounce on release — the "confirm point" moment. */
    private fun dropPin() {
        ivCenterPin.animate()
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f))
            .start()
        vPinShadow.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(220)
            .start()
    }

    /**
     * Centers the map on the device's current location. Used both on launch
     * (so the picker opens where the user actually is, not a hardcoded city)
     * and from the current-location FAB.
     */
    private fun centerOnCurrentLocation(animate: Boolean, zoom: Float) {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST
            )
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val target = if (location != null) LatLng(location.latitude, location.longitude) else null
                if (target != null) {
                    moveCameraSmoothly(target, zoom, animate)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) moveCameraSmoothly(LatLng(last.latitude, last.longitude), zoom, animate)
                    }
                }
            }
    }

    private fun moveCameraSmoothly(target: LatLng, zoom: Float, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngZoom(target, zoom)
        if (animate) {
            googleMap.animateCamera(update, 600, null as GoogleMap.CancelableCallback?)
        } else {
            googleMap.moveCamera(update)
        }
    }

    private fun onPoiSelected(poi: PointOfInterest) {
        resolvedLat = poi.latLng.latitude
        resolvedLng = poi.latLng.longitude
        resolvedAddress = poi.name
        tvAddress.text = poi.name

        suppressNextCameraIdle = true
        moveCameraSmoothly(poi.latLng, DEFAULT_ZOOM, animate = true)

        // Fill city/state/pincode in the background for Firestore, without
        // overwriting the POI name we just set as the display address.
        try {
            val results = geocoder.getFromLocation(poi.latLng.latitude, poi.latLng.longitude, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                resolvedCity = addr.locality ?: addr.subAdminArea ?: ""
                resolvedState = addr.adminArea ?: ""
                resolvedPincode = addr.postalCode ?: ""
            }
        } catch (e: Exception) {
            // Non-fatal — city/state/pincode stay blank, address name is still correct
        }
    }

    private fun runSearch(query: String) {
        try {
            val results = geocoder.getFromLocationName(query, 5)
            suggestionsContainer.removeAllViews()

            if (results.isNullOrEmpty()) {
                suggestionsContainer.visibility = android.view.View.GONE
                return
            }

            results.forEach { address ->
                val line = address.getAddressLine(0) ?: return@forEach
                val row = TextView(this).apply {
                    text = line
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setPadding(14.dp, 10.dp, 14.dp, 10.dp)
                    setOnClickListener {
                        suggestionsContainer.visibility = android.view.View.GONE
                        etSearch.setText(line)
                        moveCameraSmoothly(LatLng(address.latitude, address.longitude), DEFAULT_ZOOM, animate = true)
                    }
                }
                suggestionsContainer.addView(row)
            }
            suggestionsContainer.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            // Geocoder network/service failure — fail quietly, user can still drag the map
            suggestionsContainer.visibility = android.view.View.GONE
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        resolvedLat = lat
        resolvedLng = lng
        tvAddress.text = "Locating…"

        try {
            val results = geocoder.getFromLocation(lat, lng, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                resolvedAddress = addr.getAddressLine(0) ?: "$lat, $lng"
                resolvedCity = addr.locality ?: addr.subAdminArea ?: ""
                resolvedState = addr.adminArea ?: ""
                resolvedPincode = addr.postalCode ?: ""
                tvAddress.text = resolvedAddress
            } else {
                resolvedAddress = "Pinned location ($lat, $lng)"
                tvAddress.text = resolvedAddress
            }
        } catch (e: Exception) {
            resolvedAddress = "Pinned location ($lat, $lng)"
            tvAddress.text = resolvedAddress
        }
    }

    private fun confirmAndReturn() {
        if (resolvedAddress.isBlank()) {
            Toast.makeText(this, "Move the map to select a location first", Toast.LENGTH_SHORT).show()
            return
        }

        val result = Intent().apply {
            putExtra(EXTRA_ADDRESS, resolvedAddress)
            putExtra(EXTRA_CITY, resolvedCity)
            putExtra(EXTRA_STATE, resolvedState)
            putExtra(EXTRA_PINCODE, resolvedPincode)
            putExtra(EXTRA_LAT, resolvedLat)
            putExtra(EXTRA_LNG, resolvedLng)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            centerOnCurrentLocation(animate = true, zoom = DEFAULT_ZOOM)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}