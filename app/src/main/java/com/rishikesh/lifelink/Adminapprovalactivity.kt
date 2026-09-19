package com.rishikesh.lifelink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rishikesh.lifelink.model.NgoRegistration
import com.rishikesh.lifelink.util.applySystemBarInsets

class AdminApprovalActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoOrgs: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tabPending: TextView
    private lateinit var tabVerified: TextView
    private lateinit var tabRejected: TextView

    private var currentTab = "pending"
    private var orgs: List<NgoRegistration> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid !in AdminConfig.ADMIN_UIDS) {
            finish()
            return
        }

        setContentView(R.layout.activity_admin_approval)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivAdminBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvOrgs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoOrgs = findViewById(R.id.tvNoOrgs)
        tvPendingCount = findViewById(R.id.tvPendingCount)

        tabPending = findViewById(R.id.tabPending)
        tabVerified = findViewById(R.id.tabVerified)
        tabRejected = findViewById(R.id.tabRejected)

        tabPending.setOnClickListener { selectTab("pending") }
        tabVerified.setOnClickListener { selectTab("verified") }
        tabRejected.setOnClickListener { selectTab("rejected") }

        loadPendingCount()
        selectTab("pending")
    }

    private fun selectTab(status: String) {
        currentTab = status

        tabPending.background = getDrawable(if (status == "pending") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabPending.setTextColor(getColor(if (status == "pending") R.color.coral_800 else R.color.text_secondary))

        tabVerified.background = getDrawable(if (status == "verified") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabVerified.setTextColor(getColor(if (status == "verified") R.color.coral_800 else R.color.text_secondary))

        tabRejected.background = getDrawable(if (status == "rejected") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabRejected.setTextColor(getColor(if (status == "rejected") R.color.coral_800 else R.color.text_secondary))

        loadOrgs(status)
    }

    private fun loadPendingCount() {
        db.collection("Users")
            .whereEqualTo("isOrganization", true)
            .whereEqualTo("verificationStatus", "pending")
            .get()
            .addOnSuccessListener { tvPendingCount.text = "${it.size()} pending" }
    }

    /**
     * Verification status lives on Users/{orgId} (set at registration time).
     * Org identity fields (PAN, certificate, contact info) live on the
     * BloodCamps doc that registration also creates, keyed by orgId.
     * So we fetch the Users docs for the status filter, then look up each
     * org's most recent BloodCamps doc to fill in the display fields —
     * same two-step fetch pattern as OrgCampListActivity.
     */
    private fun loadOrgs(status: String) {
        db.collection("Users")
            .whereEqualTo("isOrganization", true)
            .whereEqualTo("verificationStatus", status)
            .get()
            .addOnSuccessListener { userDocs ->

                if (userDocs.isEmpty) {
                    orgs = emptyList()
                    showEmpty()
                    return@addOnSuccessListener
                }

                val results = mutableListOf<NgoRegistration>()
                var remaining = userDocs.size()

                userDocs.forEach { userDoc ->
                    val orgId = userDoc.id
                    val rejectionReason = userDoc.getString("rejectionReason") ?: ""

                    db.collection("BloodCamps")
                        .whereEqualTo("orgId", orgId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { campDocs ->
                            val camp = campDocs.documents.firstOrNull()

                            results.add(
                                NgoRegistration(
                                    id = orgId,
                                    uid = orgId,
                                    orgName = camp?.getString("ngoName") ?: "Untitled organization",
                                    orgType = "",
                                    contactName = camp?.getString("contact_name") ?: "",
                                    phone = camp?.getString("phone") ?: "",
                                    email = camp?.getString("email") ?: "",
                                    panNumber = camp?.getString("panNumber") ?: "",
                                    ngoDarpanId = "",
                                    certificateUrl = camp?.getString("certificateUrl") ?: "",
                                    verificationStatus = status,
                                    rejectionReason = rejectionReason
                                )
                            )

                            remaining--
                            if (remaining == 0) {
                                orgs = results
                                if (orgs.isEmpty()) showEmpty() else showList()
                            }
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining == 0) {
                                orgs = results
                                if (orgs.isEmpty()) showEmpty() else showList()
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load organizations", Toast.LENGTH_SHORT).show()
                showEmpty()
            }
    }

    private fun bindAdapter() {
        recyclerView.adapter = AdminOrgAdapter(
            items = orgs,
            showActions = currentTab == "pending",
            onApprove = { org -> confirmApprove(org) },
            onReject = { org -> promptRejectReason(org) },
            onViewCertificate = { org -> openCertificate(org) },
            onCardClick = { org -> OrgDetailSheet.show(this, org.uid, org.orgName) }
        )
    }

    private fun openCertificate(org: NgoRegistration) {
        if (org.certificateUrl.isBlank()) {
            Toast.makeText(this, "No certificate uploaded", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(org.certificateUrl)))
    }

    private fun confirmApprove(org: NgoRegistration) {
        AlertDialog.Builder(this)
            .setTitle("Approve ${org.orgName}?")
            .setMessage("This marks the registration as reviewed and grants organization access.")
            .setPositiveButton("Approve") { dialog, _ ->
                approveOrg(org)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun approveOrg(org: NgoRegistration) {
        db.collection("Users").document(org.uid)
            .set(
                mapOf(
                    "verificationStatus" to "verified",
                    "verifiedAt" to com.google.firebase.Timestamp.now(),
                    "rejectionReason" to ""
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                syncCampStatus(org.uid, "verified")
                Toast.makeText(this, "${org.orgName} approved", Toast.LENGTH_SHORT).show()
                loadPendingCount()
                loadOrgs(currentTab)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't approve. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * An org can have multiple BloodCamps docs (one per camp created).
     * Verification is per-org, so every camp belonging to this orgId
     * needs its own verificationStatus field updated to match — that's
     * the field donor-facing camp listings should filter on.
     */
    private fun syncCampStatus(orgId: String, status: String) {
        db.collection("BloodCamps")
            .whereEqualTo("orgId", orgId)
            .get()
            .addOnSuccessListener { campDocs ->
                campDocs.documents.forEach { camp ->
                    db.collection("BloodCamps").document(camp.id)
                        .update("verificationStatus", status)
                }
            }
    }

    private fun promptRejectReason(org: NgoRegistration) {
        val input = EditText(this).apply {
            hint = "Reason for rejection"
        }

        AlertDialog.Builder(this)
            .setTitle("Reject ${org.orgName}?")
            .setView(input)
            .setPositiveButton("Reject") { dialog, _ ->
                val reason = input.text.toString().trim()
                rejectOrg(org, reason)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun rejectOrg(org: NgoRegistration, reason: String) {
        db.collection("Users").document(org.uid)
            .set(
                mapOf(
                    "verificationStatus" to "rejected",
                    "rejectionReason" to reason
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                syncCampStatus(org.uid, "rejected")
                Toast.makeText(this, "${org.orgName} rejected", Toast.LENGTH_SHORT).show()
                loadPendingCount()
                loadOrgs(currentTab)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't reject. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmpty() {
        recyclerView.visibility = android.view.View.GONE
        tvNoOrgs.visibility = android.view.View.VISIBLE
        tvNoOrgs.text = when (currentTab) {
            "pending" -> "No pending registrations."
            "verified" -> "No verified organizations yet."
            else -> "No rejected registrations."
        }
    }

    private fun showList() {
        recyclerView.visibility = android.view.View.VISIBLE
        tvNoOrgs.visibility = android.view.View.GONE
        bindAdapter()
    }
}