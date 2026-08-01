package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding


class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var loginBtn: Button
    private lateinit var signupText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            startActivity(Intent(this, PatientHomeActivity::class.java))
            finish()
            return
        }



        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailEt = findViewById(R.id.emailEt)
        passwordEt = findViewById(R.id.passwordEt)
        loginBtn = findViewById(R.id.loginBtn)
        signupText = findViewById(R.id.signupText)

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

        loginBtn.setOnClickListener {
            loginUser()
        }

        signupText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }


    // 🔁 Used for BOTH auto-login and manual login

    private fun showRoleChooserDialog() {

        AlertDialog.Builder(this)
            .setTitle("Choose your role")
            .setMessage("You can switch roles anytime")
            .setCancelable(false)
            .setPositiveButton("Patient") { _, _ ->
                openPatientMode()
            }
            .setNegativeButton("Donor") { _, _ ->
                openDonorMode()
            }
            .show()
    }

    private fun openPatientMode() {
        val intent = Intent(this, PatientHomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    private fun openDonorMode() {

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
        Log.d("ROLE_FLOW", "Donor selected, UID = $uid")

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    Log.d("ROLE_FLOW", "User document NOT FOUND")
                }

                val profileCompleted = doc.getBoolean("profileCompleted") ?: false
                Log.d("ROLE_FLOW", "profileCompleted = $profileCompleted")



                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Log.e("ROLE_FLOW", "Firestore error", it)
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
                startActivity(Intent(this, PatientHomeActivity::class.java))
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
