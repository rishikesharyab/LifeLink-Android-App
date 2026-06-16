package com.rishikesh.lifelink

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.rishikesh.lifelink.R
import com.rishikesh.lifelink.model.Donor
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DonorBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_DONOR = "arg_donor"
        private const val ARG_LOCATION = "arg_location"

        private const val DONATION_INTERVAL_MONTHS = 3

        // ✅ FIXED newInstance (NOW ACCEPTS LOCATION)
        fun newInstance(donor: Donor, location: String): DonorBottomSheetFragment {
            return DonorBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_DONOR, donor)
                    putString(ARG_LOCATION, location)
                }
            }
        }
    }

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