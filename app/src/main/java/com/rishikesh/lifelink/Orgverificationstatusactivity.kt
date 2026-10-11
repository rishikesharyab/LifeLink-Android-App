package com.rishikesh.lifelink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rishikesh.lifelink.util.applySystemBarInsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrgVerificationStatusActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var statusListener: ListenerRegistration? = null

    private lateinit var statusIconBadge: FrameLayout
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

    private lateinit var tvCampName: TextView

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
        tvCampName = findViewById(R.id.tvCampName)
        tvRegNumber = findViewById(R.id.tvRegNumber)
        tvSubmittedOn = findViewById(R.id.tvSubmittedOn)
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction)
        tvContactSupport = findViewById(R.id.tvContactSupport)

        tvContactSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@lifelink.app"))
                .putExtra(Intent.EXTRA_SUBJECT, "Organization verification query")
            startActivity(Intent.createChooser(intent, "Contact support"))
        }

        listenToStatus()
    }

    // Live status: updates automatically when admin approves/rejects
    private fun listenToStatus() {
        val uid = auth.currentUser?.uid ?: return

        statusListener = db.collection("Users").document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                when (doc.getString("verificationStatus") ?: "pending") {
                    "verified" -> showVerified()
                    "rejected" -> showRejected(doc.getString("rejectionReason") ?: "No reason provided.")
                    else -> showPending()
                }
                loadDetails(uid, doc)
            }
    }

    // Submitted details: read from the org's original BloodCamps doc
    private fun loadDetails(uid: String, userDoc: DocumentSnapshot) {
        db.collection("BloodCamps").whereEqualTo("orgId", uid).get()
            .addOnSuccessListener { snap ->
                // Newest camp first
                val camps = snap.documents.sortedByDescending { it.getLong("endTimeMillis") ?: 0L }

                fun pick(vararg keys: String): String? =
                    camps.firstNotNullOfOrNull { c ->
                        keys.firstNotNullOfOrNull { k -> c.getString(k)?.trim()?.takeIf { it.isNotEmpty() } }
                    }

                tvOrgName.text = pick("ngoName")
                    ?: userDoc.getString("org_name")?.takeIf { it.isNotBlank() } ?: "—"

                tvCampName.text = pick("campName") ?: "—"

                tvRegNumber.text = pick("regNumber", "reg_number")
                    ?: userDoc.getString("reg_number")?.takeIf { it.isNotBlank() } ?: "—"

                val newest = camps.firstOrNull()
                val legacy = newest?.getLong("endTimeMillis")
                    ?.takeIf { it > 0 }
                    ?.let { Date(it - 30L * 24 * 60 * 60 * 1000) }

                val submitted = newest?.getDate("submittedAt") ?: userDoc.getDate("submittedAt") ?: legacy
                tvSubmittedOn.text = submitted?.let { dateFormat.format(it) } ?: "—"
            }
            .addOnFailureListener {
                tvOrgName.text = userDoc.getString("org_name") ?: "—"
                tvCampName.text = "—"
                tvRegNumber.text = userDoc.getString("reg_number") ?: "—"
                tvSubmittedOn.text = userDoc.getDate("submittedAt")?.let { dateFormat.format(it) } ?: "—"
            }
    }

    private fun showPending() {
        ivStatusIcon.setImageResource(R.drawable.ic_clock)
        tvStatusTitle.text = "Verification pending"
        tvStatusDescription.text =
            "We're reviewing your organization's details and documents. This usually takes 2–3 business days."
        rejectionReasonBox.visibility = View.GONE
        btnPrimaryAction.visibility = View.GONE
    }

    private fun showVerified() {
        ivStatusIcon.setImageResource(R.drawable.ic_check)
        tvStatusTitle.text = "Registration reviewed"
        tvStatusDescription.text =
            "Your organization is verified. You can now create camps and receive donor applications."
        rejectionReasonBox.visibility = View.GONE
        btnPrimaryAction.visibility = View.VISIBLE
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
        rejectionReasonBox.visibility = View.VISIBLE
        tvRejectionReason.text = reason
        btnPrimaryAction.visibility = View.VISIBLE
        btnPrimaryAction.text = "Resubmit application"
        btnPrimaryAction.setOnClickListener {
            startActivity(Intent(this, NgoRegistrationActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        statusListener?.remove()
        super.onDestroy()
    }
}