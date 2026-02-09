package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var loginBtn: Button
    private lateinit var signupText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 1. Initialize auth ONCE
        auth = FirebaseAuth.getInstance()

        // ✅ 2. Auto-login check
        val currentUser = auth.currentUser
        if (currentUser != null) {
            redirectUser(currentUser.uid)
            return
        }

        // ✅ 3. Load UI only if not logged in
        setContentView(R.layout.activity_login)

        emailEt = findViewById(R.id.emailEt)
        passwordEt = findViewById(R.id.passwordEt)
        loginBtn = findViewById(R.id.loginBtn)
        signupText = findViewById(R.id.signupText)

        loginBtn.setOnClickListener {
            loginUser()
        }

        signupText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    // 🔁 Used for BOTH auto-login and manual login
    private fun redirectUser(uid: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                val userType = document.getString("userType")

                val intent = when (userType) {
                    "Patient" -> Intent(this, PatientHomeActivity::class.java)
                    "Donor" -> Intent(this, DonorHomeActivity::class.java)
                    else -> {
                        FirebaseAuth.getInstance().signOut()
                        Intent(this, LoginActivity::class.java)
                    }
                }

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                FirebaseAuth.getInstance().signOut()
            }
    }

    private fun loginUser() {
        val email = emailEt.text.toString().trim()
        val pass = passwordEt.text.toString().trim()

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                redirectUser(auth.currentUser!!.uid)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Login Failed: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
