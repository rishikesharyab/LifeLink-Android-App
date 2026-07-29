package com.rishikesh.lifelink

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.BloodCamp
import com.rishikesh.lifelink.model.CampApplication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OrgCampManageActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var camp: BloodCamp

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoApplications: TextView
    private lateinit var tabApplications: TextView
    private lateinit var tabDonated: TextView
    private lateinit var dateFilterRow: android.widget.LinearLayout
    private lateinit var btnFromDate: TextView
    private lateinit var btnToDate: TextView
    private lateinit var btnClearDateFilter: TextView

    private var pendingApplications: List<CampApplication> = emptyList()
    private var donatedApplications: List<CampApplication> = emptyList()
    private var showingApplicationsTab = true

    private var fromDateMillis: Long? = null
    private var toDateMillis: Long? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_org_camp_manage)

        camp = intent.getParcelableExtra("camp") ?: run { finish(); return }

        findViewById<ImageView>(R.id.ivManageCampBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvManageCampTitle).text = camp.campName.ifBlank { "Manage camp" }

        recyclerView = findViewById(R.id.rvApplications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvNoApplications = findViewById(R.id.tvNoApplications)

        tabApplications = findViewById(R.id.tabApplications)
        tabDonated = findViewById(R.id.tabDonated)
        dateFilterRow = findViewById(R.id.dateFilterRow)
        btnFromDate = findViewById(R.id.btnFromDate)
        btnToDate = findViewById(R.id.btnToDate)
        btnClearDateFilter = findViewById(R.id.btnClearDateFilter)

        tabApplications.setOnClickListener { selectTab(applications = true) }
        tabDonated.setOnClickListener { selectTab(applications = false) }

        btnFromDate.setOnClickListener { pickDate(isFrom = true) }
        btnToDate.setOnClickListener { pickDate(isFrom = false) }
        btnClearDateFilter.setOnClickListener {
            fromDateMillis = null
            toDateMillis = null
            btnFromDate.text = "From date"
            btnToDate.text = "To date"
            renderCurrentTab()
        }

        loadApplications()
    }

    private fun selectTab(applications: Boolean) {
        showingApplicationsTab = applications
        tabApplications.background = getDrawable(if (applications) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabApplications.setTextColor(getColor(if (applications) R.color.coral_800 else R.color.text_secondary))
        tabDonated.background = getDrawable(if (!applications) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabDonated.setTextColor(getColor(if (!applications) R.color.coral_800 else R.color.text_secondary))

        dateFilterRow.visibility = if (applications) android.view.View.GONE else android.view.View.VISIBLE

        renderCurrentTab()
    }

    private fun pickDate(isFrom: Boolean) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
                if (isFrom) {
                    fromDateMillis = picked.timeInMillis
                    btnFromDate.text = dateFormat.format(picked.time)
                } else {
                    // include the whole "to" day
                    picked.set(Calendar.HOUR_OF_DAY, 23)
                    picked.set(Calendar.MINUTE, 59)
                    toDateMillis = picked.timeInMillis
                    btnToDate.text = dateFormat.format(picked.time)
                }
                renderCurrentTab()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadApplications() {
        db.collection("BloodCamps").document(camp.campId)
            .collection("applications")
            .get()
            .addOnSuccessListener { documents ->

                val all = documents.map { doc ->
                    CampApplication(
                        id = doc.id,
                        donorId = doc.getString("donorId") ?: "",
                        name = doc.getString("name") ?: "Unknown",
                        age = doc.getLong("age")?.toInt() ?: 0,
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        phone = doc.getString("phone") ?: "",
                        appliedAt = doc.getDate("appliedAt"),
                        donated = doc.getBoolean("donated") ?: false,
                        donatedAt = doc.getDate("donatedAt")
                    )
                }

                pendingApplications = all.filter { !it.donated }
                    .sortedByDescending { it.appliedAt?.time ?: 0L }
                donatedApplications = all.filter { it.donated }
                    .sortedByDescending { it.donatedAt?.time ?: 0L }

                renderCurrentTab()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load applications", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderCurrentTab() {
        if (showingApplicationsTab) {
            if (pendingApplications.isEmpty()) {
                showEmpty("No pending applications.")
            } else {
                showList()
                recyclerView.adapter = CampApplicationAdapter(pendingApplications) { application, position ->
                    confirmMarkAsDonated(application, position)
                }
            }
        } else {
            val filtered = donatedApplications.filter { app ->
                val time = app.donatedAt?.time ?: return@filter false
                (fromDateMillis == null || time >= fromDateMillis!!) &&
                        (toDateMillis == null || time <= toDateMillis!!)
            }

            if (filtered.isEmpty()) {
                showEmpty(
                    if (donatedApplications.isEmpty()) "No donations recorded yet."
                    else "No donations in this date range."
                )
            } else {
                showList()
                recyclerView.adapter = DonatedApplicationAdapter(filtered)
            }
        }
    }

    private fun confirmMarkAsDonated(application: CampApplication, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Mark as donated?")
            .setMessage("This confirms ${application.name} donated blood at this camp. It'll move to the Donated tab.")
            .setPositiveButton("Confirm") { dialog, _ ->
                markAsDonated(application)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun markAsDonated(application: CampApplication) {
        val now = Timestamp.now()

        db.collection("BloodCamps").document(camp.campId)
            .collection("applications").document(application.id)
            .update(
                mapOf(
                    "donated" to true,
                    "donatedAt" to now
                )
            )
            .addOnSuccessListener {

                // Also reflect on the donor's own donation history/stats, same as the
                // patient-side Mark as donated flow in SendRequestActivity
                if (application.donorId.isNotBlank()) {
                    val donationRecord = hashMapOf(
                        "date" to now,
                        "campName" to camp.campName,
                        "location" to camp.location,
                        "bloodGroup" to application.bloodGroup,
                        "unitsDonated" to 1
                    )

                    db.collection("Users").document(application.donorId)
                        .collection("donations")
                        .add(donationRecord)

                    db.collection("Users").document(application.donorId)
                        .update(
                            mapOf(
                                "totalDonations" to com.google.firebase.firestore.FieldValue.increment(1),
                                "lastDonationDate" to now
                            )
                        )
                }

                Toast.makeText(this, "${application.name} marked as donated", Toast.LENGTH_SHORT).show()
                loadApplications()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't update. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmpty(message: String) {
        recyclerView.visibility = android.view.View.GONE
        tvNoApplications.visibility = android.view.View.VISIBLE
        tvNoApplications.text = message
    }

    private fun showList() {
        recyclerView.visibility = android.view.View.VISIBLE
        tvNoApplications.visibility = android.view.View.GONE
    }
}