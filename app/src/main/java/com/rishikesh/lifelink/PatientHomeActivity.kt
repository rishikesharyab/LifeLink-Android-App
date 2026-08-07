package com.rishikesh.lifelink



import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.rishikesh.lifelink.model.Donor
import com.rishikesh.lifelink.model.BloodRequest
import com.rishikesh.lifelink.util.applyDynamicStatusBar
import java.util.Locale
import java.util.Date
import kotlin.math.*

class PatientHomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private val LOCATION_PERMISSION_CODE = 1001

    private lateinit var googleMap: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var dashboardBottomSheetBehavior: BottomSheetBehavior<androidx.core.widget.NestedScrollView>

    private lateinit var donorRecyclerView: RecyclerView
    private lateinit var tvNoDonors: TextView
    private lateinit var donorAdapter: DonorAdapter
    private val donorList = mutableListOf<Donor>()
    private val requestedDonorIds = mutableSetOf<String>()

    private val db = FirebaseFirestore.getInstance()

    private var patientLat = 0.0
    private var patientLng = 0.0

    private var userLat = 0.0
    private var userLng = 0.0

    private var donorDashboardShown = false
    var isDashboardVisible = false
    private var isDonorListVisible = false
    private var isSearchExpanded = false

    private lateinit var etSearch: EditText
    private lateinit var ivClearSearch: ImageView
    private lateinit var ivSearchIcon: ImageView
    private lateinit var ivBackSearch: ImageView
    private lateinit var bloodGroupScroll: View
    private lateinit var chipGroup: ChipGroup

    private var currentLocationText: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ================= DONOR DASHBOARD (in-Activity bottom sheet) =================

    private lateinit var tvAvatar: TextView
    private lateinit var tvDonorName: TextView
    private lateinit var tvDonorLocation: EditText
    private lateinit var tvBloodGroup: TextView
    private lateinit var tvTotalDonations: TextView
    private lateinit var tvLivesSaved: TextView
    private lateinit var tvBadgeTitle: TextView
    private lateinit var tvBadgeSub: TextView
    private lateinit var tvLastDonation: TextView
    private lateinit var tvNextEligible: TextView
    private lateinit var tvAvailabilitySubtitle: TextView
    private lateinit var switchAvailability: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var tvSendRequestSummary: TextView
    private lateinit var tvReceiveRequestSummary: TextView

    private lateinit var llCampCard: LinearLayout
    private lateinit var llDotIndicators: LinearLayout
    private lateinit var tvNoCamps: TextView
    private lateinit var tvCampName: TextView
    private lateinit var tvNgoName: TextView
    private lateinit var tvCampDate: TextView
    private lateinit var tvCampLocation: TextView
    private lateinit var tvCampTime: TextView
    private lateinit var tvCampDistance: TextView
    private lateinit var tvCampIndicator: TextView
    private lateinit var btnRegisterCamp: TextView

    private val nearbyCamps = mutableListOf<com.rishikesh.lifelink.model.BloodCamp>()
    private var currentCampIndex = 0
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val CAROUSEL_DELAY = 4000L
    private val MAX_DISTANCE_KM = 10.0
    private val DONATION_INTERVAL_MONTHS = 3

    // ================= ON CREATE =================


    override fun onCreate(savedInstanceState: Bundle?) {
        applyDynamicStatusBar(isLightBackground = true)
        Log.d("TEST_FLOW", "PatientHomeActivity opened")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)

        val nav = findViewById<View>(R.id.customBottomNav)
        nav.bringToFront()
        nav.invalidate()
        nav.requestLayout()

        val searchContainerView = findViewById<View>(R.id.searchContainer)
        searchContainerView.bringToFront()
        searchContainerView.invalidate()
        searchContainerView.requestLayout()

        // Handle Status Bar Overlap (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(searchContainerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Base margin is 8dp (was 12dp earlier)
                topMargin = systemBars.top + (6 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        // Cap the dashboard sheet's height so it never grows past the search bar's
        // real bottom edge, once it's actually measured. maxHeight works regardless
        // of fitToContents (unlike expandedOffset, which is silently ignored when
        // fitToContents=true) — under fitToContents=true this correctly means "size
        // to content normally, but cap + scroll internally if content exceeds it."
        searchContainerView.post {
            val gap = (90 * resources.displayMetrics.density).toInt()
            val availableHeight = resources.displayMetrics.heightPixels - searchContainerView.bottom - gap
            dashboardBottomSheetBehavior.maxHeight = availableHeight
        }

        // ⬆️ Bottom Sheet (donor search results) — must be initialized before
        // getCurrentLocation() below, since it triggers openDonorDashboard()
        // which touches dashboardBottomSheetBehavior.
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 380
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isDraggable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // ⬆️ Bottom Sheet (donor dashboard — replaces DonorBottomSheetFragment dialog)
        val dashboardSheet = findViewById<androidx.core.widget.NestedScrollView>(R.id.donorDashboardSheet)
        dashboardBottomSheetBehavior = BottomSheetBehavior.from(dashboardSheet)
        dashboardBottomSheetBehavior.isHideable = true
        dashboardBottomSheetBehavior.isDraggable = true
        // fitToContents=true (default) sizes the sheet to its actual content height,
        // avoiding the maxHeight/halfExpandedRatio measurement issues that caused
        // both the earlier background gap and this scroll-lock. peekHeight is the
        // "60% open" state; expandedOffset caps how far up full-drag can go.
        dashboardBottomSheetBehavior.peekHeight = (resources.displayMetrics.heightPixels * 0.6).toInt()
        // expandedOffset is set below via searchContainerView.post{}, once its real
        // measured bottom position is known — a fixed 25% guess didn't account for
        // the search bar's actual height/position, which varies with status bar inset.
        dashboardBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        dashboardBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                isDashboardVisible = newState != BottomSheetBehavior.STATE_HIDDEN
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    carouselHandler.removeCallbacksAndMessages(null)
                }
            }
            override fun onSlide(sheet: View, slideOffset: Float) {}
        })

        bindDashboardViews(dashboardSheet)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        Log.d("TEST_FLOW", "Calling location function")
        getCurrentLocation()



        onBackPressedDispatcher.addCallback(this) {

            when {

                // If donor list is open → it's the topmost view, so close it first
                isDonorListVisible -> {

                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    isDonorListVisible = false

                    openDonorDashboard()   // show dashboard again
                }

                // If search is expanded → collapse it and show the home dashboard
                isSearchExpanded || bloodGroupScroll.visibility == View.VISIBLE -> {
                    collapseSearch()
                    openDonorDashboard()
                }

                // If dashboard visible → exit app
                isDashboardVisible -> {

                    finish()
                }

                else -> finish()
            }
        }

        // 🔍 SEARCH BAR + BLOOD GROUP CHIPS
        val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        chipGroup = findViewById(R.id.chipGroupBloodGroups)
        etSearch = findViewById(R.id.etSearch)
        ivClearSearch = findViewById(R.id.ivClearSearch)
        ivSearchIcon = findViewById(R.id.ivSearchIcon)
        ivBackSearch = findViewById(R.id.ivBackSearch)
        bloodGroupScroll = findViewById(R.id.bloodGroupScroll)

        var isSyncingFromChip = false
        var isSyncingFromText = false

        // Explicitly force focus + keyboard on tap. Relying only on the implicit
        // touch→focus path was flaky here because the full-screen map fragment
        // sits underneath and sometimes wins the first touch event.
        val focusAndShowKeyboard = View.OnClickListener {
            etSearch.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        searchContainerView.setOnClickListener(focusAndShowKeyboard)
        etSearch.setOnClickListener(focusAndShowKeyboard)

        ivBackSearch.setOnClickListener {
            collapseSearch()
            openDonorDashboard()
        }

        bloodGroups.forEach { group ->
            val chip = Chip(this).apply {
                text        = group
                isCheckable = true
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        resources.getColor(R.color.coral_50, null),
                        resources.getColor(R.color.surface_secondary, null)
                    )
                )
                setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(
                            resources.getColor(R.color.coral_800, null),
                            resources.getColor(R.color.text_secondary, null)
                        )
                    )
                )
            }
            chipGroup.addView(chip)
        }

        // Chip tapped → reflect it in the search text and run the search
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val chip = group.findViewById<Chip>(checkedIds[0])
            val group_ = chip.text.toString()

            if (!isSyncingFromText) {
                isSyncingFromChip = true
                etSearch.setText(group_)
                etSearch.setSelection(etSearch.text.length)
                isSyncingFromChip = false
            }

            // Close the keyboard as soon as a filter is picked — the chip row stays open
            etSearch.clearFocus()
            hideKeyboard(etSearch)

            searchDonors(group_)
        }

        // Typing a blood group → auto-select the matching chip
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                if (isSyncingFromChip) return

                val query = s?.toString()?.trim().orEmpty()
                ivClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                val normalized = normalizeBloodGroupQuery(query)
                val matchedGroup = bloodGroups.firstOrNull { it.equals(normalized, ignoreCase = true) }

                isSyncingFromText = true
                if (matchedGroup != null) {
                    for (i in 0 until chipGroup.childCount) {
                        val chip = chipGroup.getChildAt(i) as Chip
                        chip.isChecked = chip.text.toString() == matchedGroup
                    }
                } else {
                    chipGroup.clearCheck()
                }
                isSyncingFromText = false
            }
        })

        // Focus → expand the chip row and swap the icon to a back arrow.
        // Blur → swap icon back; only collapse the row if no chip is selected.
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isSearchExpanded = true
                ivSearchIcon.visibility = View.GONE
                ivBackSearch.visibility = View.VISIBLE

                bloodGroupScroll.visibility = View.VISIBLE
                bloodGroupScroll.alpha = 0f
                bloodGroupScroll.animate().alpha(1f).setDuration(150).start()

                // Tapping the search bar should get the dashboard out of the way
                dashboardBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                if (chipGroup.checkedChipId == View.NO_ID) {
                    // Nothing selected — fully collapse and go back to the plain search icon
                    collapseSearch()
                } else {
                    // A filter is still active — keep the row expanded and the back arrow showing
                    hideKeyboard(etSearch)
                }
            }
        }

        // Clear button → reset text, chip selection, and results
        ivClearSearch.setOnClickListener {
            etSearch.setText("")
            chipGroup.clearCheck()
            ivClearSearch.visibility = View.GONE
        }

        // 📋 RecyclerView
        donorRecyclerView = findViewById(R.id.donorRecyclerView)
        donorRecyclerView.layoutManager = LinearLayoutManager(this)
        tvNoDonors = findViewById(R.id.tvNoDonors)

        donorAdapter = DonorAdapter(
            donorList,
            requestedDonorIds,
            onItemClick = { donor ->
                val latLng = LatLng(donor.latitude, donor.longitude)
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                )
            },
            onRequestClick = { donor, position ->
                sendBloodRequest(donor, position)
            }
        )

        donorRecyclerView.adapter = donorAdapter

        // 🗺️ Map
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.patientMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Bottom Navigation Bar
        val bottomNav = findViewById<View>(R.id.customBottomNav)
        bottomNav.bringToFront()
        bottomNav.invalidate()
        bottomNav.requestLayout()

        val navHome = findViewById<LinearLayout>(R.id.navHome)
        val navDonate = findViewById<LinearLayout>(R.id.navDonate)

