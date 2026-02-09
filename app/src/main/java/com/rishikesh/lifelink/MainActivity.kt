package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = auth.currentUser
        if (user == null) {
            goToLogin()
            return
        }

        db.collection("Users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    auth.signOut()
                    goToLogin()
                    return@addOnSuccessListener
                }

                val userType = document.getString("userType")

                when (userType) {
                    "Patient" -> {
                        startActivity(Intent(this, PatientHomeActivity::class.java))
                    }
                    "Donor" -> {
                        startActivity(Intent(this, DonorHomeActivity::class.java))
                    }
                    else -> {
                        auth.signOut()
                        Toast.makeText(this, "Invalid user type", Toast.LENGTH_SHORT).show()
                        goToLogin()
                    }
                }

                finish()
            }
            .addOnFailureListener {
                auth.signOut()
                goToLogin()
            }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
