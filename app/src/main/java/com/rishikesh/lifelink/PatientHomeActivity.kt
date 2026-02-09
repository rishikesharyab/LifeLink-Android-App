package com.rishikesh.lifelink

import Donor
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.FirebaseFirestore
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

    // ================= ON CREATE =================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)

        // 🔍 SearchView
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = "Search blood group (A+, O-, etc)"

        // ⬆️ Bottom Sheet
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 180
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
    }

    // ================= MAP READY =================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        enablePatientLocation()

        // 🔍 Search action
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val bloodGroup = query?.trim()?.uppercase() ?: return false
                searchDonors(bloodGroup)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })
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
                patientLat = location.latitude
                patientLng = location.longitude

                val latLng = LatLng(patientLat, patientLng)
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 14f)
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enablePatientLocation()
        }
    }

    // ================= SEARCH DONORS =================

    private fun searchDonors(bloodGroup: String) {

        donorList.clear()
        googleMap.clear()

        db.collection("Users")
            .whereEqualTo("userType", "Donor")
            .whereEqualTo("bloodGroup", bloodGroup)
            .get()
            .addOnSuccessListener { documents ->

                if (documents.isEmpty) {
                    Toast.makeText(this, "No donors found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val distance = distanceInKm(patientLat, patientLng, lat, lng)
                    if (distance > 10) continue

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
}
