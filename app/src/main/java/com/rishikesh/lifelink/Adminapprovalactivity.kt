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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.rishikesh.lifelink.model.NgoRegistration
import com.rishikesh.lifelink.util.applySystemBarInsets

class AdminApprovalActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

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

        // Admin = custom claim on the ID token (set with set-admin.js)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            finish()
            return
        }
        user.getIdToken(true)
            .addOnSuccessListener { result ->
                if (result.claims["admin"] == true) showScreen() else finish()
            }
            .addOnFailureListener { finish() }
    }

    private fun showScreen() {
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

                    fun done() {
                        remaining--
                        if (remaining == 0) {
                            orgs = results
                            if (orgs.isEmpty()) showEmpty() else showList()
                        }
                    }

                    fun add(camp: DocumentSnapshot?, verif: DocumentSnapshot?) {
                        results.add(
                            NgoRegistration(
                                id = orgId,
                                uid = orgId,
                                orgName = camp?.getString("ngoName") ?: "Untitled organization",
                                orgType = "",
                                contactName = camp?.getString("contact_name") ?: "",
                                phone = camp?.getString("phone") ?: "",
                                email = camp?.getString("email") ?: "",
                                panNumber = verif?.getString("panNumber") ?: "",
                                ngoDarpanId = verif?.getString("ngoDarpanId") ?: "",
                                certificateUrl = verif?.getString("certificateUrl") ?: "",
                                verificationStatus = status,
                                rejectionReason = rejectionReason
                            )
                        )
                        done()
                    }

                    db.collection("BloodCamps")
                        .whereEqualTo("orgId", orgId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { campDocs ->
                            val camp = campDocs.documents.firstOrNull()
                            db.collection("OrgVerification").document(orgId).get()
                                .addOnSuccessListener { add(camp, it) }
                                .addOnFailureListener { add(camp, null) }
                        }
                        .addOnFailureListener { done() }
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
        if (!isTrustedStorageUrl(org.certificateUrl)) {
            Toast.makeText(this, "Certificate link isn't a Firebase Storage URL", Toast.LENGTH_SHORT).show()
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

    private fun approveOrg(org: NgoRegistration) =
        reviewOrg(org, "verified", "", "${org.orgName} approved", "Couldn't approve. Try again.")

    private fun rejectOrg(org: NgoRegistration, reason: String) =
        reviewOrg(org, "rejected", reason, "${org.orgName} rejected", "Couldn't reject. Try again.")

    // Cloud Function sets Users + BloodCamps verificationStatus; clients can't write them
    private fun reviewOrg(org: NgoRegistration, decision: String, reason: String, okMsg: String, failMsg: String) {
        functions.getHttpsCallable("reviewOrg")
            .call(hashMapOf("orgId" to org.uid, "decision" to decision, "reason" to reason))
            .addOnSuccessListener {
                Toast.makeText(this, okMsg, Toast.LENGTH_SHORT).show()
                loadPendingCount()
                loadOrgs(currentTab)
            }
            .addOnFailureListener {
                Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show()
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