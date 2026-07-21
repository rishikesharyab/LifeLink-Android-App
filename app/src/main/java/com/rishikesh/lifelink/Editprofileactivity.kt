package com.rishikesh.lifelink

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvAvatar: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etLocation: EditText
    private lateinit var chipGroup: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        tvAvatar = findViewById(R.id.tvEditAvatar)
        etName = findViewById(R.id.etEditName)
        etPhone = findViewById(R.id.etEditPhone)
        etLocation = findViewById(R.id.etEditLocation)
        chipGroup = findViewById(R.id.chipGroupEditBloodGroup)

        findViewById<ImageView>(R.id.ivEditBack).setOnClickListener { finish() }

        setupBloodGroupChips()
        loadCurrentProfile()

        findViewById<TextView>(R.id.btnSaveProfile).setOnClickListener { saveProfile() }
    }

    private fun setupBloodGroupChips() {
        val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        bloodGroups.forEach { group ->
            val chip = Chip(this).apply {
                text = group
                isCheckable = true
                chipBackgroundColor = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        ContextCompat.getColor(this@EditProfileActivity, R.color.coral_50),
                        ContextCompat.getColor(this@EditProfileActivity, R.color.surface_secondary)
                    )
                )
                setTextColor(
                    android.content.res.ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(
                            ContextCompat.getColor(this@EditProfileActivity, R.color.coral_800),
                            ContextCompat.getColor(this@EditProfileActivity, R.color.text_secondary)
                        )
                    )
                )
            }
            chipGroup.addView(chip)
        }
    }

    private fun loadCurrentProfile() {
        val user = auth.currentUser ?: return

        db.collection("Users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                val phone = doc.getString("phone") ?: ""
                val location = doc.getString("location") ?: ""
                val bloodGroup = doc.getString("bloodGroup") ?: ""

                etName.setText(name)
                etPhone.setText(phone)
                etLocation.setText(location)
                tvAvatar.text = initialsFrom(name)

                for (i in 0 until chipGroup.childCount) {
                    val chip = chipGroup.getChildAt(i) as Chip
                    chip.isChecked = chip.text.toString() == bloodGroup
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load your profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val user = auth.currentUser ?: return

        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val bloodGroup = (chipGroup.findViewById<Chip>(chipGroup.checkedChipId))?.text?.toString()

        if (name.isEmpty()) {
            etName.error = "Name can't be empty"
            return
        }
        if (phone.length < 10) {
            etPhone.error = "Enter a valid phone number"
            return
        }
        if (bloodGroup == null) {
            Toast.makeText(this, "Select your blood group", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "location" to location,
            "bloodGroup" to bloodGroup
        )

        db.collection("Users")
            .document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't save changes. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun initialsFrom(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }
}