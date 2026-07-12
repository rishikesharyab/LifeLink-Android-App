package com.rishikesh.lifelink

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.BloodCamp

class CampDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var tvCampName         : TextView
    private lateinit var tvNgoName          : TextView
    private lateinit var tvCampDate         : TextView
    private lateinit var tvCampTime         : TextView
    private lateinit var tvLocationText     : TextView
    private lateinit var tvCampDistance     : TextView
    private lateinit var tvContactPerson    : TextView
    private lateinit var tvContactPhone     : TextView
    private lateinit var tvContactEmail     : TextView
    private lateinit var tvAlreadyRegistered: TextView
    private lateinit var btnConfirmRegister : Button
    private lateinit var llMapContainer     : LinearLayout
    private lateinit var chipGroupBloodGroups: ChipGroup
    private lateinit var gridFacilities     : GridLayout

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var googleMap: GoogleMap? = null
    private var camp: BloodCamp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camp_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Camp Details"
        toolbar.setNavigationOnClickListener { finish() }

        bindViews()

        camp = intent.getParcelableExtra<BloodCamp>("camp") ?: run { finish(); return }
        populateUi(camp!!)
        setupMap()
    }

    private fun bindViews() {
        tvCampName           = findViewById(R.id.tvDetailCampName)
        tvNgoName            = findViewById(R.id.tvDetailNgoName)
        tvCampDate           = findViewById(R.id.tvDetailDate)
        tvCampTime           = findViewById(R.id.tvDetailTime)
        tvLocationText       = findViewById(R.id.tvLocationText)
        tvCampDistance       = findViewById(R.id.tvDetailDistance)
        tvContactPerson      = findViewById(R.id.tvContactPerson)
        tvContactPhone       = findViewById(R.id.tvContactPhone)
        tvContactEmail       = findViewById(R.id.tvContactEmail)
        tvAlreadyRegistered  = findViewById(R.id.tvAlreadyRegistered)
        btnConfirmRegister   = findViewById(R.id.btnConfirmRegister)
        llMapContainer       = findViewById(R.id.llMapContainer)
        chipGroupBloodGroups = findViewById(R.id.chipGroupBloodGroups)
        gridFacilities       = findViewById(R.id.gridFacilities)
    }

    private fun populateUi(camp: BloodCamp) {
        tvCampName.text      = camp.campName
        tvNgoName.text       = "Organised by ${camp.ngoName}"
        tvCampDate.text      = camp.date
        tvCampTime.text      = "${camp.startTime} – ${camp.endTime}"
        tvLocationText.text  = camp.location
        tvCampDistance.text  = if (camp.distanceKm > 0) "%.1f km away".format(camp.distanceKm) else "—"
        tvContactPerson.text = "${camp.contactName}  ·  ${camp.designation}"
        tvContactPhone.text  = camp.phone
        tvContactEmail.text  = camp.email

        // ── Blood group chips ─────────────────────────────────────────────────
        chipGroupBloodGroups.removeAllViews()
        if (camp.bloodGroupsNeeded.isNotEmpty()) {
            camp.bloodGroupsNeeded.forEach { group ->
                val chip = Chip(this).apply {
                    text            = group
                    isClickable     = false
                    isCheckable     = false
                    setTextColor(resources.getColor(R.color.coral_800, null))
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        resources.getColor(R.color.coral_50, null)
                    )
                    chipStrokeWidth = 1f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(
                        resources.getColor(R.color.coral_200, null)
                    )
                }
                chipGroupBloodGroups.addView(chip)
            }
        } else {
            val tv = TextView(this).apply {
                text      = "All blood groups welcome"
                textSize  = 13f
                setTextColor(resources.getColor(R.color.coral_600, null))
            }
            chipGroupBloodGroups.addView(tv)
        }

        // ── Facilities grid ───────────────────────────────────────────────────
        gridFacilities.removeAllViews()
        val dp = resources.displayMetrics.density
        if (camp.facilities.isNotEmpty()) {
            camp.facilities.forEach { facility ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = GridLayout.LayoutParams().apply {
                        width       = 0
                        columnSpec  = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        bottomMargin = (8 * dp).toInt()
                    }
                }
                val icon = android.widget.ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        resources.getColor(R.color.teal_600, null)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        (18 * dp).toInt(), (18 * dp).toInt()
                    ).apply { marginEnd = (8 * dp).toInt() }
                }
                val tv = TextView(this).apply {
                    text      = facility
                    textSize  = 13f
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                }
                row.addView(icon)
                row.addView(tv)
                gridFacilities.addView(row)
            }
        } else {
            val tv = TextView(this).apply {
                text      = "—"
                textSize  = 13f
                setTextColor(resources.getColor(R.color.text_secondary, null))
            }
            gridFacilities.addView(tv)
        }

        // ── Already registered? ───────────────────────────────────────────────
        val userId = auth.currentUser?.uid ?: ""
        if (camp.registeredBy.contains(userId)) showAlreadyRegistered()

        // ── Location tap → show/hide map ──────────────────────────────────────
        findViewById<LinearLayout>(R.id.tvDetailLocation).setOnClickListener {
            llMapContainer.visibility = if (llMapContainer.visibility == View.VISIBLE)
                View.GONE else View.VISIBLE
        }

        // ── Register button ───────────────────────────────────────────────────
        btnConfirmRegister.setOnClickListener {
            handleRegistration(camp.campId)
        }
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.campMapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val c = camp ?: return
        if (c.latitude != 0.0 && c.longitude != 0.0) {
            val pos = LatLng(c.latitude, c.longitude)
            map.addMarker(MarkerOptions().position(pos).title(c.campName).snippet(c.location))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
            map.uiSettings.isZoomControlsEnabled      = true
            map.uiSettings.isMyLocationButtonEnabled  = true
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) map.isMyLocationEnabled = true
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private fun handleRegistration(campId: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val isProfileComplete = doc.getBoolean("profileCompleted") == true
                if (isProfileComplete) {
                    registerForCamp(campId, uid)
                } else {
                    Toast.makeText(this,
                        "Please complete your donor profile first",
                        Toast.LENGTH_SHORT).show()
                    startActivity(android.content.Intent(this, CompleteDonorProfileActivity::class.java))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun registerForCamp(campId: String, userId: String) {
        btnConfirmRegister.isEnabled = false
        btnConfirmRegister.text      = "Registering..."

        db.collection("BloodCamps").document(campId)
            .update("registeredBy", FieldValue.arrayUnion(userId))
            .addOnSuccessListener {
                Toast.makeText(this, "Successfully registered! 🎉", Toast.LENGTH_SHORT).show()
                showAlreadyRegistered()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Registration failed. Try again.", Toast.LENGTH_SHORT).show()
                btnConfirmRegister.isEnabled = true
                btnConfirmRegister.text      = "Register for this Camp"
            }
    }

    private fun showAlreadyRegistered() {
        tvAlreadyRegistered.visibility = View.VISIBLE
        btnConfirmRegister.isEnabled   = false
        btnConfirmRegister.text        = "Already Registered"
        btnConfirmRegister.alpha       = 0.5f
    }
}