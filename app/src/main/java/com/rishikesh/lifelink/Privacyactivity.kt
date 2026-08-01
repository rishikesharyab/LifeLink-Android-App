package com.rishikesh.lifelink

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PrivacyActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var switchPhoneVisibility: SwitchMaterial
    private lateinit var switchExactLocation: SwitchMaterial
    private lateinit var tvPhoneVisibilitySubtitle: TextView
    private lateinit var tvLocationPrecisionSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivPrivacyBack).setOnClickListener { finish() }

        switchPhoneVisibility = findViewById(R.id.switchPhoneVisibility)
        switchExactLocation = findViewById(R.id.switchExactLocation)
        tvPhoneVisibilitySubtitle = findViewById(R.id.tvPhoneVisibilitySubtitle)
        tvLocationPrecisionSubtitle = findViewById(R.id.tvLocationPrecisionSubtitle)

        loadSettings()
    }

    private fun loadSettings() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("Users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->

                // Default to visible/exact so existing users see no change until they opt out
                val phoneVisible = doc.getBoolean("phoneVisible") ?: true
                val exactLocation = doc.getBoolean("preciseLocation") ?: true

                switchPhoneVisibility.isChecked = phoneVisible
                updatePhoneSubtitle(phoneVisible)

                switchExactLocation.isChecked = exactLocation
                updateLocationSubtitle(exactLocation)

                switchPhoneVisibility.setOnCheckedChangeListener { _, checked ->
                    updatePhoneSubtitle(checked)
                    updateField(user.uid, "phoneVisible", checked)
                }

                switchExactLocation.setOnCheckedChangeListener { _, checked ->
                    updateLocationSubtitle(checked)
                    updateField(user.uid, "preciseLocation", checked)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load privacy settings", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateField(uid: String, field: String, value: Boolean) {
        db.collection("Users")
            .document(uid)
            .update(field, value)
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't save. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePhoneSubtitle(visible: Boolean) {
        tvPhoneVisibilitySubtitle.text =
            if (visible) "Visible to patients who find you nearby"
            else "Hidden — patients can still message you in-app"
    }

    private fun updateLocationSubtitle(exact: Boolean) {
        tvLocationPrecisionSubtitle.text =
            if (exact) "Your precise location is shown on the map"
            else "Only your approximate area is shown, not exact address"
    }
}