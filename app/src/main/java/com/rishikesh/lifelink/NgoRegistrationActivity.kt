package com.rishikesh.lifelink

import android.R.attr.backgroundTint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class NgoRegistrationActivity : AppCompatActivity() {

    private lateinit var tvStepLabel: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var stepContainer: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    private val db      = FirebaseFirestore.getInstance()

    private var currentStep = 1
    private val totalSteps  = 5

    // Data holders
    private val formData = mutableMapOf<String, Any>()
    private var logoUri: Uri? = null
    private var certUri: Uri? = null

    // Step meta
    private val stepTitles = listOf(
        "Basic Organization Info",
        "Location Details",
        "Contact Info",
        "Verification Documents",
        "Blood Donation Details"
    )
    private val stepSubtitles = listOf(
        "Tell us about your organization",
        "Where is your organization located?",
        "How can donors reach you?",
        "Upload your official documents",
        "Blood donation camp details"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ngo_registration)

        tvStepLabel    = findViewById(R.id.tvStepLabel)
        tvStepTitle    = findViewById(R.id.tvStepTitle)
        tvStepSubtitle = findViewById(R.id.tvStepSubtitle)
        progressBar    = findViewById(R.id.progressBar)
        stepContainer  = findViewById(R.id.stepContainer)
        btnNext        = findViewById(R.id.btnNext)
        btnBack        = findViewById(R.id.btnBack)

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
        tvStepLabel.text    = "Step $step of $totalSteps"
        tvStepTitle.text    = stepTitles[step - 1]
        tvStepSubtitle.text = stepSubtitles[step - 1]
        progressBar.progress = step

        btnBack.visibility = if (step > 1) View.VISIBLE else View.GONE
        btnNext.text       = if (step == totalSteps) "Submit" else "Next"

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
        addInputField("org_name",         "Organization Name *")
        addInputField("camp_name",        "Camp Name *")           // ← NEW
        addDropdown("org_type",           "Organization Type *",
            listOf("NGO", "Hospital", "Blood Bank", "Trust", "Other"))
        addInputField("reg_number",       "Registration Number *")
        addInputField("year_established", "Year Established *", InputType.TYPE_CLASS_NUMBER)
    }

    // ── Step 2: Location Details ──────────────────────────────────────────────

    private fun buildStep2() {
        addInputField("full_address",  "Full Address *")
        addInputField("city",          "City / District *")
        addInputField("state",         "State *")
        addInputField("pincode",       "Pincode *", InputType.TYPE_CLASS_NUMBER)
        addInputField("latitude",      "Latitude (from Google Maps)", InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        addInputField("longitude",     "Longitude (from Google Maps)", InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
    }

    // ── Step 3: Contact Info ──────────────────────────────────────────────────

    private fun buildStep3() {
        addInputField("contact_name",        "Primary Contact Person Name *")
        addInputField("designation",         "Designation *")
        addInputField("phone",               "Phone Number *", InputType.TYPE_CLASS_PHONE)
        addInputField("whatsapp",            "WhatsApp Number (if different)")
        addInputField("email",               "Official Email *", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        addInputField("website",             "Website (optional)")
    }

    // ── Step 4: Verification Documents ───────────────────────────────────────

    private fun buildStep4() {
        addInputField("pan_number",          "PAN Number *")
        addInputField("ngo_darpan_id",       "NGO Darpan ID (for NGOs)")
        addInputField("fssai_license",       "FSSAI / Blood Bank License Number")
        addUploadButton("cert_upload",       "📎 Upload Registration Certificate *")
        addUploadButton("logo_upload",       "🖼️ Upload Organization Logo (optional)")
    }

    // ── Step 5: Blood Donation Details ───────────────────────────────────────

    private fun buildStep5() {
        addYesNoToggle("conducts_camps",     "Do you conduct blood donation camps?")
        addYesNoToggle("stores_blood",       "Do you store or supply blood?")
        addMultiSelect("blood_groups_needed","Blood groups currently needed",
            listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"))
        addMultiSelect("facilities",         "Facilities available",
            listOf("AC Hall", "Refreshments", "Medical Staff", "Ambulance", "Parking"))
        addInputField("camp_start_time",     "Camp Start Time (e.g. 09:00 AM) *")
        addInputField("camp_end_time",       "Camp End Time (e.g. 05:00 PM) *")
        addInputField("camp_date",           "Camp Date (e.g. 25 Jan 2025) *")
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateCurrentStep(): Boolean {
        for (i in 0 until stepContainer.childCount) {
            val child = stepContainer.getChildAt(i)
            if (child is LinearLayout) {
                val tag = child.tag as? String ?: continue

                // Skip optional fields
                if (tag.endsWith("_optional")) continue

                // Only validate EditText fields, skip Spinner/Switch/etc
                val et = child.findViewWithTag<android.widget.EditText>("input_$tag")
                if (et != null && et.text.isNullOrBlank()) {
                    et.error = "This field is required"
                    et.requestFocus()
                    return false
                }
            }
        }
        return true
    }

    // ── Collect Data ──────────────────────────────────────────────────────────

    private fun collectCurrentStepData() {
        for (i in 0 until stepContainer.childCount) {
            val child = stepContainer.getChildAt(i)
            if (child is LinearLayout) {
                val tag = child.tag as? String ?: continue
                val et  = child.findViewWithTag<EditText>("input_$tag")
                if (et != null) {
                    formData[tag] = et.text.toString().trim()
                }
            }
        }
    }

    // ── Submit to Firestore ───────────────────────────────────────────────────

    private fun submitToFirestore() {
        btnNext.isEnabled = false
        btnNext.text      = "Submitting..."

        val campData = hashMapOf(
            "campName"            to (formData["camp_name"]           ?: ""),
            "ngoName"             to (formData["org_name"]            ?: ""),
            "location"            to "${formData["city"]}, ${formData["state"]}",
            "date"                to (formData["camp_date"]           ?: ""),
            "startTime"           to (formData["camp_start_time"]     ?: ""),
            "endTime"             to (formData["camp_end_time"]       ?: ""),
            "latitude"            to (formData["latitude"].toString().toDoubleOrNull()  ?: 0.0),
            "longitude"           to (formData["longitude"].toString().toDoubleOrNull() ?: 0.0),
            "endTimeMillis"       to (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000),
            "contact_name"        to (formData["contact_name"]        ?: ""),
            "designation"         to (formData["designation"]         ?: ""),
            "phone"               to (formData["phone"]               ?: ""),
            "email"               to (formData["email"]               ?: ""),
            "blood_groups_needed" to (formData["blood_groups_needed"] ?: emptyList<String>()),
            "facilities"          to (formData["facilities"]          ?: emptyList<String>()),
            "registeredBy"        to emptyList<String>()
        )

        db.collection("BloodCamps")
            .add(campData)
            .addOnSuccessListener {
                Toast.makeText(this, "Camp registered successfully! 🎉", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Submission failed. Please try again.", Toast.LENGTH_SHORT).show()
                btnNext.isEnabled = true
                btnNext.text      = "Submit"
            }
    }

    // ── UI Builder Helpers ────────────────────────────────────────────────────

    private fun addInputField(key: String, hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag         = key
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 20.dp }
        }

        val label = TextView(this).apply {
            text      = hint
            textSize  = 12f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 4.dp }
        }

        val et = EditText(this).apply {
            this.inputType = inputType
            this.hint      = hint
            tag            = "input_$key"
            background     = resources.getDrawable(R.drawable.bg_input_field, null)
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        wrapper.addView(label)
        wrapper.addView(et)
        stepContainer.addView(wrapper)
    }

    private fun addDropdown(key: String, hint: String, options: List<String>) {
        val wrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            tag          = key
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 20.dp }
        }

        val label = TextView(this).apply {
            text     = hint
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_secondary, null))
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@NgoRegistrationActivity,
                android.R.layout.simple_spinner_dropdown_item, options)
            tag = "spinner_$key"
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
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.CENTER_VERTICAL
            tag          = "${key}_optional"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 20.dp }
        }

        val tv = TextView(this).apply {
            text     = label
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switch = Switch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                formData[key] = checked
            }
        }

        row.addView(tv)
        row.addView(switch)
        stepContainer.addView(row)
    }

    private fun addMultiSelect(key: String, label: String, options: List<String>) {
        val wrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            tag          = "${key}_optional"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 20.dp }
        }

        val tv = TextView(this).apply {
            text     = label
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 8.dp }
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Wrap chips — use FlexboxLayout if available, else horizontal scroll
        }

        val selected = mutableListOf<String>()
        val scrollRow = HorizontalScrollView(this)

        options.forEach { option ->
            val chip = TextView(this).apply {
                text = option
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                background = resources.getDrawable(R.drawable.bg_chip_unselected, null)
                setPadding(12.dp, 6.dp, 12.dp, 6.dp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 8.dp }

                setOnClickListener {
                    if (selected.contains(option)) {
                        selected.remove(option)
                        background = resources.getDrawable(R.drawable.bg_chip_unselected, null)
                        setTextColor(resources.getColor(R.color.text_secondary, null))
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

    private fun addUploadButton(key: String, label: String) {
        val wrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            tag          = "${key}_optional"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 20.dp }
        }

        val btn = Button(this).apply {
            text = label
            setTextColor(resources.getColor(R.color.coral_600, null))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.coral_50, null))
            stateListAnimator = null
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                startActivityForResult(intent, if (key == "logo_upload") 101 else 102)
            }
        }

        wrapper.addView(btn)
        stepContainer.addView(wrapper)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()



}