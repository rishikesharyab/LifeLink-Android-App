package com.rishikesh.lifelink

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.rishikesh.lifelink.R
import com.rishikesh.lifelink.model.Donor
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.jvm.java
import com.rishikesh.lifelink.model.BloodCamp

class DonorBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_DONOR = "arg_donor"
        private const val ARG_LOCATION = "arg_location"

        private const val DONATION_INTERVAL_MONTHS = 3

        // ✅ FIXED newInstance (NOW ACCEPTS LOCATION)
        fun newInstance(donor: Donor, location: String, userLat: Double = 0.0, userLng: Double = 0.0): DonorBottomSheetFragment {
            return DonorBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_DONOR, donor)
                    putString(ARG_LOCATION, location)
                    putDouble("userLat", userLat)
                    putDouble("userLng", userLng)
                }
            }
        }
    }


    private val nearbyCamps      = mutableListOf<BloodCamp>()
    private var currentCampIndex = 0
    private val carouselHandler  = android.os.Handler(android.os.Looper.getMainLooper())
    private val CAROUSEL_DELAY   = 4000L
    private val MAX_DISTANCE_KM  = 10.0


    // Views
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
    private lateinit var switchAvailability: SwitchMaterial

    private lateinit var llCampCard       : LinearLayout
    private lateinit var llCampGradient   : LinearLayout
    private lateinit var llDotIndicators  : LinearLayout
    private lateinit var tvNoCamps        : TextView
    private lateinit var tvCampName       : TextView
    private lateinit var tvNgoName        : TextView
    private lateinit var tvCampDate       : TextView
    private lateinit var tvCampLocation   : TextView
    private lateinit var tvCampTime       : TextView
    private lateinit var tvCampDistance   : TextView
    private lateinit var tvCampIndicator  : TextView
    private lateinit var btnRegisterCamp  : TextView



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_donor_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)

        // ✅ GET LOCATION FROM ARGUMENT
        val location = arguments?.getString(ARG_LOCATION) ?: ""
        tvDonorLocation.setText(location)

        val donor = arguments?.getParcelable<Donor>(ARG_DONOR)
        if (donor != null) populateUi(donor) else dismiss()

        val userLat = arguments?.getDouble("userLat") ?: 0.0
        val userLng = arguments?.getDouble("userLng") ?: 0.0
        loadUpcomingCamps(userLat, userLng)
    }

    private fun bindViews(root: View) {
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
        llCampGradient  = root.findViewById(R.id.llCampGradientHeader)
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

        root.findViewById<TextView>(R.id.btnNgoRegister).setOnClickListener {
            startActivity(Intent(requireContext(), NgoRegistrationActivity::class.java))
        }
    }

    private fun populateUi(donor: Donor) {
        tvAvatar.text        = donor.initials()
        tvDonorName.text     = donor.name
        tvBloodGroup.text    = donor.bloodGroup

        tvTotalDonations.text = donor.totalDonations.toString()
        tvLivesSaved.text     = (donor.totalDonations * 3).toString()

        val badge = Badge.from(donor.totalDonations)
        tvBadgeTitle.text = badge.title
        tvBadgeSub.text   = badge.subtitle

        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        if (donor.lastDonationDate != null) {
            tvLastDonation.text = format.format(donor.lastDonationDate)
            tvNextEligible.text = format.format(nextEligibleDate(donor.lastDonationDate))
        } else {
            tvLastDonation.text = "—"
            tvNextEligible.text = "Now"
        }

        switchAvailability.isChecked = donor.isAvailable
        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            onAvailabilityChanged(isChecked)
        }
    }

    override fun onStart() {
        super.onStart()

        val window = dialog?.window ?: return
        val params = window.attributes
        val screenHeight = resources.displayMetrics.heightPixels

        params.height = (screenHeight * 0.87).toInt()
        window.attributes = params

        window.setDimAmount(0f)
    }

    private fun nextEligibleDate(lastDonation: java.util.Date): java.util.Date {
        return Calendar.getInstance().apply {
            time = lastDonation
            add(Calendar.MONTH, DONATION_INTERVAL_MONTHS)
        }.time
    }

    private fun onAvailabilityChanged(isAvailable: Boolean) {
        tvAvailabilitySubtitle.text =
            if (isAvailable) "Visible to nearby requests"
            else "Hidden from nearby requests"
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? PatientHomeActivity)?.isDashboardVisible = false
    }

    // ── 5. Add onDestroyView() to stop carousel when sheet closes

    override fun onDestroyView() {
        super.onDestroyView()
        carouselHandler.removeCallbacksAndMessages(null)
    }

    // ── 6. Main camp loading function
    private fun loadUpcomingCamps(userLat: Double, userLng: Double) {
        val db  = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        android.util.Log.d("CAMP_DEBUG", "Fetching camps... userLat=$userLat userLng=$userLng")

        db.collection("BloodCamps")
            .get()
            .addOnSuccessListener { documents ->

                android.util.Log.d("CAMP_DEBUG", "Total docs found: ${documents.size()}")

                nearbyCamps.clear()

                for (doc in documents) {
                    android.util.Log.d("CAMP_DEBUG", "Doc: ${doc.id} data: ${doc.data}")

                    val campLat = doc.getDouble("latitude")  ?: continue
                    val campLng = doc.getDouble("longitude") ?: continue

                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        userLat, userLng, campLat, campLng, results
                    )
                    val distKm = results[0] / 1000.0

                    android.util.Log.d("CAMP_DEBUG", "Camp: ${doc.getString("campName")} distKm=$distKm")

                    if (distKm > MAX_DISTANCE_KM) {
                        android.util.Log.d("CAMP_DEBUG", "Skipped — too far: $distKm km")
                        continue
                    }

                    nearbyCamps.add(
                        BloodCamp(
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

                android.util.Log.d("CAMP_DEBUG", "Nearby camps count: ${nearbyCamps.size}")

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
            .addOnFailureListener { e ->
                android.util.Log.e("CAMP_DEBUG", "Failed to fetch camps: ${e.message}")
                llCampCard.visibility = View.GONE
                tvNoCamps.visibility  = View.VISIBLE
            }
    }

    // ── 7. Show a single camp with fade animation

    private fun showCamp(index: Int, userLat: Double, userLng: Double) {
        val camp = nearbyCamps[index]

        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng, camp.latitude, camp.longitude, results
        )
        val distKm = results[0] / 1000.0

        // Fade out
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
                val intent = android.content.Intent(requireContext(), CampDetailActivity::class.java)
                intent.putExtra("camp", camp)
                startActivity(intent)
            }

            // Fade in
            llCampCard.animate().alpha(1f).setDuration(250).start()

        }.start()
    }

    // ── 8. Dot indicator builder

    private fun buildDots() {
        llDotIndicators.removeAllViews()
        val dp = resources.displayMetrics.density

        nearbyCamps.forEachIndexed { i, _ ->
            val dot = android.view.View(requireContext())
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

    // ── 9. Carousel runner

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



}

// Badge logic
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