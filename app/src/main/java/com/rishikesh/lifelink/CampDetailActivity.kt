package com.rishikesh.lifelink

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.rishikesh.lifelink.util.applySystemBarInsets
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.BloodCamp

class CampDetailActivity : AppCompatActivity() {

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
    private lateinit var btnOpenMaps        : TextView
    private lateinit var llContactPhone     : LinearLayout
    private lateinit var llContactEmail     : LinearLayout
    private lateinit var chipGroupBloodGroups: ChipGroup
    private lateinit var gridFacilities     : GridLayout

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var camp: BloodCamp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camp_detail)
        applySystemBarInsets()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Camp Details"
        toolbar.setTitleTextColor(getColor(R.color.text_primary))
        toolbar.navigationIcon?.setTint(getColor(R.color.text_primary))
        toolbar.setNavigationOnClickListener { finish() }

        bindViews()

        camp = intent.getParcelableExtra<BloodCamp>("camp") ?: run { finish(); return }
        populateUi(camp!!)
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
        btnOpenMaps          = findViewById(R.id.btnOpenMaps)
        llContactPhone       = findViewById(R.id.llContactPhone)
        llContactEmail       = findViewById(R.id.llContactEmail)
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

        // ── Open in Google Maps / call / email (available to everyone) ────────
        btnOpenMaps.setOnClickListener { openInGoogleMaps(camp) }

        llContactPhone.setOnClickListener {
            if (camp.phone.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${camp.phone}")))
            }
        }
        llContactEmail.setOnClickListener {
            if (camp.email.isNotBlank()) {
                try {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${camp.email}")))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Already registered? ───────────────────────────────────────────────
        val userId = auth.currentUser?.uid ?: ""
        if (camp.registeredBy.contains(userId)) showAlreadyRegistered()

        // ── This org's own camp? Show a management shortcut instead of hiding it ──
        if (camp.orgId.isNotBlank() && camp.orgId == userId) {
            btnConfirmRegister.text = "Manage applications"
            btnConfirmRegister.isEnabled = true
            btnConfirmRegister.alpha = 1f
            btnConfirmRegister.setOnClickListener {
                val intent = Intent(this, OrgCampManageActivity::class.java)
                intent.putExtra("camp", camp)
                startActivity(intent)
            }
            tvAlreadyRegistered.visibility = View.GONE
            return
        }

        // ── Register button ───────────────────────────────────────────────────
        btnConfirmRegister.setOnClickListener {
            handleRegistration(camp.campId)
        }
    }

    // ── Google Maps ───────────────────────────────────────────────────────────

    private fun openInGoogleMaps(camp: BloodCamp) {
        val hasCoords = camp.latitude != 0.0 && camp.longitude != 0.0
        val query = if (hasCoords) "${camp.latitude},${camp.longitude}" else Uri.encode(camp.location)

        if (!hasCoords && camp.location.isBlank()) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val geoUri = if (hasCoords)
            Uri.parse("geo:$query?q=$query(${Uri.encode(camp.campName)})")
        else
            Uri.parse("geo:0,0?q=$query")

        try {
            startActivity(Intent(Intent.ACTION_VIEW, geoUri).setPackage("com.google.android.apps.maps"))
        } catch (e: ActivityNotFoundException) {
            // Google Maps app not installed — fall back to the browser
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                )
            )
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
                    startActivity(Intent(this, CompleteDonorProfileActivity::class.java))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun registerForCamp(campId: String, userId: String) {
        btnConfirmRegister.isEnabled = false
        btnConfirmRegister.text      = "Registering..."

        db.collection("Users").document(userId).get()
            .addOnSuccessListener { userDoc ->

                val application = hashMapOf(
                    "donorId" to userId,
                    "name" to (userDoc.getString("name") ?: "Unknown"),
                    "age" to (userDoc.getLong("age")?.toInt() ?: 0),
                    "bloodGroup" to (userDoc.getString("bloodGroup") ?: ""),
                    "phone" to (userDoc.getString("phone") ?: ""),
                    "appliedAt" to com.google.firebase.Timestamp.now(),
                    "donated" to false
                )

                db.collection("BloodCamps").document(campId)
                    .collection("applications")
                    .add(application)
                    .addOnSuccessListener {
                        // Keep registeredBy in sync too — existing "already registered" check relies on it
                        db.collection("BloodCamps").document(campId)
                            .update("registeredBy", FieldValue.arrayUnion(userId))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Successfully registered! 🎉", Toast.LENGTH_SHORT).show()
                                showAlreadyRegistered()
                            }
                            .addOnFailureListener {
                                // Application was still recorded successfully; this is a secondary sync field
                                Toast.makeText(this, "Successfully registered! 🎉", Toast.LENGTH_SHORT).show()
                                showAlreadyRegistered()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Registration failed. Try again.", Toast.LENGTH_SHORT).show()
                        btnConfirmRegister.isEnabled = true
                        btnConfirmRegister.text      = "Register for this Camp"
                    }
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