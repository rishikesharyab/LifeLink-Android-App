package com.rishikesh.lifelink

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
            onDecline = { request, position -> respond(request, position, BloodRequest.STATUS_DECLINED) },
            onNavigate = { request -> openInMaps(request) }
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
            .get()
            .addOnSuccessListener { documents ->

                incomingRequests.clear()
                incomingRequests.addAll(documents.mapNotNull { doc ->
                    val status = doc.getString("status") ?: BloodRequest.STATUS_PENDING
                    if (status != BloodRequest.STATUS_PENDING && status != BloodRequest.STATUS_ACCEPTED) {
                        return@mapNotNull null
                    }
                    BloodRequest(
                        id = doc.id,
                        fromUserId = doc.getString("fromUserId") ?: "",
                        fromUserName = doc.getString("fromUserName") ?: "A patient",
                        fromUserPhone = doc.getString("fromUserPhone") ?: "",
                        fromUserLocation = doc.getString("fromUserLocation") ?: "",
                        fromUserLat = doc.getDouble("fromUserLat") ?: 0.0,
                        fromUserLng = doc.getDouble("fromUserLng") ?: 0.0,
                        toUserId = doc.getString("toUserId") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        status = status,
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
        if (newStatus != BloodRequest.STATUS_ACCEPTED) {
            sendResponse(request, position, newStatus, null)
            return
        }

        // Share the number only if the donor allows it (Privacy → Show phone number)
        val uid = auth.currentUser?.uid ?: return
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val visible = doc.getBoolean("phoneVisible") ?: true
                val phone = doc.getString("phone").orEmpty()
                sendResponse(request, position, newStatus, if (visible && phone.isNotBlank()) phone else null)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't respond. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendResponse(request: BloodRequest, position: Int, newStatus: String, sharedPhone: String?) {
        val docId = BloodRequest.docId(request.fromUserId, request.toUserId)

        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "respondedAt" to Timestamp.now()
        )
        if (sharedPhone != null) updates["toUserPhone"] = sharedPhone

        db.collection("BloodRequests").document(docId)
            .update(updates)
            .addOnSuccessListener {
                if (newStatus == BloodRequest.STATUS_ACCEPTED) {
                    incomingRequests[position] = request.copy(status = BloodRequest.STATUS_ACCEPTED)
                    adapter.notifyItemChanged(position)
                } else {
                    incomingRequests.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    if (incomingRequests.isEmpty()) {
                        recyclerView.visibility = android.view.View.GONE
                        tvNoRequests.visibility = android.view.View.VISIBLE
                    }
                }

                val message = when {
                    newStatus != BloodRequest.STATUS_ACCEPTED -> "Request declined"
                    sharedPhone != null -> "Request accepted — ${request.fromUserName} can now see your number"
                    else -> "Request accepted — your number stays hidden"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't respond. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openInMaps(request: BloodRequest) {
        val hasCoords = request.fromUserLat != 0.0 || request.fromUserLng != 0.0

        if (!hasCoords && request.fromUserLocation.isBlank()) {
            Toast.makeText(this, "Patient location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val query = if (hasCoords) "${request.fromUserLat},${request.fromUserLng}"
        else Uri.encode(request.fromUserLocation)

        val geoUri = if (hasCoords)
            Uri.parse("geo:$query?q=$query(${Uri.encode(request.fromUserName)})")
        else
            Uri.parse("geo:0,0?q=$query")

        try {
            startActivity(Intent(Intent.ACTION_VIEW, geoUri).setPackage("com.google.android.apps.maps"))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                )
            )
        }
    }
}