// Inflate layout manually
        val homeView = layoutInflater.inflate(R.layout.layout_bottom_nav_item, navHome, true)
        val donateView = layoutInflater.inflate(R.layout.layout_bottom_nav_item, navDonate, true)

        homeView.findViewById<ImageView>(R.id.iconDefault).setImageResource(R.drawable.ic_home)
        homeView.findViewById<TextView>(R.id.title).text = "Home"

        donateView.findViewById<ImageView>(R.id.iconDefault).setImageResource(R.drawable.outline_donor_heart)
        donateView.findViewById<TextView>(R.id.title).text = "Donate"
        val homeIcon = homeView.findViewById<ImageView>(R.id.iconDefault)
        val donateIcon = donateView.findViewById<ImageView>(R.id.iconDefault)

        val homeText = homeView.findViewById<TextView>(R.id.title)
        val donateText = donateView.findViewById<TextView>(R.id.title)


        // 👉 Default selected
        selectTab(navHome, navDonate, homeIcon, donateIcon, homeText, donateText)

        // 👉 Click listeners
        navHome.setOnClickListener {

            selectTab(navHome, navDonate, homeIcon, donateIcon, homeText, donateText)

            val dashboardOpen = dashboardBottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED ||
                    dashboardBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED

            if (dashboardOpen) {
                dashboardBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                openDonorDashboard()
            }
        }

        navDonate.setOnClickListener {

            selectTab(navDonate, navHome, homeIcon, donateIcon, homeText, donateText)

            checkDonorRegistration()
        }


    }

    // ================= MAP READY =================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false

