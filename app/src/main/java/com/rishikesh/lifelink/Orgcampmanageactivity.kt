package com.rishikesh.lifelink

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.rishikesh.lifelink.model.BloodCamp
import com.rishikesh.lifelink.model.CampApplication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class OrgCampManageActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var camp: BloodCamp

    private lateinit var rvPrimary: RecyclerView
    private lateinit var rvSecondary: RecyclerView
    private lateinit var tvNoApplications: TextView
    private lateinit var tabApplications: TextView
    private lateinit var tabDonated: TextView
    private lateinit var dateFilterRow: android.widget.LinearLayout
    private lateinit var btnFromDate: TextView
    private lateinit var btnToDate: TextView
    private lateinit var btnClearDateFilter: TextView
    private lateinit var gestureDetector: GestureDetector

    private var pendingApplications: List<CampApplication> = emptyList()
    private var donatedApplications: List<CampApplication> = emptyList()
    private var showingApplicationsTab = true

    private var fromDateMillis: Long? = null
    private var toDateMillis: Long? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Which physical RecyclerView is currently the visible ("front") one.
    // The other is parked off-screen AND set to GONE, so it can never show
    // through underneath the front view regardless of translationX precision.
    private var frontIsPrimary = true
    private val frontRv: RecyclerView get() = if (frontIsPrimary) rvPrimary else rvSecondary
    private val backRv: RecyclerView get() = if (frontIsPrimary) rvSecondary else rvPrimary

    private var isSwitchingTab = false
    private var isDragging = false

    private val screenWidth: Float by lazy { resources.displayMetrics.widthPixels.toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_org_camp_manage)
        applySystemBarInsets()

        camp = intent.getParcelableExtra("camp") ?: run { finish(); return }

        findViewById<ImageView>(R.id.ivManageCampBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvManageCampTitle).text = camp.campName.ifBlank { "Manage camp" }

        rvPrimary = findViewById(R.id.rvApplicationsPrimary)
        rvSecondary = findViewById(R.id.rvApplicationsSecondary)
        rvPrimary.layoutManager = LinearLayoutManager(this)
        rvSecondary.layoutManager = LinearLayoutManager(this)

        rvSecondary.translationX = screenWidth
        rvSecondary.visibility = View.GONE

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
            renderFront()
        }

        setupSwipeGesture()

        loadApplications()
    }

    // ── Swipe handling ────────────────────────────────────────────────────────

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null || isSwitchingTab) return false

                val totalDeltaX = e2.x - e1.x
                val totalDeltaY = e2.y - e1.y

                if (abs(totalDeltaX) < 24 || abs(totalDeltaX) <= abs(totalDeltaY)) return false

                val draggingLeftPastEnd = totalDeltaX < 0 && !showingApplicationsTab
                val draggingRightPastStart = totalDeltaX > 0 && showingApplicationsTab
                if (draggingLeftPastEnd || draggingRightPastStart) return false

                isDragging = true
                frontRv.parent.requestDisallowInterceptTouchEvent(true)

                val clamped = totalDeltaX.coerceIn(-screenWidth * 0.6f, screenWidth * 0.6f)
                frontRv.translationX = clamped

                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || isSwitchingTab) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) <= abs(deltaY)) return false
                if (abs(deltaX) < 60 && abs(velocityX) < 300) {
                    snapBack()
                    return false
                }

                if (deltaX < 0 && showingApplicationsTab) {
                    selectTab(applications = false)
                    return true
                } else if (deltaX > 0 && !showingApplicationsTab) {
                    selectTab(applications = true)
                    return true
                }

                snapBack()
                return false
            }
        })

        val swipeTouchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (rv !== frontRv || isSwitchingTab) return false
                gestureDetector.onTouchEvent(e)
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    // isSwitchingTab may have just been set true by onFling() above
                    // (on this very event) — if so, don't snap the outgoing view
                    // back, or it cancels the exit animation selectTab() just started.
                    if (isDragging && !isSwitchingTab) snapBack()
                    isDragging = false
                }
                return isDragging
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (rv !== frontRv) return
                gestureDetector.onTouchEvent(e)
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    if (isDragging && !isSwitchingTab) snapBack()
                    isDragging = false
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        }

        rvPrimary.addOnItemTouchListener(swipeTouchListener)
        rvSecondary.addOnItemTouchListener(swipeTouchListener)
    }

    private fun snapBack() {
        frontRv.animate()
            .translationX(0f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ── Tab switching: both views animate together, parked view is GONE ────────

    private fun selectTab(applications: Boolean) {
        if (showingApplicationsTab == applications) {
            snapBack()
            return
        }
        if (isSwitchingTab) return

        showingApplicationsTab = applications
        updateTabColors(applications)
        dateFilterRow.visibility = if (applications) View.GONE else View.VISIBLE

        val incoming = backRv
        val outgoing = frontRv

        incoming.animate().cancel()
        outgoing.animate().cancel()

        val (isEmpty, emptyMessage) = bindTab(incoming, applications)

        isSwitchingTab = true
        tvNoApplications.visibility = View.GONE

        // Donated sits to the right of Applications in the tab bar
        val incomingFromRight = !applications
        val startX = if (incomingFromRight) screenWidth else -screenWidth
        val exitX = if (incomingFromRight) -screenWidth else screenWidth

        incoming.translationX = startX
        incoming.visibility = View.VISIBLE

        incoming.animate()
            .translationX(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()

        outgoing.animate()
            .translationX(exitX)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                outgoing.visibility = View.GONE
                frontIsPrimary = !frontIsPrimary
                isSwitchingTab = false
                tvNoApplications.text = emptyMessage
                tvNoApplications.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
            .start()
    }

    private fun updateTabColors(applications: Boolean) {
        tabApplications.background = getDrawable(if (applications) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabApplications.setTextColor(getColor(if (applications) R.color.coral_800 else R.color.text_secondary))
        tabDonated.background = getDrawable(if (!applications) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabDonated.setTextColor(getColor(if (!applications) R.color.coral_800 else R.color.text_secondary))
    }

    /** Binds the given tab's data into [rv]. Returns (isEmpty, emptyStateMessage). */
    private fun bindTab(rv: RecyclerView, applications: Boolean): Pair<Boolean, String> {
        return if (applications) {
            rv.adapter = CampApplicationAdapter(pendingApplications) { application, position ->
                confirmMarkAsDonated(application, position)
            }
            pendingApplications.isEmpty() to "No pending applications."
        } else {
            val filtered = donatedApplications.filter { app ->
                val time = app.donatedAt?.time ?: return@filter false
                (fromDateMillis == null || time >= fromDateMillis!!) &&
                        (toDateMillis == null || time <= toDateMillis!!)
            }
            rv.adapter = DonatedApplicationAdapter(filtered)
            val message = if (donatedApplications.isEmpty()) "No donations recorded yet." else "No donations in this date range."
            filtered.isEmpty() to message
        }
    }

    private fun renderFront() {
        val (isEmpty, emptyMessage) = bindTab(frontRv, showingApplicationsTab)
        tvNoApplications.text = emptyMessage
        tvNoApplications.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // ── Date filter ──────────────────────────────────────────────────────────

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
                    picked.set(Calendar.HOUR_OF_DAY, 23)
                    picked.set(Calendar.MINUTE, 59)
                    toDateMillis = picked.timeInMillis
                    btnToDate.text = dateFormat.format(picked.time)
                }
                renderFront()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ── Data loading ─────────────────────────────────────────────────────────

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

                renderFront()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load applications", Toast.LENGTH_SHORT).show()
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
                        .addOnFailureListener { e ->
                            android.util.Log.e("DONATION_SYNC", "Failed to add donation history: ${e.message}")
                            Toast.makeText(this, "Marked donated, but couldn't sync history: ${e.message}", Toast.LENGTH_LONG).show()
                        }

                    db.collection("Users").document(application.donorId)
                        .update(
                            mapOf(
                                "totalDonations" to com.google.firebase.firestore.FieldValue.increment(1),
                                "lastDonationDate" to now
                            )
                        )
                        .addOnFailureListener { e ->
                            android.util.Log.e("DONATION_SYNC", "Failed to update donor stats: ${e.message}")
                        }
                }

                Toast.makeText(this, "${application.name} marked as donated", Toast.LENGTH_SHORT).show()
                loadApplications()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't update. Try again.", Toast.LENGTH_SHORT).show()
            }
    }
}