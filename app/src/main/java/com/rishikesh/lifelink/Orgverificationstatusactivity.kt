package com.rishikesh.lifelink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.util.applySystemBarInsets
import java.text.SimpleDateFormat
import java.util.Locale
import android.widget.FrameLayout

/**
 * Shown to an org/NGO account after registration so they can check where their
 * verification stands. Reads verificationStatus (pending/verified/rejected)
 * off the Users doc — the same field AdminApprovalActivity writes to.
 */
class OrgVerificationStatusActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var statusIconBadge: android.widget.FrameLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var rejectionReasonBox: LinearLayout
    private lateinit var tvRejectionReason: TextView
    private lateinit var tvOrgName: TextView
    private lateinit var tvRegNumber: TextView
    private lateinit var tvSubmittedOn: TextView
    private lateinit var btnPrimaryAction: MaterialButton
    private lateinit var tvContactSupport: TextView

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_org_verification_status)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivVerificationBack).setOnClickListener { finish() }

        statusIconBadge = findViewById(R.id.statusIconBadge)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        rejectionReasonBox = findViewById(R.id.rejectionReasonBox)
        tvRejectionReason = findViewById(R.id.tvRejectionReason)
        tvOrgName = findViewById(R.id.tvOrgName)
        tvRegNumber = findViewById(R.id.tvRegNumber)
        tvSubmittedOn = findViewById(R.id.tvSubmittedOn)
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction)
        tvContactSupport = findViewById(R.id.tvContactSupport)

        tvContactSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@lifelink.app"))
                .putExtra(Intent.EXTRA_SUBJECT, "Organization verification query")
            startActivity(Intent.createChooser(intent, "Contact support"))
        }

        loadStatus()
    }

    private fun loadStatus() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("verificationStatus") ?: "pending"
                tvOrgName.text = doc.getString("org_name") ?: doc.getString("name") ?: "—"
                tvRegNumber.text = doc.getString("reg_number") ?: "—"
                doc.getDate("submittedAt")?.let { tvSubmittedOn.text = dateFormat.format(it) }

                when (status) {
                    "verified" -> showVerified()
                    "rejected" -> showRejected(doc.getString("rejectionReason") ?: "No reason provided.")
                    else -> showPending()
                }
            }
    }

    private fun showPending() {
        ivStatusIcon.setImageResource(R.drawable.ic_clock)
        tvStatusTitle.text = "Verification pending"
        tvStatusDescription.text =
            "We're reviewing your organization's details and documents. This usually takes 2–3 business days."
        rejectionReasonBox.visibility = android.view.View.GONE
        btnPrimaryAction.visibility = android.view.View.GONE
    }

    private fun showVerified() {
        ivStatusIcon.setImageResource(R.drawable.ic_check)
        tvStatusTitle.text = "Registration reviewed"
        tvStatusDescription.text =
            "Your organization is verified. You can now create camps and receive donor applications."
        rejectionReasonBox.visibility = android.view.View.GONE
        btnPrimaryAction.visibility = android.view.View.VISIBLE
        btnPrimaryAction.text = "Go to dashboard"
        btnPrimaryAction.setOnClickListener {
            startActivity(Intent(this, OrgCampListActivity::class.java))
            finish()
        }
    }

    private fun showRejected(reason: String) {
        ivStatusIcon.setImageResource(R.drawable.ic_alert_triangle)
        tvStatusTitle.text = "Verification unsuccessful"
        tvStatusDescription.text =
            "We couldn't verify your organization with the details provided. Review the reason below and resubmit."
        rejectionReasonBox.visibility = android.view.View.VISIBLE
        tvRejectionReason.text = reason
        btnPrimaryAction.visibility = android.view.View.VISIBLE
        btnPrimaryAction.text = "Resubmit application"
        btnPrimaryAction.setOnClickListener {
            startActivity(Intent(this, NgoRegistrationActivity::class.java))
            finish()
        }
    }
}