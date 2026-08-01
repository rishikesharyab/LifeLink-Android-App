package com.rishikesh.lifelink

import android.content.Intent
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
import com.rishikesh.lifelink.model.BloodCamp

class OrgCampListActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoOrgCamps: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_org_camp_list)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivOrgCampListBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvOrgCamps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoOrgCamps = findViewById(R.id.tvNoOrgCamps)

        loadCamps()
    }

    private fun loadCamps() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("BloodCamps")
            .whereEqualTo("orgId", uid)
            .get()
            .addOnSuccessListener { documents ->

                val camps = documents.map { doc ->
                    BloodCamp(
                        campId = doc.id,
                        campName = doc.getString("campName") ?: "",
                        ngoName = doc.getString("ngoName") ?: "",
                        date = doc.getString("date") ?: "",
                        location = doc.getString("location") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        startTime = doc.getString("startTime") ?: "",
                        endTime = doc.getString("endTime") ?: "",
                        contactName = doc.getString("contact_name") ?: "",
                        designation = doc.getString("designation") ?: "",
                        phone = doc.getString("phone") ?: "",
                        email = doc.getString("email") ?: "",
                        bloodGroupsNeeded = (doc.get("blood_groups_needed") as? List<String>) ?: emptyList(),
                        facilities = (doc.get("facilities") as? List<String>) ?: emptyList(),
                        registeredBy = (doc.get("registeredBy") as? List<String>) ?: emptyList(),
                        orgId = doc.getString("orgId") ?: ""
                    )
                }

                if (camps.isEmpty()) {
                    showEmpty()
                } else {
                    showList()
                    loadApplicationCountsAndRender(camps)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load your camps", Toast.LENGTH_SHORT).show()
                showEmpty()
            }
    }

    /** Fetches pending/donated application counts for each camp before rendering the list. */
    private fun loadApplicationCountsAndRender(camps: List<BloodCamp>) {
        val counts = mutableMapOf<String, Pair<Int, Int>>()
        var remaining = camps.size

        if (remaining == 0) return

        camps.forEach { camp ->
            db.collection("BloodCamps").document(camp.campId)
                .collection("applications")
                .get()
                .addOnSuccessListener { apps ->
                    val donated = apps.count { it.getBoolean("donated") == true }
                    val pending = apps.size() - donated
                    counts[camp.campId] = pending to donated

                    remaining--
                    if (remaining == 0) bindAdapter(camps, counts)
                }
                .addOnFailureListener {
                    counts[camp.campId] = 0 to 0
                    remaining--
                    if (remaining == 0) bindAdapter(camps, counts)
                }
        }
    }

    private fun bindAdapter(camps: List<BloodCamp>, counts: Map<String, Pair<Int, Int>>) {
        recyclerView.adapter = OrgCampAdapter(camps, counts) { camp ->
            val intent = Intent(this, OrgCampManageActivity::class.java)
            intent.putExtra("camp", camp)
            startActivity(intent)
        }
    }

    private fun showEmpty() {
        recyclerView.visibility = android.view.View.GONE
        tvNoOrgCamps.visibility = android.view.View.VISIBLE
    }

    private fun showList() {
        recyclerView.visibility = android.view.View.VISIBLE
        tvNoOrgCamps.visibility = android.view.View.GONE
    }
}