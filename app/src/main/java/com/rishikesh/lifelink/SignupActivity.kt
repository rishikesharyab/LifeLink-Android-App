package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var nameEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var confirmPasswordEt: EditText
    private lateinit var signupBtn: Button
    private lateinit var loginText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        nameEt = findViewById(R.id.nameEt)
        emailEt = findViewById(R.id.emailEt)
        passwordEt = findViewById(R.id.passwordEt)
        confirmPasswordEt = findViewById(R.id.confirmPasswordEt)
        signupBtn = findViewById(R.id.signupBtn)
        loginText = findViewById(R.id.loginText)

        // Show / hide password
        passwordEt.attachPasswordToggle(findViewById(R.id.ivTogglePassword))
        confirmPasswordEt.attachPasswordToggle(findViewById(R.id.ivToggleConfirmPassword))

        // Handle system bar insets to avoid overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }


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
        val confirm = confirmPasswordEt.text.toString().trim()


        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEt.error = "Enter a valid email address"
            emailEt.requestFocus()
            return
        }

        if (password.length < 6) {
            passwordEt.error = "Password must be at least 6 characters"
            passwordEt.requestFocus()
            return
        }

        if (password != confirm) {
            confirmPasswordEt.error = "Passwords don't match"
            confirmPasswordEt.requestFocus()
            return
        }

        signupBtn.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val user = authResult.user ?: run {
                    signupBtn.isEnabled = true
                    return@addOnSuccessListener
                }
                val db = FirebaseFirestore.getInstance()

                val userMap = hashMapOf(
                    "name" to name,
                    "email" to email
                )

                db.collection("Users")
                    .document(user.uid)
                    .set(userMap)
                    .addOnSuccessListener {

                        // Send the verification link, then hold the user on the verify screen
                        user.sendEmailVerification()

                        startActivity(
                            Intent(this, VerifyEmailActivity::class.java)
                                .putExtra(VerifyEmailActivity.EXTRA_MAIL_SENT, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        signupBtn.isEnabled = true
                        Toast.makeText(this, "Database error. Try again.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                signupBtn.isEnabled = true
                Toast.makeText(this, it.message ?: "Signup failed", Toast.LENGTH_SHORT).show()
            }
    }

}