package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.rishikesh.lifelink.util.applySystemBarInsets

class VerifyEmailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MAIL_SENT = "mail_sent"
        private const val COOLDOWN_MS = 60_000L
    }

    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvResend: TextView
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = auth.currentUser
        if (user == null) { goToLogin(); return }

        setContentView(R.layout.activity_verify_email)
        applySystemBarInsets()

        findViewById<TextView>(R.id.tvVerifyEmailAddress).text = user.email
        tvResend = findViewById(R.id.tvResend)

        findViewById<MaterialButton>(R.id.btnVerified).setOnClickListener { checkVerified(true) }
        tvResend.setOnClickListener { resend() }
        findViewById<TextView>(R.id.tvUseAnotherAccount).setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        // Signup just sent a mail, so start the resend cooldown straight away
        if (intent.getBooleanExtra(EXTRA_MAIL_SENT, false)) startCooldown()
    }

    // Auto-continue when the user comes back from their mail app
    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null) checkVerified(false)
    }

    private fun checkVerified(showMessage: Boolean) {
        val user = auth.currentUser ?: return
        user.reload()
            .addOnSuccessListener {
                if (auth.currentUser?.isEmailVerified == true) {
                    startActivity(
                        Intent(this, PatientHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                } else if (showMessage) {
                    Toast.makeText(this, "Email not verified yet. Check your inbox or spam.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                if (showMessage) Toast.makeText(this, "Couldn't check. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resend() {
        val user = auth.currentUser ?: return
        tvResend.isEnabled = false
        user.sendEmailVerification()
            .addOnSuccessListener {
                Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show()
                startCooldown()
            }
            .addOnFailureListener {
                tvResend.isEnabled = true
                Toast.makeText(this, "Couldn't send. Try again in a few minutes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startCooldown() {
        timer?.cancel()
        tvResend.isEnabled = false
        timer = object : CountDownTimer(COOLDOWN_MS, 1000) {
            override fun onTick(ms: Long) { tvResend.text = "Resend in ${ms / 1000}s" }
            override fun onFinish() {
                tvResend.text = "Resend email"
                tvResend.isEnabled = true
            }
        }.start()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}