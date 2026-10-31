package com.rishikesh.lifelink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.abs

class SendRequestActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var rvPrimary: RecyclerView
    private lateinit var rvSecondary: RecyclerView
    private lateinit var tvNoRequests: TextView
    private lateinit var tabSent: TextView
    private lateinit var tabAccepted: TextView
    private lateinit var gestureDetector: GestureDetector

    private var sentRequests: List<BloodRequest> = emptyList()
    private var acceptedRequests: List<BloodRequest> = emptyList()
    private var showingSentTab = true

    // Which physical RecyclerView is currently the visible ("front") one.
    // The other is parked off-screen AND set to GONE — so even if translationX
    // math is ever slightly off, the parked view still cannot render or overlap.
    private var frontIsPrimary = true
    private val frontRv: RecyclerView get() = if (frontIsPrimary) rvPrimary else rvSecondary
    private val backRv: RecyclerView get() = if (frontIsPrimary) rvSecondary else rvPrimary

    private var isSwitchingTab = false
    private var isDragging = false

    private val screenWidth: Float by lazy { resources.displayMetrics.widthPixels.toFloat() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_request)
        applySystemBarInsets()

        findViewById<ImageView>(R.id.ivSendRequestBack).setOnClickListener { finish() }

        rvPrimary = findViewById(R.id.rvRequestsPrimary)
        rvSecondary = findViewById(R.id.rvRequestsSecondary)
        rvPrimary.layoutManager = LinearLayoutManager(this)
        rvSecondary.layoutManager = LinearLayoutManager(this)

        // Park the back view off-screen AND hide it — belt and braces so it
        // can never visually overlap the front view, regardless of translation state.
        rvSecondary.translationX = screenWidth
        rvSecondary.visibility = View.GONE

        tvNoRequests = findViewById(R.id.tvNoRequests)

        tabSent = findViewById(R.id.tabSent)
        tabAccepted = findViewById(R.id.tabAccepted)

        tabSent.setOnClickListener { selectTab(sent = true) }
        tabAccepted.setOnClickListener { selectTab(sent = false) }

        setupSwipeGesture()

        loadRequests()
    }

    // ── Swipe handling ────────────────────────────────────────────────────────

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            // Live-follow the finger so dragging feels tracked, not just detected after the fact
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

                val draggingLeftPastEnd = totalDeltaX < 0 && !showingSentTab
                val draggingRightPastStart = totalDeltaX > 0 && showingSentTab
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

                if (deltaX < 0 && showingSentTab) {
                    selectTab(sent = false)
                    return true
                } else if (deltaX > 0 && !showingSentTab) {
                    selectTab(sent = true)
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
                    // isSwitchingTab may have just been set to true by onFling()
                    // above (on this very event) if the swipe committed to a tab
                    // switch — in that case we must NOT snap the outgoing view
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

    private fun selectTab(sent: Boolean) {
        if (showingSentTab == sent) {
            snapBack()
            return
        }
        if (isSwitchingTab) return

        showingSentTab = sent
        updateTabColors(sent)

        val incoming = backRv
        val outgoing = frontRv

        // Cancel any stray in-flight animations before starting fresh ones,
        // so a previous interrupted transition can't leave stale end-state.
        incoming.animate().cancel()
        outgoing.animate().cancel()

        val isEmpty = bindTab(incoming, sent)

        isSwitchingTab = true
        tvNoRequests.visibility = View.GONE

        // Accepted sits to the right of Sent in the tab bar
        val incomingFromRight = !sent
        val startX = if (incomingFromRight) screenWidth else -screenWidth
        val exitX = if (incomingFromRight) -screenWidth else screenWidth

        // Make the incoming view visible and positioned off-screen BEFORE
        // it starts animating in — it must never be GONE while translating.
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
                // Hide the outgoing view completely once parked — this is what
                // guarantees it can never show through underneath the new tab,
                // regardless of translationX precision.
                outgoing.visibility = View.GONE
                frontIsPrimary = !frontIsPrimary
                isSwitchingTab = false
                tvNoRequests.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
            .start()
    }

    private fun updateTabColors(sent: Boolean) {
        tabSent.background = getDrawable(if (sent) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabSent.setTextColor(getColor(if (sent) R.color.coral_800 else R.color.text_secondary))
        tabAccepted.background = getDrawable(if (!sent) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        tabAccepted.setTextColor(getColor(if (!sent) R.color.coral_800 else R.color.text_secondary))
    }

    /** Binds the given tab's data into [rv]. Returns true if that tab's list is empty. */
    private fun bindTab(rv: RecyclerView, sent: Boolean): Boolean {
        return if (sent) {
            rv.adapter = SentRequestAdapter(sentRequests) { request, position ->
                resendRequest(request, position)
            }
            sentRequests.isEmpty()
        } else {
            rv.adapter = AcceptedRequestAdapter(
                acceptedRequests,
                onCallClick = { request ->
                    if (request.toUserPhone.isBlank()) {
                        Toast.makeText(this, "Phone hidden by donor", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${request.toUserPhone}")))
                    }
                },
                onMarkDonatedClick = { request, position ->
                    confirmMarkAsDonated(request, position)
                }
            )
            acceptedRequests.isEmpty()
        }
    }

    private fun renderFront() {
        val isEmpty = bindTab(frontRv, showingSentTab)
        tvNoRequests.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // ── Data loading ─────────────────────────────────────────────────────────

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
                        toUserPhone = doc.getString("toUserPhone") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "",
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        status = doc.getString("status") ?: BloodRequest.STATUS_PENDING,
                        donated = doc.getBoolean("donated") ?: false,
                        createdAt = doc.getDate("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.time ?: 0L }

                sentRequests = all.filter { it.status != BloodRequest.STATUS_ACCEPTED }
                acceptedRequests = all.filter { it.status == BloodRequest.STATUS_ACCEPTED }

                renderFront()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load requests", Toast.LENGTH_SHORT).show()
            }
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

    // Only sets donated/donatedAt. A Cloud Function writes the donation history and donor stats.
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
                acceptedRequests = acceptedRequests.map {
                    if (it.id == request.id) it.copy(donated = true) else it
                }
                Toast.makeText(this, "${request.toUserName} marked as donated", Toast.LENGTH_SHORT).show()
                renderFront()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Couldn't update. Try again. (${e.message})", Toast.LENGTH_SHORT).show()
            }
    }
}