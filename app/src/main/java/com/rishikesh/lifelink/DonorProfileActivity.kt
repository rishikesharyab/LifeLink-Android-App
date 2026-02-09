package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DonorProfileActivity : AppCompatActivity() {

    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var bloodSpinner: Spinner
    private lateinit var saveBtn: Button

    private val db = FirebaseFirestore.getInstance()
    private val uid by lazy { FirebaseAuth.getInstance().currentUser!!.uid }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donor_profile)

        nameEt = findViewById(R.id.nameEt)
        phoneEt = findViewById(R.id.phoneEt)
        bloodSpinner = findViewById(R.id.bloodSpinner)
        saveBtn = findViewById(R.id.saveBtn)

        setupSpinner()

        saveBtn.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupSpinner() {
        val bloodGroups = arrayOf(
            "Select Blood Group",
            "A+", "A-", "B+", "B-",
            "AB+", "AB-", "O+", "O-"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            bloodGroups
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bloodSpinner.adapter = adapter
    }

    private fun saveProfile() {
        val name = nameEt.text.toString().trim()
        val phone = phoneEt.text.toString().trim()
        val bloodGroup = bloodSpinner.selectedItem.toString()

        if (name.isEmpty() || phone.isEmpty() || bloodGroup == "Select Blood Group") {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val data = mapOf(
            "name" to name,
            "phone" to phone,
            "bloodGroup" to bloodGroup,
            "available" to true
        )

        db.collection("Users")
            .document(uid)
            .update(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DonorHomeActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
            }
    }
}
