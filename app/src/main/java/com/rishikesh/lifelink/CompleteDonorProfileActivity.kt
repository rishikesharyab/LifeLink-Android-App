package com.rishikesh.lifelink

import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import android.Manifest
import android.app.DatePickerDialog
import android.util.Log
import android.view.View
import com.google.android.gms.location.Priority

class CompleteDonorProfileActivity : AppCompatActivity() {

    private var currentStep = 1

    // Step layouts
    private lateinit var step1: LinearLayout
    private lateinit var step2: LinearLayout
    private lateinit var step3: LinearLayout

    // UI
    private lateinit var stepText: TextView
    private lateinit var stepLabel: TextView
    private lateinit var segment1: View
    private lateinit var segment2: View
    private lateinit var segment3: View
    private lateinit var backBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var saveBtn: Button

    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var locationEt: EditText
    private lateinit var lastDonationEt: EditText
    private lateinit var cbNeverDonated: CheckBox

    private lateinit var genderBox: LinearLayout
    private lateinit var tvGender: TextView
    private var selectedGender: String? = null

    // Blood group chips
    private lateinit var bloodChips: Map<String, TextView>
    private var selectedBloodGroup: String? = null

    // Travel range rows
    private lateinit var rowTravel5: LinearLayout
    private lateinit var rowTravel10: LinearLayout
    private lateinit var rowTravel20: LinearLayout
    private lateinit var radioTravel5: View
    private lateinit var radioTravel10: View
    private lateinit var radioTravel20: View
    private var selectedTravelRange = "5"

    // Summary (step 3)
    private lateinit var tvSummaryBloodGroup: TextView
    private lateinit var tvSummaryLocation: TextView
    private lateinit var tvSummaryTravel: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLat = 0.0
    private var userLng = 0.0
    private var currentLocationText: String = ""
    private var selectedDonationDate: Date? = null