//        openDonorDashboard()
//
//        enablePatientLocation()


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }

        // If the location callback already fired before the map was ready, catch up now
        if (userLat != 0.0 || userLng != 0.0) {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLng), 14f)
            )
        }

        // 🔍 Search action

    }

    //Bottom Nav bar
    private fun selectTab(
        selected: LinearLayout,
        other: LinearLayout,
        homeIcon: ImageView,
        donateIcon: ImageView,
        homeText: TextView,
        donateText: TextView
    ) {

        if (selected.id == R.id.navHome) {

            homeIcon.setImageResource(R.drawable.ic_home) // active
            donateIcon.setImageResource(R.drawable.outline_donor_heart)

            homeText.setTextColor(getColor(R.color.black))
            donateText.setTextColor(getColor(R.color.gray))

        } else {

            homeIcon.setImageResource(R.drawable.ic_home)
            donateIcon.setImageResource(R.drawable.outline_donor_heart)

            homeText.setTextColor(getColor(R.color.gray))
            donateText.setTextColor(getColor(R.color.black))
        }
    }

    // ================= SEARCH DONORS =================

    private fun searchDonors(bloodGroup: String) {

        Log.d("SEARCH_DEBUG", "Searching for: $bloodGroup")


        donorList.clear()

        if (userLat == 0.0 || userLng == 0.0) {
            Toast.makeText(this, "Fetching location, try again...", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Users")
            .whereEqualTo("bloodGroup", bloodGroup)
            .get()
            .addOnSuccessListener { documents ->

                Log.d("SEARCH_DEBUG", "Docs size: ${documents.size()}")

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid

                for (doc in documents) {

                    if (doc.id == currentUid) continue // don't show yourself in your own search

                    val isAvailable = doc.getBoolean("available") ?: true
                    if (!isAvailable) continue // skip donors who turned availability off

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val distance = distanceInKm(userLat, userLng, lat, lng)

                    val donor = Donor(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unknown",
                        location = doc.getString("location") ?: "",
                        bloodGroup = doc.getString("bloodGroup") ?: "N/A",
                        phone = doc.getString("phone") ?: "N/A",
                        latitude = lat,
                        longitude = lng,
                        distanceKm = distance
                    )

                    donorList.add(donor)

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(donor.name)
                    )
                }

                donorAdapter.notifyDataSetChanged()
                refreshRequestedState()

                if (donorList.isEmpty()) {
                    donorRecyclerView.visibility = View.GONE
                    tvNoDonors.visibility = View.VISIBLE
                } else {
                    donorRecyclerView.visibility = View.VISIBLE
                    tvNoDonors.visibility = View.GONE
                }

                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                isDonorListVisible = true
                isDashboardVisible = false
            }
    }


    // ================= BLOOD REQUESTS =================

    /**
     * Marks donors as "Requested" (disabled button) if there's a still-pending
     * request within the 3-minute cooldown window, or if it's already been
     * accepted. Anything past the cooldown reverts to a resendable "Request" state.
     *
     * This only patches request-button state on top of an already-rendered list —
     * it must never gate whether the donor list itself shows up, since this query
     * can fail independently (e.g. missing Firestore security rules for
     * BloodRequests) without that being a reason to hide search results.
     */
    private fun refreshRequestedState() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("BloodRequests")
            .whereEqualTo("fromUserId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->

                requestedDonorIds.clear()
                val now = Date().time

                for (doc in documents) {
                    val toUserId = doc.getString("toUserId") ?: continue
                    val status = doc.getString("status") ?: BloodRequest.STATUS_PENDING
                    val createdAt = doc.getDate("createdAt")

                    val stillCoolingDown = createdAt != null &&
                            (now - createdAt.time) < BloodRequest.RESEND_COOLDOWN_MS

                    if (status == BloodRequest.STATUS_ACCEPTED || stillCoolingDown) {
                        requestedDonorIds.add(toUserId)
                    }
                }

                donorAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Log.w("SEARCH_DEBUG", "Couldn't refresh request state (donor list still shows)", it)
            }
    }

    private fun sendBloodRequest(donor: Donor, position: Int) {
        Log.d("REQUEST_DEBUG", "sendBloodRequest called for donor: ${donor.name}")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to send a request", Toast.LENGTH_SHORT).show()
            return
        }

        val docId = BloodRequest.docId(currentUser.uid, donor.id)
        val requestRef = db.collection("BloodRequests").document(docId)

        requestRef.get()
            .addOnSuccessListener { existing ->
                Log.d("REQUEST_DEBUG", "Existing request check: ${existing.exists()}")

                val status = existing.getString("status")
                val createdAt = existing.getDate("createdAt")
                val now = Date().time

                if (status == BloodRequest.STATUS_ACCEPTED) {
                    Toast.makeText(this, "${donor.name} already accepted your request", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                if (createdAt != null && (now - createdAt.time) < BloodRequest.RESEND_COOLDOWN_MS) {
                    val remainingSec = (BloodRequest.RESEND_COOLDOWN_MS - (now - createdAt.time)) / 1000
                    Toast.makeText(this, "You can resend in ${remainingSec}s", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                Log.d("REQUEST_DEBUG", "Fetching user info for UID: ${currentUser.uid}")
                db.collection("Users").document(currentUser.uid).get()
                    .addOnSuccessListener { userDoc ->
                        if (!userDoc.exists()) {
                            Log.e("REQUEST_DEBUG", "User document not found")
                            Toast.makeText(this, "User profile not found. Complete your profile.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val fromName = userDoc.getString("name") ?: "A patient"
                        val fromPhone = userDoc.getString("phone") ?: ""
                        val fromLocation = userDoc.getString("location") ?: ""

                        val requestData = hashMapOf(
                            "fromUserId" to currentUser.uid,
                            "fromUserName" to fromName,
                            "fromUserPhone" to fromPhone,
                            "fromUserLocation" to fromLocation,
                            "toUserId" to donor.id,
                            "toUserName" to donor.name,
                            "toUserLocation" to donor.location,
                            "bloodGroup" to donor.bloodGroup,
                            "distanceKm" to donor.distanceKm,
                            "status" to BloodRequest.STATUS_PENDING,
                            "createdAt" to Timestamp.now()
                        )

                        Log.d("REQUEST_DEBUG", "Saving request to Firestore...")
                        requestRef.set(requestData)
                            .addOnSuccessListener {
                                Log.d("REQUEST_DEBUG", "Request saved successfully")
                                requestedDonorIds.add(donor.id)
                                donorAdapter.notifyItemChanged(position)
                                Toast.makeText(this, "Request sent to ${donor.name}", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("REQUEST_DEBUG", "Firestore SET failed", e)
                                Toast.makeText(this, "Couldn't send request: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("REQUEST_DEBUG", "User lookup failed", e)
                        Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("REQUEST_DEBUG", "Initial request lookup failed", e)
                Toast.makeText(this, "Request failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ================= OPEN DASHBOARD =================

    private fun openDonorDashboard() {
        if (dashboardBottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED ||
            dashboardBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        ) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val donor = Donor(
                    id = uid,
                    name = doc.getString("name") ?: "",
                    location = doc.getString("location") ?: "",
                    bloodGroup = doc.getString("bloodGroup") ?: "",
                    phone = doc.getString("phone") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    distanceKm = 0.0,
                    totalDonations = doc.getLong("totalDonations")?.toInt() ?: 0,
                    lastDonationDate = doc.getDate("lastDonationDate"),
                    isAvailable = doc.getBoolean("available") ?: true
                )

                tvDonorLocation.setText(currentLocationText)
                populateDashboardUi(donor)
                loadUpcomingCamps(userLat, userLng)
                loadRequestSummaries()
                checkOrganizationStatus()

                // Opens at 60% (peekHeight) — dragging up takes it to the capped
                // expanded state (expandedOffset stops it below the search bar);
                // if content still overflows that, it scrolls internally.
                dashboardBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                isDonorListVisible = false
            }
    }

    // ── Bind donor-dashboard views (called once, in onCreate) ──────────────────

    private fun bindDashboardViews(root: View) {
        tvAvatar               = root.findViewById(R.id.tvAvatar)
        tvDonorName            = root.findViewById(R.id.tvDonorName)
        tvDonorLocation        = root.findViewById(R.id.locationEt)
        tvBloodGroup           = root.findViewById(R.id.tvBloodGroup)
        tvTotalDonations       = root.findViewById(R.id.tvTotalDonations)
        tvLivesSaved           = root.findViewById(R.id.tvLivesSaved)
        tvBadgeTitle           = root.findViewById(R.id.tvBadgeTitle)
        tvBadgeSub             = root.findViewById(R.id.tvBadgeSub)
        tvLastDonation         = root.findViewById(R.id.tvLastDonation)
        tvNextEligible         = root.findViewById(R.id.tvNextEligible)
        tvAvailabilitySubtitle = root.findViewById(R.id.tvAvailabilitySubtitle)
        switchAvailability     = root.findViewById(R.id.switchAvailability)

        llCampCard      = root.findViewById(R.id.llCampCard)
        llDotIndicators = root.findViewById(R.id.llDotIndicators)
        tvNoCamps       = root.findViewById(R.id.tvNoCamps)
        tvCampName      = root.findViewById(R.id.tvCampName)
        tvNgoName       = root.findViewById(R.id.tvNgoName)
        tvCampDate      = root.findViewById(R.id.tvCampDate)
        tvCampLocation  = root.findViewById(R.id.tvCampLocation)
        tvCampTime      = root.findViewById(R.id.tvCampTime)
        tvCampDistance  = root.findViewById(R.id.tvCampDistance)
        tvCampIndicator = root.findViewById(R.id.tvCampIndicator)
        btnRegisterCamp = root.findViewById(R.id.btnRegisterCamp)

        tvSendRequestSummary    = root.findViewById(R.id.tvSendRequestSummary)
        tvReceiveRequestSummary = root.findViewById(R.id.tvReceiveRequestSummary)

        root.findViewById<TextView>(R.id.btnNgoRegister).setOnClickListener {
            startActivity(Intent(this, NgoRegistrationActivity::class.java))
        }
        tvAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        root.findViewById<LinearLayout>(R.id.cardSendRequest).setOnClickListener {
            startActivity(Intent(this, SendRequestActivity::class.java))
        }
        root.findViewById<LinearLayout>(R.id.cardReceiveRequest).setOnClickListener {
            startActivity(Intent(this, ReceiveRequestActivity::class.java))
        }
        root.findViewById<TextView>(R.id.btnManageCamps).setOnClickListener {
            startActivity(Intent(this, OrgCampListActivity::class.java))
        }
    }

    private fun populateDashboardUi(donor: Donor) {
        tvAvatar.text        = donor.initials()
        tvDonorName.text     = donor.name
        tvBloodGroup.text    = donor.bloodGroup

        tvTotalDonations.text = donor.totalDonations.toString()
        tvLivesSaved.text     = (donor.totalDonations * 3).toString()

        val badge = Badge.from(donor.totalDonations)
        tvBadgeTitle.text = badge.title
        tvBadgeSub.text   = badge.subtitle

        val format = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        if (donor.lastDonationDate != null) {
            tvLastDonation.text = format.format(donor.lastDonationDate)
            tvNextEligible.text = format.format(
                java.util.Calendar.getInstance().apply {
                    time = donor.lastDonationDate
                    add(java.util.Calendar.MONTH, DONATION_INTERVAL_MONTHS)
                }.time
            )
        } else {
            tvLastDonation.text = "—"
            tvNextEligible.text = "Now"
        }

        switchAvailability.isChecked = donor.isAvailable
        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            tvAvailabilitySubtitle.text =
                if (isChecked) "Visible to nearby requests" else "Hidden from nearby requests"

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnCheckedChangeListener
            db.collection("Users").document(uid)
                .update("available", isChecked)
                .addOnFailureListener {
                    Toast.makeText(this, "Couldn't update availability. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun checkOrganizationStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val isOrganization = doc.getBoolean("isOrganization") ?: false
                findViewById<LinearLayout>(R.id.llManageCampsCard).visibility =
                    if (isOrganization) View.VISIBLE else View.GONE
            }
    }

    private fun loadRequestSummaries() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("BloodRequests")
            .whereEqualTo("fromUserId", uid)
            .get()
            .addOnSuccessListener { documents ->
                var pending = 0
                var accepted = 0
                for (doc in documents) {
                    when (doc.getString("status")) {
                        "pending" -> pending++
                        "accepted" -> accepted++
                    }
                }

                tvSendRequestSummary.text = when {
                    pending == 0 && accepted == 0 -> "No requests yet"
                    else -> "$pending pending · $accepted accepted"
                }
            }

        db.collection("BloodRequests")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                tvReceiveRequestSummary.text =
                    if (count == 0) "No new requests" else "$count new request${if (count == 1) "" else "s"}"
            }
    }

    private fun loadUpcomingCamps(userLat: Double, userLng: Double) {
        db.collection("BloodCamps")
            .get()
            .addOnSuccessListener { documents ->

                nearbyCamps.clear()

                for (doc in documents) {
                    val campLat = doc.getDouble("latitude")  ?: continue
                    val campLng = doc.getDouble("longitude") ?: continue

                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        userLat, userLng, campLat, campLng, results
                    )
                    val distKm = results[0] / 1000.0

                    if (distKm > MAX_DISTANCE_KM) continue

                    nearbyCamps.add(
                        com.rishikesh.lifelink.model.BloodCamp(
                            campId            = doc.id,
                            campName          = doc.getString("campName")          ?: "",
                            ngoName           = doc.getString("ngoName")           ?: "",
                            date              = doc.getString("date")              ?: "",
                            location          = doc.getString("location")          ?: "",
                            latitude          = campLat,
                            longitude         = campLng,
                            startTime         = doc.getString("startTime")         ?: "",
                            endTime           = doc.getString("endTime")           ?: "",
                            endTimeMillis     = doc.getLong("endTimeMillis")       ?: 0L,
                            distanceKm        = distKm,
                            contactName       = doc.getString("contact_name")      ?: "",
                            designation       = doc.getString("designation")       ?: "",
                            phone             = doc.getString("phone")             ?: "",
                            email             = doc.getString("email")             ?: "",
                            bloodGroupsNeeded = (doc.get("blood_groups_needed") as? List<String>) ?: emptyList(),
                            facilities        = (doc.get("facilities")            as? List<String>) ?: emptyList(),
                            registeredBy      = (doc.get("registeredBy")          as? List<String>) ?: emptyList()
                        )
                    )
                }

                if (nearbyCamps.isEmpty()) {
                    llCampCard.visibility = View.GONE
                    tvNoCamps.visibility  = View.VISIBLE
                } else {
                    llCampCard.visibility = View.VISIBLE
                    tvNoCamps.visibility  = View.GONE
                    buildDots()
                    showCamp(0, userLat, userLng)
                    if (nearbyCamps.size > 1) startCarousel(userLat, userLng)
                }
            }
            .addOnFailureListener {
                llCampCard.visibility = View.GONE
                tvNoCamps.visibility  = View.VISIBLE
            }
    }

    private fun showCamp(index: Int, userLat: Double, userLng: Double) {
        val camp = nearbyCamps[index]

        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng, camp.latitude, camp.longitude, results
        )
        val distKm = results[0] / 1000.0

        llCampCard.animate().alpha(0f).setDuration(250).withEndAction {

            tvCampName.text      = camp.campName
            tvNgoName.text       = camp.ngoName
            tvCampDate.text      = camp.date
            tvCampLocation.text  = camp.location
            tvCampTime.text      = "${camp.startTime} – ${camp.endTime}"
            tvCampDistance.text  = "%.1f km".format(distKm)
            tvCampIndicator.text = "${index + 1} / ${nearbyCamps.size}"

            updateDots(index)

            btnRegisterCamp.setOnClickListener {
                val intent = Intent(this, CampDetailActivity::class.java)
                intent.putExtra("camp", camp)
                startActivity(intent)
            }

            llCampCard.animate().alpha(1f).setDuration(250).start()

        }.start()
    }

    private fun buildDots() {
        llDotIndicators.removeAllViews()
        val dp = resources.displayMetrics.density

        nearbyCamps.forEachIndexed { i, _ ->
            val dot = View(this)
            val params = LinearLayout.LayoutParams(
                if (i == 0) (18 * dp).toInt() else (7 * dp).toInt(),
                (7 * dp).toInt()
            ).apply { marginEnd = (6 * dp).toInt() }
            dot.layoutParams = params
            dot.background = if (i == 0)
                resources.getDrawable(R.drawable.bg_dot_active, null)
            else
                resources.getDrawable(R.drawable.bg_dot_inactive, null)
            dot.tag = "dot_$i"
            llDotIndicators.addView(dot)
        }
    }

    private fun updateDots(activeIndex: Int) {
        val dp = resources.displayMetrics.density

        for (i in 0 until llDotIndicators.childCount) {
            val dot    = llDotIndicators.getChildAt(i)
            val params = dot.layoutParams as LinearLayout.LayoutParams
            if (i == activeIndex) {
                params.width = (18 * dp).toInt()
                dot.background = resources.getDrawable(R.drawable.bg_dot_active, null)
            } else {
                params.width = (7 * dp).toInt()
                dot.background = resources.getDrawable(R.drawable.bg_dot_inactive, null)
            }
            dot.layoutParams = params
        }
    }

    private fun startCarousel(userLat: Double, userLng: Double) {
        carouselHandler.removeCallbacksAndMessages(null)

        val runnable = object : Runnable {
            override fun run() {
                currentCampIndex = (currentCampIndex + 1) % nearbyCamps.size
                showCamp(currentCampIndex, userLat, userLng)
                carouselHandler.postDelayed(this, CAROUSEL_DELAY)
            }
        }

        carouselHandler.postDelayed(runnable, CAROUSEL_DELAY)
    }


    private fun getCurrentLocation() {

        Handler(Looper.getMainLooper()).postDelayed({

            if (::googleMap.isInitialized) {
                val latLng = LatLng(userLat, userLng)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
            }

        }, 500)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->

            if (location != null) {

                userLat = location.latitude
                userLng = location.longitude

                Log.d("LOCATION_DEBUG", "Lat: $userLat")

                // Move map to correct location — only if the map has finished loading
                if (::googleMap.isInitialized) {
                    val latLng = LatLng(userLat, userLng)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                }

                getAddressFromLatLng(userLat, userLng)
            } else {
                Log.d("LOCATION_DEBUG", "Location NULL")
            }

            // 🔥 Only open the dashboard once location fetch has resolved
            openDonorDashboard()
        }.addOnFailureListener {
            Log.e("LOCATION_DEBUG", "getCurrentLocation failed", it)
            // Still open the dashboard even if location couldn't be fetched
            openDonorDashboard()
        }
    }


    private fun getAddressFromLatLng(lat: Double, lng: Double) {

        val geocoder = Geocoder(this, Locale.getDefault())

        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {

                val address = addresses[0]

                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""
                val sector = address.subLocality ?: ""
                // For Sector and distric
                val fullLocation = when {
                    sector.isNotEmpty() -> "$sector, $city"
                    city.isNotEmpty() -> "$city, $state"
                    else -> address.getAddressLine(0) ?: "Unknown location"
                }

                currentLocationText = fullLocation
                // 🔥 IMPORTANT: THIS updates UI

                Log.d("LOCATION_DEBUG", "Address: $fullLocation")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)



        if (requestCode == 1001) {

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.d("LOCATION_DEBUG", "Permission Granted by user")

                // 🔥 CALL AGAIN AFTER PERMISSION
                getCurrentLocation()

            } else {
                Log.d("LOCATION_DEBUG", "Permission Denied")
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            }

        }
    }



    // ================= SEARCH COLLAPSE =================

    /** Collapses the chip row, clears focus/keyboard, and restores the search icon. */
    private fun collapseSearch() {
        etSearch.clearFocus()
        hideKeyboard(etSearch)
        ivBackSearch.visibility = View.GONE
        ivSearchIcon.visibility = View.VISIBLE
        isSearchExpanded = false

        bloodGroupScroll.animate().alpha(0f).setDuration(150)
            .withEndAction { bloodGroupScroll.visibility = View.GONE }
            .start()
    }

    // ================= KEYBOARD =================

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // ================= SEARCH TEXT NORMALIZATION =================

    /**
     * Lets the user type "b+", "B Positive", "b pos", "o-", "O Negative" etc.
     * and still match the canonical "B+" / "O-" chip labels.
     */
    private fun normalizeBloodGroupQuery(raw: String): String {
        var q = raw.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return ""

        q = q.replace("blood group", "")
            .replace("group", "")
            .replace("type", "")
            .trim()

        val isNegative = q.contains("-") || q.contains("neg")
        val isPositive = q.contains("+") || q.contains("pos")

        val letters = q.replace(Regex("[^ab]"), "")
        val group = when {
            letters.contains("a") && letters.contains("b") -> "AB"
            letters.contains("a") -> "A"
            letters.contains("b") -> "B"
            q.startsWith("o") -> "O"
            else -> return ""
        }

        val sign = when {
            isPositive -> "+"
            isNegative -> "-"
            else -> return ""
        }

        return "$group$sign"
    }

    // ================= DISTANCE =================

    private fun distanceInKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    fun resetDashboardState() {
        donorDashboardShown = false
    }

    // ================= CHECK DONOR REGISTRATION =================
    // Bottom navigation Bar
    private fun checkDonorRegistration() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->

                val isProfileComplete = doc.getBoolean("profileCompleted") == true

                if (isProfileComplete) {

                    // ✅ Already donor
                    startActivity(
                        Intent(this, AlreadyDonorActivity::class.java)
                    )

                } else {

                    // ❌ Not registered
                    startActivity(
                        Intent(this, CompleteDonorProfileActivity::class.java)
                    )
                }
            }
    }
}

// ================= BADGE LOGIC =================
// (moved here from the deleted DonorBottomSheetFragment.kt)

enum class Badge(val title: String, val subtitle: String) {
    NEW_HERO("New", "Hero"),
    RISING_HERO("Rising", "Hero"),
    SUPER_HERO("Super", "Hero"),
    LEGEND("Blood", "Legend"),
    CHAMPION("Life", "Champion");

    companion object {
        fun from(totalDonations: Int): Badge = when {
            totalDonations == 0 -> NEW_HERO
            totalDonations < 3  -> RISING_HERO
            totalDonations < 8  -> SUPER_HERO
            totalDonations < 15 -> LEGEND
            else                -> CHAMPION
        }
    }
}