package com.rishikesh.lifelink

import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
//import java.util.jar.Manifest
import android.Manifest
import android.util.Log
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority

class CompleteDonorProfileActivity : AppCompatActivity() {

    private var currentStep = 1

    // Step layouts
    private lateinit var step1: LinearLayout
    private lateinit var step2: LinearLayout
    private lateinit var step3: LinearLayout

    // UI
    private lateinit var stepText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var backBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var saveBtn: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLat = 0.0
    private var userLng = 0.0
    private var currentLocationText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_donor_profile)
        // ====== SPINNERS SETUP ======

        val genderSpinner = findViewById<Spinner>(R.id.genderSpinner)
        val bloodSpinner = findViewById<Spinner>(R.id.bloodSpinner)

        // For current Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        // AUTO GET LOCATION
        getCurrentLocation()





// Gender spinner
        val genderList = listOf("Select Gender", "Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genderList
        )
        genderSpinner.adapter = genderAdapter

// Blood group spinner
        val bloodList = listOf("Select Blood Group", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        val bloodAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            bloodList
        )
        bloodSpinner.adapter = bloodAdapter


        // Step layouts
        step1 = findViewById(R.id.step1Layout)
        step2 = findViewById(R.id.step2Layout)
        step3 = findViewById(R.id.step3Layout)

        // UI
        stepText = findViewById(R.id.stepText)
        progressBar = findViewById(R.id.progressBar)
        backBtn = findViewById(R.id.backBtn)
        nextBtn = findViewById(R.id.nextBtn)
        saveBtn = findViewById(R.id.saveBtn)

        updateUI()

        nextBtn.setOnClickListener {
            if (validateStep()) {
                currentStep++
                updateUI()
            }
        }

        backBtn.setOnClickListener {
            currentStep--
            updateUI()
        }

        saveBtn.setOnClickListener {
            Toast.makeText(this, "SAVE CLICKED", Toast.LENGTH_SHORT).show()
            Log.d("CLICK_TEST", "Save button clicked")
            saveProfile()
        }
    }

    // ================= UI CONTROL =================

    private fun updateUI() {

        step1.visibility = if (currentStep == 1) LinearLayout.VISIBLE else LinearLayout.GONE
        step2.visibility = if (currentStep == 2) LinearLayout.VISIBLE else LinearLayout.GONE
        step3.visibility = if (currentStep == 3) LinearLayout.VISIBLE else LinearLayout.GONE

        stepText.text = "Step $currentStep of 3"
        progressBar.progress = currentStep

        backBtn.visibility = if (currentStep == 1) Button.GONE else Button.VISIBLE
        nextBtn.visibility = if (currentStep == 3) Button.GONE else Button.VISIBLE
        saveBtn.visibility = if (currentStep == 3) Button.VISIBLE else Button.GONE
    }

    // ================= VALIDATION =================

    private fun validateStep(): Boolean {

        return when (currentStep) {

            1 -> {
                val name = findViewById<EditText>(R.id.nameEt).text.toString()
                val phone = findViewById<EditText>(R.id.phoneEt).text.toString()
                val age = findViewById<EditText>(R.id.ageEt).text.toString()

                if (name.isEmpty() || phone.isEmpty() || age.isEmpty()) {
                    toast("Please fill all fields")
                    false
                } else true
            }

            2 -> {
                val lastDonation =
                    findViewById<EditText>(R.id.lastDonationEt).text.toString()

                if (lastDonation.isEmpty()) {
                    toast("Please enter last donation date")
                    false
                } else true
            }

            else -> true
        }
    }

    // ================= SAVE PROFILE =================

    private fun saveProfile() {

        Log.d("STEP_DEBUG", "1: saveProfile started")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // ✅ Get inputs
        val name = findViewById<EditText>(R.id.nameEt).text.toString()
        val phone = findViewById<EditText>(R.id.phoneEt).text.toString()
        val ageText = findViewById<EditText>(R.id.ageEt).text.toString()

        Log.d("STEP_DEBUG", "2: Inputs read")

        // ✅ Validate age
        if (ageText.isEmpty()) {
            toast("Enter age")
            return
        }

        val age = ageText.toIntOrNull()
        if (age == null) {
            toast("Enter valid age number")
            return
        }

        // ✅ Get gender safely
        val genderSpinner = findViewById<Spinner>(R.id.genderSpinner)
        val gender = genderSpinner.selectedItem?.toString() ?: "Not specified"

        // ✅ Validate blood group
        val bloodSpinner = findViewById<Spinner>(R.id.bloodSpinner)
        val bloodGroup = bloodSpinner.selectedItem?.toString() ?: ""

        if (bloodGroup == "Select Blood Group" || bloodGroup.isEmpty()) {
            toast("Please select blood group")
            return
        }

        // ✅ Travel range
        val travelGroup = findViewById<RadioGroup>(R.id.travelGroup)
        val travelRange = when (travelGroup.checkedRadioButtonId) {
            R.id.travel5 -> "5"
            R.id.travel10 -> "10"
            R.id.travel20 -> "20"
            else -> "5"
        }

        // ⚠️ Location warning (don’t block)
        if (userLat == 0.0 || userLng == 0.0) {
            toast("Location not ready yet, saving anyway")
        }

        try {
            Log.d("STEP_DEBUG", "Building data...")

            val location = currentLocationText

            val data = hashMapOf(
                "name" to name,
                "phone" to phone,
                "gender" to gender,
                "age" to age,
                "bloodGroup" to bloodGroup,

                "travelRange" to travelRange,
                "latitude" to userLat,
                "longitude" to userLng,
                "location" to location,
                "profileCompleted" to true,
                "userType" to "Donor"
            )

            Log.d("STEP_DEBUG", "Data built successfully")
            Log.d("STEP_DEBUG", "3: Before Firestore")

            FirebaseFirestore.getInstance()
                .collection("Users")
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("STEP_DEBUG", "4: Firestore SUCCESS")
                    toast("Profile completed successfully")
                    finish()
                }
                .addOnFailureListener {
                    Log.e("STEP_DEBUG", "5: Firestore FAILED: ${it.message}")
                    toast("Error: ${it.message}")
                }

        } catch (e: Exception) {
            Log.e("STEP_DEBUG", "CRASH HERE: ${e.message}")
        }
    }

    // To get current location below user name
    private fun getCurrentLocation() {
        Log.d("LOCATION_DEBUG", "Function called")

        if (ActivityCompat.checkSelfPermission(
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

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->

            if (location != null) {

                userLat = location.latitude
                userLng = location.longitude

                Log.d("LOCATION_DEBUG", "Lat: $userLat Lng: $userLng")

                getAddressFromLatLng(userLat, userLng)

            } else {
                // 🔥 Fallback to lastLocation
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->

                    if (lastLoc != null) {

                        userLat = lastLoc.latitude
                        userLng = lastLoc.longitude

                        Log.d("LOCATION_DEBUG", "Fallback Lat: $userLat Lng: $userLng")

                        getAddressFromLatLng(userLat, userLng)

                    } else {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private fun getAddressFromLatLng(lat: Double, lng: Double) {

        Log.d("LOCATION_DEBUG", "Getting address for: $lat, $lng")
        val geocoder = Geocoder(this, Locale.getDefault())

        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {

                val address = addresses[0]

                val sectorOrArea = address.subLocality ?: address.premises ?: ""
                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""

                val fullLocation = if (city.isNotEmpty()) {
                    "$sectorOrArea, $city"
                } else {
                    address.getAddressLine(0) ?: "Unknown location"
                }

                currentLocationText = fullLocation
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

        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}