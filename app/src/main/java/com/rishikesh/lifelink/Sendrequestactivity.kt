package com.rishikesh.lifelink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.BloodRequest

class SendRequestActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRequests: TextView
    private lateinit var tabSent: TextView
    private lateinit var tabAccepted: TextView

    private var sentRequests: List<BloodRequest> = emptyList()
    private var acceptedRequests: List<BloodRequest> = emptyList()
    private var showingSentTab = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_request)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivSendRequestBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoRequests = findViewById(R.id.tvNoRequests)

        tabSent = findViewById(R.id.tabSent)
        tabAccepted = findViewById(R.id.tabAccepted)

        tabSent.setOnClickListener { selectTab(sent = true) }
        tabAccepted.setOnClickListener { selectTab(sent = false) }

        loadRequests()
    }

    private fun selectTab(sent: Boolean) {
        showingSentTab = sent
        tabSent.background = getDrawable(if (sent) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabSent.setTextColor(getColor(if (sent) R.color.coral_800 else R.color.text_secondary))
        tabAccepted.background = getDrawable(if (!sent) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabAccepted.setTextColor(getColor(if (!sent) R.color.coral_800 else R.color.text_secondary))
        renderCurrentTab()
    }

    private fun loadRequests() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("BloodRequests")
            .whereEqualTo("fromUserId", uid)
            .get()
            .addOnSuccessListener { documents ->

                val all = documents.map { doc ->
                    BloodRequest(
                        id = doc.id,
                        fromUserId = doc.getString("fromUserId") ?: "",
                        fromUserName = doc.getString("fromUserName") ?: "",
                        fromUserLocation = doc.getString("fromUserLocation") ?: "",
                        toUserId = doc.getString("toUserId") ?: "",
                        toUserName = doc.getString("toUserName") ?: "Unknown donor",
                        toUserLocation = doc.getString("toUserLocation") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        status = doc.getString("status") ?: BloodRequest.STATUS_PENDING,
                        donated = doc.getBoolean("donated") ?: false,
                        createdAt = doc.getDate("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.time ?: 0L }

                sentRequests = all.filter { it.status != BloodRequest.STATUS_ACCEPTED }
                acceptedRequests = all.filter { it.status == BloodRequest.STATUS_ACCEPTED }

                renderCurrentTab()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load requests", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderCurrentTab() {
        if (showingSentTab) {
            if (sentRequests.isEmpty()) {
                showEmpty()
            } else {
                showList()
                recyclerView.adapter = SentRequestAdapter(sentRequests) { request, position ->
                    resendRequest(request, position)
                }
            }
        } else {
            if (acceptedRequests.isEmpty()) {
                showEmpty()
            } else {
                showList()
                loadDonorPhonesAndRenderAccepted()
            }
        }
    }

    /** Accepted tab needs a live phone lookup — fetched fresh rather than trusting a stored copy. */
    private fun loadDonorPhonesAndRenderAccepted() {
        val donorIds = acceptedRequests.map { it.toUserId }.distinct()
        if (donorIds.isEmpty()) return

        val phones = mutableMapOf<String, String>()
        var remaining = donorIds.size

        donorIds.forEach { donorId ->
            db.collection("Users").document(donorId).get()
                .addOnSuccessListener { doc ->
                    phones[donorId] = doc.getString("phone") ?: "Phone unavailable"
                    remaining--
                    if (remaining == 0) bindAcceptedAdapter(phones)
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining == 0) bindAcceptedAdapter(phones)
                }
        }
    }

    private fun bindAcceptedAdapter(phones: Map<String, String>) {
        recyclerView.adapter = AcceptedRequestAdapter(
            acceptedRequests,
            phones,
            onCallClick = { request ->
                val number = phones[request.toUserId]
                if (number.isNullOrBlank() || number == "Phone unavailable") {
                    Toast.makeText(this, "Phone number unavailable", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                }
            },
            onMarkDonatedClick = { request, position ->
                confirmMarkAsDonated(request, position)
            }
        )
    }

    private fun confirmMarkAsDonated(request: BloodRequest, position: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mark as donated?")
            .setMessage("This confirms ${request.toUserName} donated blood for you. " +
                    "It'll be added to their donation history and can't be undone from here.")
            .setPositiveButton("Confirm") { dialog, _ ->
                markAsDonated(request, position)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun resendRequest(request: BloodRequest, position: Int) {
        val docId = BloodRequest.docId(request.fromUserId, request.toUserId)

        db.collection("BloodRequests").document(docId)
            .update(
                mapOf(
                    "status" to BloodRequest.STATUS_PENDING,
                    "createdAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Request resent to ${request.toUserName}", Toast.LENGTH_SHORT).show()
                loadRequests()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't resend. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Marks a donor as having donated for this request. This does three things:
     * 1. Flags the BloodRequests doc as donated (so the button stays "Donated ✓")
     * 2. Adds a record to Users/{donorId}/donations — the same subcollection
     *    DonationHistoryActivity reads from, so it shows up on the donor's account
     * 3. Increments the donor's totalDonations and updates lastDonationDate on
     *    their Users doc, so their profile stats/badge and next-eligible-date
     *    calculations stay accurate
     */
    private fun markAsDonated(request: BloodRequest, position: Int) {
        val docId = BloodRequest.docId(request.fromUserId, request.toUserId)
        val now = Timestamp.now()

        db.collection("BloodRequests").document(docId)
            .update(
                mapOf(
                    "donated" to true,
                    "donatedAt" to now
                )
            )
            .addOnSuccessListener {

                // Add to the donor's donation history
                val donationRecord = hashMapOf(
                    "date" to now,
                    "campName" to "Direct request",
                    "location" to request.fromUserLocation,
                    "bloodGroup" to request.bloodGroup,
                    "unitsDonated" to 1,
                    "receiverName" to request.fromUserName,
                    "receiverLocation" to request.fromUserLocation
                )

                db.collection("Users")
                    .document(request.toUserId)
                    .collection("donations")
                    .add(donationRecord)
                    .addOnSuccessListener {
                        Log.d("DONATION_DEBUG", "Donation record added for ${request.toUserId}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("DONATION_DEBUG", "Failed to add donation record", e)
                        Toast.makeText(
                            this,
                            "Marked donated, but couldn't save to their history: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                // Bump the donor's stats
                db.collection("Users")
                    .document(request.toUserId)
                    .update(
                        mapOf(
                            "totalDonations" to com.google.firebase.firestore.FieldValue.increment(1),
                            "lastDonationDate" to now
                        )
                    )
                    .addOnFailureListener { e ->
                        Log.e("DONATION_DEBUG", "Failed to update donor stats", e)
                    }

                acceptedRequests = acceptedRequests.map {
                    if (it.id == request.id) it.copy(donated = true) else it
                }
                Toast.makeText(this, "${request.toUserName} marked as donated", Toast.LENGTH_SHORT).show()
                renderCurrentTab()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Couldn't update. Try again. (${e.message})", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmpty() {
        recyclerView.visibility = android.view.View.GONE
        tvNoRequests.visibility = android.view.View.VISIBLE
    }

    private fun showList() {
        recyclerView.visibility = android.view.View.VISIBLE
        tvNoRequests.visibility = android.view.View.GONE
    }
}