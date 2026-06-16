package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var nameEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var signupBtn: Button
    private lateinit var loginText: TextView
    private lateinit var patientRadio: RadioButton
    private lateinit var donorRadio: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        nameEt = findViewById(R.id.nameEt)
        emailEt = findViewById(R.id.emailEt)
        passwordEt = findViewById(R.id.passwordEt)
        signupBtn = findViewById(R.id.signupBtn)
        loginText = findViewById(R.id.loginText)


        signupBtn.setOnClickListener {
            registerUser()
        }

        loginText.setOnClickListener {
            finish()
        }
    }

    private fun registerUser() {
        val name = nameEt.text.toString().trim()
        val email = emailEt.text.toString().trim()
        val password = passwordEt.text.toString().trim()


        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val userId = authResult.user?.uid ?: return@addOnSuccessListener
                val db = FirebaseFirestore.getInstance()

                val userMap = hashMapOf(
                    "name" to name,
                    "email" to email
                )

                db.collection("Users")
                    .document(userId)
                    .set(userMap)
                    .addOnSuccessListener {

                        // ✅ IMPORTANT CHANGE HERE
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        Toast.makeText(this, "Database error. Try again.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Signup failed", Toast.LENGTH_SHORT).show()
            }
    }

}
