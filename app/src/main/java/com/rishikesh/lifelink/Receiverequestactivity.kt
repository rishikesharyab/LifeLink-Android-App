package com.rishikesh.lifelink

import android.os.Bundle
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

class ReceiveRequestActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRequests: TextView

    private val incomingRequests = mutableListOf<BloodRequest>()
    private lateinit var adapter: IncomingRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive_request)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivReceiveRequestBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvIncomingRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoRequests = findViewById(R.id.tvNoIncomingRequests)

        adapter = IncomingRequestAdapter(
            incomingRequests,
            onAccept = { request, position -> respond(request, position, BloodRequest.STATUS_ACCEPTED) },
            onDecline = { request, position -> respond(request, position, BloodRequest.STATUS_DECLINED) }
        )
        recyclerView.adapter = adapter

        loadIncomingRequests()
    }

    private fun loadIncomingRequests() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("BloodRequests")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("status", BloodRequest.STATUS_PENDING)
            .get()
            .addOnSuccessListener { documents ->

                incomingRequests.clear()
                incomingRequests.addAll(documents.map { doc ->
                    BloodRequest(
                        id = doc.id,
                        fromUserId = doc.getString("fromUserId") ?: "",
                        fromUserName = doc.getString("fromUserName") ?: "A patient",
                        toUserId = doc.getString("toUserId") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        status = doc.getString("status") ?: BloodRequest.STATUS_PENDING,
                        createdAt = doc.getDate("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.time ?: 0L })

                adapter.notifyDataSetChanged()

                if (incomingRequests.isEmpty()) {
                    recyclerView.visibility = android.view.View.GONE
                    tvNoRequests.visibility = android.view.View.VISIBLE
                } else {
                    recyclerView.visibility = android.view.View.VISIBLE
                    tvNoRequests.visibility = android.view.View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load requests", Toast.LENGTH_SHORT).show()
            }
    }

    private fun respond(request: BloodRequest, position: Int, newStatus: String) {
        val docId = BloodRequest.docId(request.fromUserId, request.toUserId)

        db.collection("BloodRequests").document(docId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "respondedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                incomingRequests.removeAt(position)
                adapter.notifyItemRemoved(position)

                if (incomingRequests.isEmpty()) {
                    recyclerView.visibility = android.view.View.GONE
                    tvNoRequests.visibility = android.view.View.VISIBLE
                }

                val message = if (newStatus == BloodRequest.STATUS_ACCEPTED)
                    "Request accepted — ${request.fromUserName} can now see your number"
                else
                    "Request declined"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't respond. Try again.", Toast.LENGTH_SHORT).show()
            }
    }
}