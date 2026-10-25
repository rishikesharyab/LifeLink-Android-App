package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
            routeAfterLogin(user)
            return
        }



        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailEt = findViewById(R.id.emailEt)
        passwordEt = findViewById(R.id.passwordEt)
        loginBtn = findViewById(R.id.loginBtn)
        signupText = findViewById(R.id.signupText)

        // Show / hide password
        passwordEt.attachPasswordToggle(findViewById(R.id.ivTogglePassword))

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

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            showResetPasswordDialog()
        }
    }

    // ── Reset password ────────────────────────────────────────────────────────

    private fun showResetPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(emailEt.text.toString().trim())
            setSelection(text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("Enter your email and we'll send you a link to reset your password.")
            .setView(container)
            .setPositiveButton("Send link", null)   // overridden below so the dialog stays open on errors
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = input.text.toString().trim()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    input.error = "Enter a valid email address"
                    return@setOnClickListener
                }
                sendResetLink(email, dialog)
            }
        }
        dialog.show()
    }

    private fun sendResetLink(email: String, dialog: AlertDialog) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "If an account exists for $email, a reset link has been sent. Check your spam folder too.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't send reset email. Try again later.", Toast.LENGTH_SHORT).show()
            }
    }

    // Used for BOTH auto-login and manual login.
    // Verified (or grandfathered) users go Home; everyone else goes to the verify screen.
    private fun routeAfterLogin(user: FirebaseUser) {
        val target = if (EmailGate.isAllowed(user)) PatientHomeActivity::class.java
        else VerifyEmailActivity::class.java

        startActivity(
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

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
            .addOnSuccessListener { result ->
                val signedIn = result.user ?: return@addOnSuccessListener
                if (EmailGate.isAllowed(signedIn)) {
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                }
                routeAfterLogin(signedIn)
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