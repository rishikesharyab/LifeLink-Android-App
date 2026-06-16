package com.rishikesh.lifelink


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.Donor
import java.util.Locale
import kotlin.math.*

class PatientHomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private val LOCATION_PERMISSION_CODE = 1001

    private lateinit var googleMap: GoogleMap
    private lateinit var searchView: SearchView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var donorRecyclerView: RecyclerView
    private lateinit var donorAdapter: DonorAdapter
    private val donorList = mutableListOf<Donor>()

    private val db = FirebaseFirestore.getInstance()

    private var patientLat = 0.0
    private var patientLng = 0.0

    private var userLat = 0.0
    private var userLng = 0.0

    private var donorDashboardShown = false
    var isDashboardVisible = false
    private var isDonorListVisible = false

    private var currentLocationText: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ================= ON CREATE =================


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("TEST_FLOW", "PatientHomeActivity opened")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)
        findViewById<View>(R.id.customBottomNav).bringToFront()
        val nav = findViewById<View>(R.id.customBottomNav)
        nav.bringToFront()
        nav.invalidate()
        nav.requestLayout()
       // dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        Log.d("TEST_FLOW", "Calling location function")
        getCurrentLocation()



        onBackPressedDispatcher.addCallback(this) {

            when {

                // If donor list is open → close it
                isDonorListVisible -> {

                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    isDonorListVisible = false

                    openDonorDashboard()   // show dashboard again
                }

                // If dashboard visible → exit app
                isDashboardVisible -> {

                    finish()
                }

                else -> finish()
            }
        }

        // 🔍 SearchView
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = "Search blood group (A+, O-, etc)"

        // ⬆️ Bottom Sheet
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 380
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN


        // 📋 RecyclerView
        donorRecyclerView = findViewById(R.id.donorRecyclerView)
        donorRecyclerView.layoutManager = LinearLayoutManager(this)

        donorAdapter = DonorAdapter(donorList) { donor ->
            val latLng = LatLng(donor.latitude, donor.longitude)
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, 15f)
            )
        }

        donorRecyclerView.adapter = donorAdapter

        // 🗺️ Map
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.patientMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Bottom Navigation Bar
        val bottomNav = findViewById<View>(R.id.customBottomNav)
        bottomNav.bringToFront()
        bottomNav.invalidate()
        bottomNav.requestLayout()

        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navDonate = findViewById<LinearLayout>(R.id.navDonate)

// Inflate layout manually
        val homeView = layoutInflater.inflate(R.layout.layout_bottom_nav_item, navHome, true)
        val donateView = layoutInflater.inflate(R.layout.layout_bottom_nav_item, navDonate, true)

        homeView.findViewById<ImageView>(R.id.iconDefault).setImageResource(R.drawable.ic_home)
        homeView.findViewById<TextView>(R.id.title).text = "Home"

        donateView.findViewById<ImageView>(R.id.iconDefault).setImageResource(R.drawable.outline_donor_heart)
        donateView.findViewById<TextView>(R.id.title).text = "Donate"
        val homeIcon = homeView.findViewById<ImageView>(R.id.iconDefault)
        val donateIcon = donateView.findViewById<ImageView>(R.id.iconDefault)

        val homeText = homeView.findViewById<TextView>(R.id.title)
        val donateText = donateView.findViewById<TextView>(R.id.title)


        // 👉 Default selected
        selectTab(navHome, navDonate, homeIcon, donateIcon, homeText, donateText)

        // 👉 Click listeners
        navHome.setOnClickListener {

            selectTab(navHome, navDonate, homeIcon, donateIcon, homeText, donateText)

            openDonorDashboard()
        }

        navDonate.setOnClickListener {

            selectTab(navDonate, navHome, homeIcon, donateIcon, homeText, donateText)

            checkDonorRegistration()
        }


    }

    // ================= MAP READY =================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

