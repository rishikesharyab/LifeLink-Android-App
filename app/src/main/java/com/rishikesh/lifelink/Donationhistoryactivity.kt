package com.rishikesh.lifelink

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rishikesh.lifelink.model.DonationRecord

class DonationHistoryActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoHistory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donation_history)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivHistoryBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvDonationHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoHistory = findViewById(R.id.tvNoHistory)

        loadHistory()
    }

    private fun loadHistory() {
        val user = auth.currentUser
        if (user == null) {
            showEmpty()
            return
        }

        // Stored as a subcollection: Users/{uid}/donations/{donationId}
        // Each doc: { date: Timestamp, campName: String, location: String,
        //             bloodGroup: String, unitsDonated: Long }
        db.collection("Users")
            .document(user.uid)
            .collection("donations")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->

                val records = documents.map { doc ->
                    DonationRecord(
                        id = doc.id,
                        date = doc.getDate("date"),
                        campName = doc.getString("campName") ?: "",
                        location = doc.getString("location") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        unitsDonated = doc.getLong("unitsDonated")?.toInt() ?: 1,
                        receiverName = doc.getString("receiverName") ?: "",
                        receiverLocation = doc.getString("receiverLocation") ?: ""
                    )
                }

                if (records.isEmpty()) {
                    showEmpty()
                } else {
                    recyclerView.visibility = android.view.View.VISIBLE
                    tvNoHistory.visibility = android.view.View.GONE
                    recyclerView.adapter = DonationHistoryAdapter(records)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load donation history", Toast.LENGTH_SHORT).show()
                showEmpty()
            }
    }

    private fun showEmpty() {
        recyclerView.visibility = android.view.View.GONE
        tvNoHistory.visibility = android.view.View.VISIBLE
    }
}