    private val stepTitles = listOf("Basic info", "Medical info", "Availability")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_donor_profile)
        applySystemBarInsets()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()

        // Step layouts
        step1 = findViewById(R.id.step1Layout)
        step2 = findViewById(R.id.step2Layout)
        step3 = findViewById(R.id.step3Layout)

        // UI
        stepText = findViewById(R.id.stepText)
        stepLabel = findViewById(R.id.stepLabel)
        segment1 = findViewById(R.id.segment1)
        segment2 = findViewById(R.id.segment2)
        segment3 = findViewById(R.id.segment3)
        backBtn = findViewById(R.id.backBtn)
        nextBtn = findViewById(R.id.nextBtn)
        saveBtn = findViewById(R.id.saveBtn)

        nameEt = findViewById(R.id.nameEt)
        phoneEt = findViewById(R.id.phoneEt)
        ageEt = findViewById(R.id.ageEt)
        locationEt = findViewById(R.id.locationEt)
        lastDonationEt = findViewById(R.id.lastDonationEt)
        cbNeverDonated = findViewById(R.id.cbNeverDonated)

        genderBox = findViewById(R.id.genderBox)
        tvGender = findViewById(R.id.tvGender)
        genderBox.setOnClickListener { showGenderMenu() }

        bloodChips = mapOf(
            "O+" to findViewById(R.id.chipOPos),
            "O-" to findViewById(R.id.chipONeg),
            "A+" to findViewById(R.id.chipAPos),
            "A-" to findViewById(R.id.chipANeg),
            "B+" to findViewById(R.id.chipBPos),
            "B-" to findViewById(R.id.chipBNeg),
            "AB+" to findViewById(R.id.chipABPos),
            "AB-" to findViewById(R.id.chipABNeg)
        )
        bloodChips.forEach { (group, chip) ->
            chip.setOnClickListener { selectBloodGroup(group) }
        }

        rowTravel5 = findViewById(R.id.rowTravel5)
        rowTravel10 = findViewById(R.id.rowTravel10)
        rowTravel20 = findViewById(R.id.rowTravel20)
        radioTravel5 = findViewById(R.id.radioTravel5)
        radioTravel10 = findViewById(R.id.radioTravel10)
        radioTravel20 = findViewById(R.id.radioTravel20)

        rowTravel5.setOnClickListener { selectTravelRange("5") }
        rowTravel10.setOnClickListener { selectTravelRange("10") }
        rowTravel20.setOnClickListener { selectTravelRange("20") }
        selectTravelRange("5")

        tvSummaryBloodGroup = findViewById(R.id.tvSummaryBloodGroup)
        tvSummaryLocation = findViewById(R.id.tvSummaryLocation)
        tvSummaryTravel = findViewById(R.id.tvSummaryTravel)

        lastDonationEt.setOnClickListener { showDatePicker() }

        cbNeverDonated.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                lastDonationEt.setText("")
                lastDonationEt.isEnabled = false
                selectedDonationDate = null
            } else {
                lastDonationEt.isEnabled = true
            }
        }

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
            saveProfile()
        }
    }

    // ================= GENDER SELECTION =================

    private fun showGenderMenu() {
        val popup = PopupMenu(this, genderBox)
        listOf("Male", "Female", "Other").forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener { item ->
            selectedGender = item.title.toString()
            tvGender.text = selectedGender
            tvGender.setTextColor(android.graphics.Color.parseColor("#2C2C2A"))
            true
        }
        popup.show()
    }

    // ================= BLOOD GROUP SELECTION =================

    private fun selectBloodGroup(group: String) {
        selectedBloodGroup = group
        bloodChips.forEach { (g, chip) ->
            if (g == group) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(getColor(android.R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(android.graphics.Color.parseColor("#5F5E5A"))
            }
        }
    }

    // ================= TRAVEL RANGE SELECTION =================

    private fun selectTravelRange(range: String) {
        selectedTravelRange = range

        val rows = mapOf("5" to rowTravel5, "10" to rowTravel10, "20" to rowTravel20)
        val radios = mapOf("5" to radioTravel5, "10" to radioTravel10, "20" to radioTravel20)

        rows.forEach { (r, row) ->
            row.setBackgroundResource(if (r == range) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
            (row.getChildAt(0) as TextView).setTextColor(
                if (r == range) android.graphics.Color.parseColor("#2C2C2A")
                else android.graphics.Color.parseColor("#5F5E5A")
            )
        }
        radios.forEach { (r, radio) ->
            radio.setBackgroundResource(if (r == range) R.drawable.radio_circle_selected else R.drawable.radio_circle_unselected)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val dialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
            selectedDonationDate = selectedCalendar.time

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            lastDonationEt.setText(sdf.format(selectedDonationDate!!))
        }, year, month, day)

        // Block any date after today
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    // ================= UI CONTROL =================

    private fun updateUI() {

        step1.visibility = if (currentStep == 1) LinearLayout.VISIBLE else LinearLayout.GONE
        step2.visibility = if (currentStep == 2) LinearLayout.VISIBLE else LinearLayout.GONE
        step3.visibility = if (currentStep == 3) LinearLayout.VISIBLE else LinearLayout.GONE

        stepText.text = "Step $currentStep of 3"
        stepLabel.text = stepTitles[currentStep - 1]

        val activeColor = android.graphics.Color.parseColor("#8B3A1F")
        val inactiveColor = android.graphics.Color.parseColor("#E4D8C8")
        segment1.setBackgroundColor(if (currentStep >= 1) activeColor else inactiveColor)
        segment2.setBackgroundColor(if (currentStep >= 2) activeColor else inactiveColor)
        segment3.setBackgroundColor(if (currentStep >= 3) activeColor else inactiveColor)

        backBtn.visibility = if (currentStep == 1) Button.GONE else Button.VISIBLE
        nextBtn.visibility = if (currentStep == 3) Button.GONE else Button.VISIBLE
        saveBtn.visibility = if (currentStep == 3) Button.VISIBLE else Button.GONE

        if (currentStep == 3) populateSummary()
    }

    private fun populateSummary() {
        tvSummaryBloodGroup.text = selectedBloodGroup ?: "—"
        tvSummaryLocation.text = if (currentLocationText.isNotEmpty()) currentLocationText else "—"
        tvSummaryTravel.text = "$selectedTravelRange km"
    }

    // ================= VALIDATION =================

    private fun validateStep(): Boolean {

        return when (currentStep) {

            1 -> {
                val name = nameEt.text.toString()
                val phone = phoneEt.text.toString()
                val age = ageEt.text.toString()

                when {
                    name.isEmpty() -> {
                        toast("Please enter your name")
                        false
                    }
                    phone.isEmpty() -> {
                        toast("Please enter your phone number")
                        false
                    }
                    phone.length != 10 -> {
                        toast("Enter a valid 10-digit phone number")
                        false
                    }
                    age.isEmpty() -> {
                        toast("Please enter your age")
                        false
                    }
                    (age.toIntOrNull() ?: 0) < 18 -> {
                        toast("You must be at least 18 years old to register as a donor")
                        false
                    }
                    selectedGender == null -> {
                        toast("Please select your gender")
                        false
                    }
                    else -> true
                }
            }

            2 -> {
                if (selectedBloodGroup == null) {
                    toast("Please select your blood group")
                    false
                } else if (cbNeverDonated.isChecked) {
                    true
                } else if (selectedDonationDate == null) {
                    toast("Please select last donation date")
                    false
                } else true
            }

            else -> true
        }
    }

    // ================= SAVE PROFILE =================

    private fun saveProfile() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val name = nameEt.text.toString()
        val phone = phoneEt.text.toString()
        val ageText = ageEt.text.toString()

        if (name.isEmpty()) {
            toast("Please enter your name")
            return
        }

        if (phone.isEmpty() || phone.length != 10) {
            toast("Enter a valid 10-digit phone number")
            return
        }

        if (ageText.isEmpty()) {
            toast("Please enter your age")
            return
        }

        val age = ageText.toIntOrNull()
        if (age == null) {
            toast("Enter valid age number")
            return
        }

        if (age < 18) {
            toast("You must be at least 18 years old to register as a donor")
            return
        }

        if (selectedGender == null) {
            toast("Please select your gender")
            return
        }

        val gender = selectedGender ?: "Not specified"

        val bloodGroup = selectedBloodGroup
        if (bloodGroup == null) {
            toast("Please select blood group")
            return
        }

        if (userLat == 0.0 || userLng == 0.0) {
            toast("Location not ready yet, saving anyway")
        }

        try {
            val location = currentLocationText

            val data = hashMapOf(
                "name" to name,
                "phone" to phone,
                "gender" to gender,
                "age" to age,
                "bloodGroup" to bloodGroup,
                "travelRange" to selectedTravelRange,
                "latitude" to userLat,
                "longitude" to userLng,
                "location" to location,
                "lastDonationDate" to selectedDonationDate,
                "neverDonated" to cbNeverDonated.isChecked,
                "profileCompleted" to true,
                "userType" to "Donor"
            )

            FirebaseFirestore.getInstance()
                .collection("Users")
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    toast("Profile completed successfully")
                    finish()
                }
                .addOnFailureListener {
                    Log.e("STEP_DEBUG", "Firestore FAILED: ${it.message}")
                    toast("Error: ${it.message}")
                }

        } catch (e: Exception) {
            Log.e("STEP_DEBUG", "CRASH HERE: ${e.message}")
        }
    }

    // ================= LOCATION =================

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, null
        ).addOnSuccessListener { location ->

            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude
                getAddressFromLatLng(userLat, userLng)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        userLat = lastLoc.latitude
                        userLng = lastLoc.longitude
                        getAddressFromLatLng(userLat, userLng)
                    } else {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getAddressFromLatLng(lat: Double, lng: Double) {
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
                locationEt.setText(fullLocation)
                findViewById<TextView>(R.id.tvLocationBadge).visibility = View.VISIBLE
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