//        openDonorDashboard()
//
//        enablePatientLocation()


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }

        // 🔍 Search action
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {


//                val bloodGroup = query?.trim()?.uppercase() ?: return false

                val bloodGroup = query
                    ?.replace(" ", "")
                    ?.uppercase()
                    ?: return false

                // 👉 Hide dashboard ONLY here
                val fragment = supportFragmentManager.findFragmentByTag("DonorDashboard")
                if (fragment is DonorBottomSheetFragment) {
                    fragment.dismiss()
                }

                isDashboardVisible = false
                isDonorListVisible = true

                searchDonors(bloodGroup)

                searchView.clearFocus()

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

     //Bottom Nav bar
     private fun selectTab(
         selected: LinearLayout,
         other: LinearLayout,
         homeIcon: ImageView,
         donateIcon: ImageView,
         homeText: TextView,
         donateText: TextView
     ) {

         if (selected.id == R.id.navHome) {

             homeIcon.setImageResource(R.drawable.ic_home) // active
             donateIcon.setImageResource(R.drawable.outline_donor_heart)

             homeText.setTextColor(getColor(R.color.black))
             donateText.setTextColor(getColor(R.color.gray))

         } else {

             homeIcon.setImageResource(R.drawable.ic_home)
             donateIcon.setImageResource(R.drawable.outline_donor_heart)

             homeText.setTextColor(getColor(R.color.gray))
             donateText.setTextColor(getColor(R.color.black))
         }
     }

    // ================= LOCATION =================

    private fun enablePatientLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
            return
        }

        googleMap.isMyLocationEnabled = true

        val fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude

                val latLng = LatLng(patientLat, patientLng)
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 14f)
                )
            }
        }
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        if (requestCode == LOCATION_PERMISSION_CODE &&
//            grantResults.isNotEmpty() &&
//            grantResults[0] == PackageManager.PERMISSION_GRANTED
//        ) {
//            enablePatientLocation()
//        }
//    }








    // ================= SEARCH DONORS =================

    private fun searchDonors(bloodGroup: String) {

        Log.d("SEARCH_DEBUG", "Searching for: $bloodGroup")


        donorList.clear()

        if (userLat == 0.0 || userLng == 0.0) {
            Toast.makeText(this, "Fetching location, try again...", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Users")
            .whereEqualTo("bloodGroup", bloodGroup)
            .get()
            .addOnSuccessListener { documents ->

                Log.d("SEARCH_DEBUG", "Docs size: ${documents.size()}")

                if (documents.isEmpty) {
                    Toast.makeText(this, "No donors found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (doc in documents) {

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val distance = distanceInKm(userLat, userLng, lat, lng)

                    val donor = Donor(
                        name = doc.getString("name") ?: "Unknown",
                        bloodGroup = doc.getString("bloodGroup") ?: "N/A",
                        phone = doc.getString("phone") ?: "N/A",
                        latitude = lat,
                        longitude = lng,
                        distanceKm = distance
                    )

                    donorList.add(donor)

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(donor.name)
                    )
                }

                donorAdapter.notifyDataSetChanged()

                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
    }


    // ================= OPEN DASHBOARD =================

    private fun openDonorDashboard() {

        val existing = supportFragmentManager.findFragmentByTag("DonorDashboard")

        if (existing is DonorBottomSheetFragment) {
            existing.dismiss()
        }

        if (isDashboardVisible) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return



        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->

                val donor = Donor(
                    id = uid,
                    name = doc.getString("name") ?: "",
                    location = doc.getString("location") ?: "",
                    bloodGroup = doc.getString("bloodGroup") ?: "",
                    phone = doc.getString("phone") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    distanceKm = 0.0,
                    totalDonations = doc.getLong("totalDonations")?.toInt() ?: 0,
                    lastDonationDate = doc.getDate("lastDonationDate"),
                    isAvailable = doc.getBoolean("available") ?: true
                )

                val sheet = DonorBottomSheetFragment.newInstance(donor, currentLocationText)
                sheet.show(supportFragmentManager, "DonorDashboard")

                isDashboardVisible = true
                isDonorListVisible = false
            }

    }

    private fun getCurrentLocation() {

        Handler(Looper.getMainLooper()).postDelayed({

            val latLng = LatLng(userLat, userLng)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))

        }, 500)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->

            if (location != null) {

                userLat = location.latitude
                userLng = location.longitude

                Log.d("LOCATION_DEBUG", "Lat: $userLat")

                // Move map to correct location
                val latLng = LatLng(userLat, userLng)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))

                getAddressFromLatLng(userLat, userLng)

            } else {
                Log.d("LOCATION_DEBUG", "Location NULL")
            }
        }
        // 🔥 Refresh dashboard when location ready
        openDonorDashboard()
    }

    private fun getAddressFromLatLng(lat: Double, lng: Double) {

        val geocoder = Geocoder(this, Locale.getDefault())

        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {

                val address = addresses[0]

                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""
                val sector = address.subLocality ?: ""
             // For Sector and distric
                val fullLocation = when {
                    sector.isNotEmpty() -> "$sector, $city"
                    city.isNotEmpty() -> "$city, $state"
                    else -> address.getAddressLine(0) ?: "Unknown location"
                }

                // For sector, distric and state
//                val fullLocation = when {
//                    sector.isNotEmpty() && city.isNotEmpty() && state.isNotEmpty() ->
//                        "$sector, $city, $state"
//
//                    city.isNotEmpty() && state.isNotEmpty() ->
//                        "$city, $state"
//
//                    else ->
//                        address.getAddressLine(0) ?: "Unknown location"
//                }

                currentLocationText = fullLocation
                // 🔥 IMPORTANT: THIS updates UI

                Log.d("LOCATION_DEBUG", "Address: $fullLocation")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)



        if (requestCode == 1001) {

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.d("LOCATION_DEBUG", "Permission Granted by user")

//                if (ContextCompat.checkSelfPermission(
//                        this,
//                        Manifest.permission.ACCESS_FINE_LOCATION
//                    ) == PackageManager.PERMISSION_GRANTED
//                ) {
//                    googleMap.isMyLocationEnabled = true
//                }

                // 🔥 CALL AGAIN AFTER PERMISSION
                getCurrentLocation()

            } else {
                Log.d("LOCATION_DEBUG", "Permission Denied")
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            }

        }
    }



    // ================= DISTANCE =================

    private fun distanceInKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    fun resetDashboardState() {
        donorDashboardShown = false
    }

    // ================= CHECK DONOR REGISTRATION =================
    // Bottom navigation Bar
    private fun checkDonorRegistration() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->

                val isProfileComplete = doc.getBoolean("profileCompleted") == true

                if (isProfileComplete) {

                    // ✅ Already donor
                    startActivity(
                        Intent(this, AlreadyDonorActivity::class.java)
                    )

                } else {

                    // ❌ Not registered
                    startActivity(
                        Intent(this, CompleteDonorProfileActivity::class.java)
                    )
                }
            }
    }
}
