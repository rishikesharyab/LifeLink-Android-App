package com.rishikesh.lifelink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.rishikesh.lifelink.util.applySystemBarInsets
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class NgoRegistrationActivity : AppCompatActivity() {

    companion object {
        private const val REQ_LOGO_UPLOAD = 101
        private const val REQ_CERT_UPLOAD = 102
        private const val REQ_LOCATION_PICKER = 103
        private const val PHONE_DIGITS = 10
    }

    private lateinit var tvStepLabel: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepSubtitle: TextView
    private lateinit var ivStepIcon: ImageView
    private lateinit var stepContainer: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var segments: List<View>

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var currentStep = 1
    private val totalSteps = 5

    private val formData = mutableMapOf<String, Any>()
    private val optionalFields = setOf("whatsapp", "website", "ngo_darpan_id", "fssai_license")

    private var pickedAddress: String? = null
    private var pickedLat: Double = 0.0
    private var pickedLng: Double = 0.0
    private lateinit var tvAddressValue: TextView

    private var certDownloadUrl: String? = null
    private var logoDownloadUrl: String? = null
    private var certUploading = false
    private lateinit var tvCertStatus: TextView
    private lateinit var tvLogoStatus: TextView

    private val stepTitles = listOf(
        "Basic Organization Info",
        "Location Details",
        "Contact Info",
        "Verification Documents",
        "Blood Donation Details"
    )
    private val stepSubtitles = listOf(
        "Tell us about your organization",
        "Where will the camp be held?",
        "How can donors reach you?",
        "Upload your official documents",
        "Blood donation camp details"
    )
    private val stepIcons = listOf(
        R.drawable.ic_building,
        R.drawable.ic_location_pin,
        R.drawable.ic_phone,
        R.drawable.ic_document,
        R.drawable.ic_heart_outline
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ngo_registration)
        applySystemBarInsets()

        tvStepLabel = findViewById(R.id.tvStepLabel)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepSubtitle = findViewById(R.id.tvStepSubtitle)
        ivStepIcon = findViewById(R.id.ivStepIcon)
        stepContainer = findViewById(R.id.stepContainer)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        segments = listOf(
            findViewById(R.id.segment1), findViewById(R.id.segment2), findViewById(R.id.segment3),
            findViewById(R.id.segment4), findViewById(R.id.segment5)
        )

        renderStep(currentStep)

        btnNext.setOnClickListener {
            if (validateCurrentStep()) {
                collectCurrentStepData()
                if (currentStep < totalSteps) {
                    currentStep++
                    renderStep(currentStep)
                } else {
                    submitToFirestore()
                }
            }
        }

        btnBack.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                renderStep(currentStep)
            }
        }
    }

    // ── Render Step ───────────────────────────────────────────────────────────

    private fun renderStep(step: Int) {
        tvStepLabel.text = "Step $step of $totalSteps"
        tvStepTitle.text = stepTitles[step - 1]
        tvStepSubtitle.text = stepSubtitles[step - 1]
        ivStepIcon.setImageResource(stepIcons[step - 1])

        segments.forEachIndexed { index, view ->
            view.setBackgroundColor(if (index < step) 0xFF8B3A1F.toInt() else 0xFFE4D8C8.toInt())
        }

        btnBack.visibility = if (step > 1) View.VISIBLE else View.GONE
        btnNext.text = if (step == totalSteps) "Submit" else "Next"
        btnNext.icon = resources.getDrawable(
            if (step == totalSteps) R.drawable.ic_check else R.drawable.ic_arrow_right, null
        )

        stepContainer.removeAllViews()

        when (step) {
            1 -> buildStep1()
            2 -> buildStep2()
            3 -> buildStep3()
            4 -> buildStep4()
            5 -> buildStep5()
        }
    }

    // ── Step 1: Basic Organization Info ──────────────────────────────────────

    private fun buildStep1() {
        addInputField("org_name", "Organization Name *", prefill = formData["org_name"] as? String)
        addInputField("camp_name", "Camp Name *", prefill = formData["camp_name"] as? String)
        addDropdown(
            "org_type", "Organization Type *",
            listOf("NGO", "Hospital", "Blood Bank", "Trust", "Other")
        )
        addInputField("reg_number", "Registration Number *", prefill = formData["reg_number"] as? String)
        addInputField(
            "year_established", "Year Established *", InputType.TYPE_CLASS_NUMBER,
            prefill = formData["year_established"] as? String
        )
    }

    // ── Step 2: Location Details ─────────────────────────────────────────────

    private fun buildStep2() {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "camp_address"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.dp }
        }

        val label = TextView(this).apply {
            text = "Camp Address *"
            textSize = 12f
            setTextColor(0xFF5F5E5A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 4.dp }
        }

        val addressCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.bg_input_field_active, null)
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            isClickable = true
            setOnClickListener {
                startActivityForResult(
                    Intent(this@NgoRegistrationActivity, LocationPickerActivity::class.java),
                    REQ_LOCATION_PICKER
                )
            }
        }

        val pinIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(16.dp, 16.dp).also { it.marginEnd = 8.dp }
            setImageResource(R.drawable.ic_location_pin)
        }

        tvAddressValue = TextView(this).apply {
            text = pickedAddress ?: "Tap to set camp location on map"
            textSize = 14f
            setTextColor(if (pickedAddress != null) 0xFF2C2C2A.toInt() else 0xFFB4B2A9.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        addressCard.addView(pinIcon)
        addressCard.addView(tvAddressValue)
        wrapper.addView(label)
        wrapper.addView(addressCard)
        stepContainer.addView(wrapper)

        addInputField("city", "City / District *", prefill = formData["city"] as? String)
        addInputField("state", "State *", prefill = formData["state"] as? String)
        addInputField(
            "pincode", "Pincode", InputType.TYPE_CLASS_NUMBER,
            optional = true, prefill = formData["pincode"] as? String
        )
    }

    // ── Step 3: Contact Info ─────────────────────────────────────────────────

    private fun buildStep3() {
        addInputField("contact_name", "Primary Contact Person Name *", prefill = formData["contact_name"] as? String)
        addInputField("designation", "Designation *", prefill = formData["designation"] as? String)
        addInputField(
            "phone", "Phone Number *", InputType.TYPE_CLASS_PHONE,
            prefill = formData["phone"] as? String, maxLength = PHONE_DIGITS
        )
        addInputField(
            "whatsapp", "WhatsApp Number", InputType.TYPE_CLASS_PHONE,
            optional = true, prefill = formData["whatsapp"] as? String
        )
        addInputField(
            "email", "Official Email *", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            prefill = formData["email"] as? String
        )
        addInputField("website", "Website", optional = true, prefill = formData["website"] as? String)
    }

    // ── Step 4: Verification Documents ───────────────────────────────────────

    private fun buildStep4() {
        addInputField("pan_number", "PAN Number *", prefill = formData["pan_number"] as? String)
        addInputField("ngo_darpan_id", "NGO Darpan ID (for NGOs)", optional = true, prefill = formData["ngo_darpan_id"] as? String)
        addInputField("fssai_license", "FSSAI / Blood Bank License Number", optional = true, prefill = formData["fssai_license"] as? String)

        tvCertStatus = addUploadRow("Registration Certificate *", certDownloadUrl, certUploading) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            startActivityForResult(intent, REQ_CERT_UPLOAD)
        }

        tvLogoStatus = addUploadRow("Organization Logo", logoDownloadUrl, false) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, REQ_LOGO_UPLOAD)
        }
    }

    // ── Step 5: Blood Donation Details ───────────────────────────────────────

    private fun buildStep5() {
        addYesNoToggle("conducts_camps", "Do you conduct blood donation camps?")
        addYesNoToggle("stores_blood", "Do you store or supply blood?")
        addMultiSelect(
            "blood_groups_needed", "Blood groups currently needed",
            listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        )
        addMultiSelect(
            "facilities", "Facilities available",
            listOf("AC Hall", "Refreshments", "Medical Staff", "Ambulance", "Parking")
        )
        addInputField("camp_start_time", "Camp Start Time (e.g. 09:00 AM) *", prefill = formData["camp_start_time"] as? String)
        addInputField("camp_end_time", "Camp End Time (e.g. 05:00 PM) *", prefill = formData["camp_end_time"] as? String)
        addInputField("camp_date", "Camp Date (e.g. 25 Jan 2025) *", prefill = formData["camp_date"] as? String)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateCurrentStep(): Boolean {
        if (currentStep == 2 && pickedAddress == null) {
            Toast.makeText(this, "Please set the camp location on the map", Toast.LENGTH_SHORT).show()
            return false
        }

        if (currentStep == 4) {
            if (certUploading) {
                Toast.makeText(this, "Certificate is still uploading, please wait", Toast.LENGTH_SHORT).show()
                return false
            }
            if (certDownloadUrl == null) {
                Toast.makeText(this, "Please upload your registration certificate", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        for (i in 0 until stepContainer.childCount) {
            val child = stepContainer.getChildAt(i)
            if (child is LinearLayout) {
                val key = child.tag as? String ?: continue
                if (key in optionalFields) continue

                val et = child.findViewWithTag<EditText>("input_$key")
                if (et != null && et.text.isNullOrBlank()) {
                    et.error = "This field is required"
                    et.requestFocus()
                    return false
                }

                // Phone number must be exactly PHONE_DIGITS digits (paste can bypass the input filter)
                if (key == "phone" && et != null) {
                    val digitCount = et.text.toString().count { it.isDigit() }
                    if (digitCount != PHONE_DIGITS) {
                        showPhoneAlert()
                        et.requestFocus()
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun showPhoneAlert() {
        AlertDialog.Builder(this)
            .setTitle("Invalid phone number")
            .setMessage("Phone number must be exactly $PHONE_DIGITS digits.")
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Collect Data ──────────────────────────────────────────────────────────

    private fun collectCurrentStepData() {
        for (i in 0 until stepContainer.childCount) {
            val child = stepContainer.getChildAt(i)
            if (child is LinearLayout) {
                val key = child.tag as? String ?: continue
                val et = child.findViewWithTag<EditText>("input_$key")
                if (et != null) {
                    formData[key] = et.text.toString().trim()
                }
            }
        }
    }

    // ── Location picker result ───────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_LOCATION_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                pickedAddress = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS)
                pickedLat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0.0)
                pickedLng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0.0)
                formData["city"] = data.getStringExtra(LocationPickerActivity.EXTRA_CITY) ?: ""
                formData["state"] = data.getStringExtra(LocationPickerActivity.EXTRA_STATE) ?: ""
                formData["pincode"] = data.getStringExtra(LocationPickerActivity.EXTRA_PINCODE) ?: ""
                renderStep(currentStep)
            }
            return
        }

        if (requestCode == REQ_CERT_UPLOAD || requestCode == REQ_LOGO_UPLOAD) {
            val uri = data?.data ?: return
            uploadDocument(uri, isCertificate = requestCode == REQ_CERT_UPLOAD)
        }
    }

    private fun uploadDocument(uri: Uri, isCertificate: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val statusView = if (isCertificate) tvCertStatus else tvLogoStatus
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentResolver.getType(uri)) ?: "dat"
        val fileName = "${if (isCertificate) "cert" else "logo"}_${System.currentTimeMillis()}.$extension"
        val ref = storage.reference.child("ngo_documents/$uid/$fileName")

        if (isCertificate) certUploading = true
        statusView.text = "Uploading…"

        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { url ->
                        if (isCertificate) {
                            certDownloadUrl = url.toString()
                            certUploading = false
                        } else {
                            logoDownloadUrl = url.toString()
                        }
                        statusView.text = "Uploaded — $fileName"
                        statusView.setTextColor(0xFF8B3A1F.toInt())
                    }
                    .addOnFailureListener {
                        if (isCertificate) certUploading = false
                        statusView.text = "Upload failed — tap to retry"
                    }
            }
            .addOnFailureListener {
                if (isCertificate) certUploading = false
                statusView.text = "Upload failed — tap to retry"
            }
    }

    // ── Submit to Firestore ───────────────────────────────────────────────────

    private fun submitToFirestore() {
        btnNext.isEnabled = false
        btnNext.text = "Submitting..."

        val orgId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val campData = hashMapOf(
            "campName" to (formData["camp_name"] ?: ""),
            "ngoName" to (formData["org_name"] ?: ""),
            "location" to (pickedAddress ?: "${formData["city"]}, ${formData["state"]}"),
            "date" to (formData["camp_date"] ?: ""),
            "startTime" to (formData["camp_start_time"] ?: ""),
            "endTime" to (formData["camp_end_time"] ?: ""),
            "latitude" to pickedLat,
            "longitude" to pickedLng,
            "endTimeMillis" to (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000),
            "contact_name" to (formData["contact_name"] ?: ""),
            "designation" to (formData["designation"] ?: ""),
            "phone" to (formData["phone"] ?: ""),
            "email" to (formData["email"] ?: ""),
            "blood_groups_needed" to (formData["blood_groups_needed"] ?: emptyList<String>()),
            "facilities" to (formData["facilities"] ?: emptyList<String>()),
            "registeredBy" to emptyList<String>(),
            "orgId" to orgId,
            "panNumber" to (formData["pan_number"] ?: ""),
            "certificateUrl" to (certDownloadUrl ?: ""),
            "logoUrl" to (logoDownloadUrl ?: "")
        )

        db.collection("BloodCamps")
            .add(campData)
            .addOnSuccessListener {
                if (orgId.isNotBlank()) {
                    db.collection("Users").document(orgId)
                        .set(
                            mapOf("isOrganization" to true, "verificationStatus" to "pending"),
                            SetOptions.merge()
                        )
                }
                Toast.makeText(this, "Camp registered — pending verification", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Submission failed. Please try again.", Toast.LENGTH_SHORT).show()
                btnNext.isEnabled = true
                btnNext.text = "Submit"
            }
    }

    // ── UI Builder Helpers (donor-profile visual language) ──────────────────

    private fun addInputField(
        key: String,
        hint: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        optional: Boolean = false,
        prefill: String? = null,
        maxLength: Int? = null
    ) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = key
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.dp }
        }

        val label = TextView(this).apply {
            text = if (optional) "$hint · optional" else hint
            textSize = 12f
            setTextColor(0xFF5F5E5A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 4.dp }
        }

        val et = EditText(this).apply {
            this.inputType = inputType
            this.hint = hint
            tag = "input_$key"
            background = resources.getDrawable(R.drawable.bg_input_field_active, null)
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48.dp
            )
            maxLength?.let { filters = arrayOf(android.text.InputFilter.LengthFilter(it)) }
            prefill?.let { setText(it) }
        }

        wrapper.addView(label)
        wrapper.addView(et)
        stepContainer.addView(wrapper)
    }

    private fun addDropdown(key: String, hint: String, options: List<String>) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = key
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.dp }
        }

        val label = TextView(this).apply {
            text = hint
            textSize = 12f
            setTextColor(0xFF5F5E5A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 4.dp }
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@NgoRegistrationActivity,
                android.R.layout.simple_spinner_dropdown_item, options
            )
            tag = "spinner_$key"
            background = resources.getDrawable(R.drawable.bg_input_field_active, null)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48.dp
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    formData[key] = options[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        wrapper.addView(label)
        wrapper.addView(spinner)
        stepContainer.addView(wrapper)
    }

    private fun addYesNoToggle(key: String, label: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            tag = "${key}_toggle"
            background = resources.getDrawable(R.drawable.bg_option_unselected, null)
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 12.dp }
        }

        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFF2C2C2A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switch = SwitchMaterial(this).apply {
            setOnCheckedChangeListener { _, checked -> formData[key] = checked }
            try {
                thumbTintList = resources.getColorStateList(R.color.switch_thumb_tint, null)
                trackTintList = resources.getColorStateList(R.color.switch_track_tint, null)
            } catch (e: Exception) { /* color resource optional */ }
        }

        row.addView(tv)
        row.addView(switch)
        stepContainer.addView(row)
    }

    private fun addMultiSelect(key: String, label: String, options: List<String>) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "${key}_multiselect"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.dp }
        }

        val tv = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF5F5E5A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 8.dp }
        }

        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val selected = mutableListOf<String>()
        val scrollRow = HorizontalScrollView(this)

        options.forEach { option ->
            val chip = TextView(this).apply {
                text = option
                textSize = 12f
                setTextColor(0xFF5F5E5A.toInt())
                background = resources.getDrawable(R.drawable.bg_chip_unselected, null)
                setPadding(12.dp, 6.dp, 12.dp, 6.dp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 8.dp }

                setOnClickListener {
                    if (selected.contains(option)) {
                        selected.remove(option)
                        background = resources.getDrawable(R.drawable.bg_chip_unselected, null)
                        setTextColor(0xFF5F5E5A.toInt())
                    } else {
                        selected.add(option)
                        background = resources.getDrawable(R.drawable.bg_chip_selected, null)
                        setTextColor(resources.getColor(R.color.white, null))
                    }
                    formData[key] = selected.toList()
                }
            }
            chipRow.addView(chip)
        }

        scrollRow.addView(chipRow)
        wrapper.addView(tv)
        wrapper.addView(scrollRow)
        stepContainer.addView(wrapper)
    }

    private fun addUploadRow(
        label: String,
        currentUrl: String?,
        uploading: Boolean,
        onClick: () -> Unit
    ): TextView {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "${label}_upload"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.dp }
        }

        val tvLabel = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF5F5E5A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 6.dp }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.bg_input_field_active, null)
            setPadding(12.dp, 14.dp, 12.dp, 14.dp)
            isClickable = true
            setOnClickListener { onClick() }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(16.dp, 16.dp).also { it.marginEnd = 8.dp }
            setImageResource(R.drawable.ic_document)
        }

        val statusText = TextView(this).apply {
            text = when {
                uploading -> "Uploading…"
                currentUrl != null -> "Uploaded ✓"
                else -> "Tap to choose file"
            }
            textSize = 13f
            setTextColor(if (currentUrl != null) 0xFF8B3A1F.toInt() else 0xFFB4B2A9.toInt())
        }

        row.addView(icon)
        row.addView(statusText)
        wrapper.addView(tvLabel)
        wrapper.addView(row)
        stepContainer.addView(wrapper)
        return statusText
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}