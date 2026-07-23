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
import java.util.Locale
import java.util.Date
import kotlin.math.*

class PatientHomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private val LOCATION_PERMISSION_CODE = 1001

    private lateinit var googleMap: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

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

    // ================= ON CREATE =================


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("TEST_FLOW", "PatientHomeActivity opened")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_home)
        findViewById<View>(R.id.customBottomNav).bringToFront()
        val nav = findViewById<View>(R.id.customBottomNav)
        nav.bringToFront()
        nav.invalidate()
        nav.requestLayout()

        val searchContainerView = findViewById<View>(R.id.searchContainer)
        searchContainerView.bringToFront()
        searchContainerView.invalidate()
        searchContainerView.requestLayout()
        // dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

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

        // ⬆️ Bottom Sheet
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 380
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isDraggable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN


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

            openDonorDashboard()
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

    // ================= LOCATION =================



//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        if (requestCode == LOCATION_PERMISSION_CODE &&
//            grantResults.isNotEmpty() &&
//            grantResults[0] == PackageManager.PERMISSION_GRANTED
//        ) {
//            enablePatientLocation()
//        }
//    }








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
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to send a request", Toast.LENGTH_SHORT).show()
            return
        }

        val docId = BloodRequest.docId(currentUser.uid, donor.id)
        val requestRef = db.collection("BloodRequests").document(docId)

        requestRef.get().addOnSuccessListener { existing ->

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

            db.collection("Users").document(currentUser.uid).get()
                .addOnSuccessListener { userDoc ->

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

                    requestRef.set(requestData)
                        .addOnSuccessListener {
                            requestedDonorIds.add(donor.id)
                            donorAdapter.notifyItemChanged(position)
                            Toast.makeText(this, "Request sent to ${donor.name}", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Couldn't send request. Try again.", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    // ================= OPEN DASHBOARD =================

    private fun openDonorDashboard() {

        val existing = supportFragmentManager.findFragmentByTag("DonorDashboard")

        if (existing is DonorBottomSheetFragment) {
            existing.dismiss()
        }

        if (isDashboardVisible) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return



        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->

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

//                val sheet = DonorBottomSheetFragment.newInstance(donor, currentLocationText)
//                sheet.show(supportFragmentManager, "DonorDashboard")
                val sheet = DonorBottomSheetFragment.newInstance(donor, currentLocationText, userLat, userLng)
                sheet.show(supportFragmentManager, "DonorDashboard")

                isDashboardVisible = true
                isDonorListVisible = false
            }

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
                openDonorDashboard()

            } else {
                Log.d("LOCATION_DEBUG", "Location NULL")
            }
        }
        // 🔥 Refresh dashboard when location ready
        openDonorDashboard()
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

                // For sector, distric and state
//                val fullLocation = when {
//                    sector.isNotEmpty() && city.isNotEmpty() && state.isNotEmpty() ->
//                        "$sector, $city, $state"
//
//                    city.isNotEmpty() && state.isNotEmpty() ->
//                        "$city, $state"
//
//                    else ->
//                        address.getAddressLine(0) ?: "Unknown location"
//                }

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

//                if (ContextCompat.checkSelfPermission(
//                        this,
//                        Manifest.permission.ACCESS_FINE_LOCATION
//                    ) == PackageManager.PERMISSION_GRANTED
//                ) {
//                    googleMap.isMyLocationEnabled = true
//                }

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