package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvAvatar: TextView
    private lateinit var tvName: TextView
    private lateinit var tvJoined: TextView
    private lateinit var tvBloodGroup: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvDonations: TextView
    private lateinit var tvLivesSaved: TextView
    private lateinit var tvBadgeTitle: TextView
    private lateinit var tvBadgeNext: TextView
    private lateinit var progressBadge: ProgressBar
    private lateinit var tvAvailabilitySubtitle: TextView
    private lateinit var switchAvailability: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bindViews()

        findViewById<ImageView>(R.id.ivProfileBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.rowEditProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowDonationHistory).setOnClickListener {
            startActivity(Intent(this, DonationHistoryActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun bindViews() {
        tvAvatar = findViewById(R.id.tvProfileAvatar)
        tvName = findViewById(R.id.tvProfileName)
        tvJoined = findViewById(R.id.tvProfileJoined)
        tvBloodGroup = findViewById(R.id.tvProfileBloodGroup)
        tvEmail = findViewById(R.id.tvProfileEmail)
        tvPhone = findViewById(R.id.tvProfilePhone)
        tvDonations = findViewById(R.id.tvProfileDonations)
        tvLivesSaved = findViewById(R.id.tvProfileLivesSaved)
        tvBadgeTitle = findViewById(R.id.tvProfileBadgeTitle)
        tvBadgeNext = findViewById(R.id.tvProfileBadgeNext)
        progressBadge = findViewById(R.id.progressBadge)
        tvAvailabilitySubtitle = findViewById(R.id.tvProfileAvailabilitySubtitle)
        switchAvailability = findViewById(R.id.switchProfileAvailability)
    }

    private fun loadProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Full email straight from FirebaseAuth — always current, no Firestore round trip needed
        tvEmail.text = user.email ?: "No email on file"

        db.collection("Users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->

                val name = doc.getString("name") ?: "Unnamed donor"
                val phone = doc.getString("phone") ?: "No phone on file"
                val bloodGroup = doc.getString("bloodGroup") ?: "—"
                val totalDonations = doc.getLong("totalDonations")?.toInt() ?: 0
                val isAvailable = doc.getBoolean("available") ?: true

                tvAvatar.text = initialsFrom(name)
                tvName.text = name
                tvJoined.text = "Blood donor"
                tvBloodGroup.text = bloodGroup
                tvPhone.text = phone
                tvDonations.text = totalDonations.toString()
                tvLivesSaved.text = (totalDonations * 3).toString()

                val badge = Badge.from(totalDonations)
                tvBadgeTitle.text = "${badge.title} ${badge.subtitle}"

                val (nextLabel, progress) = badgeProgress(totalDonations)
                tvBadgeNext.text = nextLabel
                progressBadge.progress = progress

                switchAvailability.isChecked = isAvailable
                updateAvailabilitySubtitle(isAvailable)

                switchAvailability.setOnCheckedChangeListener { _, checked ->
                    updateAvailabilitySubtitle(checked)
                    db.collection("Users")
                        .document(user.uid)
                        .update("available", checked)
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Couldn't update availability. Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load profile. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAvailabilitySubtitle(isAvailable: Boolean) {
        tvAvailabilitySubtitle.text =
            if (isAvailable) "Visible to nearby requests"
            else "Hidden from nearby requests"
    }

    private fun initialsFrom(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    /** Returns a short "X donations to next tier" label and a 0–100 progress value. */
    private fun badgeProgress(totalDonations: Int): Pair<String, Int> {
        // Matches Badge.from() exactly: 0 -> NEW_HERO, <3 -> RISING_HERO,
        // <8 -> SUPER_HERO, <15 -> LEGEND, else -> CHAMPION
        val tiers = listOf(1, 3, 8, 15)
        val nextTierNames = listOf("Rising Hero", "Super Hero", "Blood Legend", "Life Champion")

        for (i in tiers.indices) {
            if (totalDonations < tiers[i]) {
                val prevTier = if (i == 0) 0 else tiers[i - 1]
                val span = tiers[i] - prevTier
                val progress = (((totalDonations - prevTier).toFloat() / span) * 100).toInt()
                val remaining = tiers[i] - totalDonations
                return "$remaining donation${if (remaining == 1) "" else "s"} to ${nextTierNames[i]}" to progress
            }
        }
        return "Top tier reached" to 100
    }
}