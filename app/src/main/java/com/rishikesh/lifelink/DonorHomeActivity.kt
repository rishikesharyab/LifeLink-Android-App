package com.rishikesh.lifelink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class DonorHomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donor_home)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.contains("bloodGroup") || !doc.contains("phone")) {
                    startActivity(Intent(this, DonorProfileActivity::class.java))
                    finish()
                }
            }


        // 🔹 Init location client once
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 🔹 Logout button
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // 🔹 Load map
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        enableLocationAndSave()
    }

    private fun enableLocationAndSave() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION
            )
        } else {
            googleMap.isMyLocationEnabled = true
            saveDonorLocationSafely()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocationAndSave()
        } else {
            Toast.makeText(
                this,
                "Location permission is required to show your position",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveDonorLocationSafely() {

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->

                // 🔴 VERY IMPORTANT: location can be NULL
                if (location == null) {
                    Toast.makeText(
                        this,
                        "Turn on GPS to update location",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val latLng = LatLng(location.latitude, location.longitude)

                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 14f)
                )

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                val db = FirebaseFirestore.getInstance()

                val data = hashMapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "lastUpdated" to FieldValue.serverTimestamp()
                )

                db.collection("Users")
                    .document(uid)
                    .update(data)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to get location",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
