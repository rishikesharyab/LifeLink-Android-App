package com.rishikesh.lifelink

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Admin bottom sheet for an organization's camps (BloodCamps where orgId == orgUid).
 * 1 camp  -> opens its details directly.
 * 2+ camps -> camp cards first; tap a card for full details (back arrow returns to the list).
 * Usage: OrgDetailSheet.show(context, orgUid, orgName)
 */
object OrgDetailSheet {

    private const val CREAM = 0xFFF4EEE3.toInt()
    private const val CORAL = 0xFF8B3A1F.toInt()
    private const val DIVIDER = 0xFFE4D8C8.toInt()
    private const val TEXT_PRIMARY = 0xFF2C2C2A.toInt()
    private const val TEXT_SECONDARY = 0xFF5F5E5A.toInt()

    fun show(context: Context, orgUid: String, orgName: String) {
        val dialog = BottomSheetDialog(context)
        val density = context.resources.displayMetrics.density

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CREAM)
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (28 * density).toInt())
        }
        val scroll = NestedScrollView(context).apply { addView(content) }
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }

        val ui = Ui(context, orgName, content, scroll, density)
        ui.showMessage("Loading details…")
        dialog.show()

        FirebaseFirestore.getInstance().collection("BloodCamps")
            .whereEqualTo("orgId", orgUid)
            .get()
            .addOnSuccessListener { snap ->
                val camps = snap.documents.sortedByDescending { it.getLong("endTimeMillis") ?: 0L }
                when {
                    camps.isEmpty() -> ui.showMessage("No registration details found.")
                    camps.size == 1 -> ui.showDetail(camps[0], canGoBack = false)
                    else -> { ui.camps = camps; ui.showList() }
                }
            }
            .addOnFailureListener { ui.showMessage("Couldn't load details: ${it.message}") }
    }

    private class Ui(
        val ctx: Context,
        val orgName: String,
        val content: LinearLayout,
        val scroll: NestedScrollView,
        val density: Float
    ) {
        var camps: List<DocumentSnapshot> = emptyList()

        fun px(v: Int) = (v * density).toInt()

        private fun reset() {
            content.removeAllViews()
            scroll.scrollTo(0, 0)
        }

        fun showMessage(msg: String) {
            reset()
            content.addView(title(orgName.ifBlank { "Organization" }))
            content.addView(text(msg, 13f, TEXT_SECONDARY).apply { setPadding(0, px(12), 0, 0) })
        }

        // ── Step 1: camp cards ───────────────────────────────────────────────

        fun showList() {
            reset()
            content.addView(title(orgName.ifBlank { "Organization" }))
            content.addView(text("${camps.size} camps registered", 12f, TEXT_SECONDARY).apply {
                setPadding(0, px(2), 0, px(14))
            })
            camps.forEach { content.addView(campCard(it)) }
        }

        private fun campCard(d: DocumentSnapshot): LinearLayout {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(px(14), px(12), px(14), px(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = px(10) }
                setOnClickListener { showDetail(d, canGoBack = true) }
            }

            val left = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            left.addView(text(d.getString("campName").orEmpty().ifBlank { "Untitled camp" }, 14f, TEXT_PRIMARY, bold = true))
            left.addView(text(
                "${d.getString("date").orEmpty()} · ${d.getString("startTime").orEmpty()} – ${d.getString("endTime").orEmpty()}",
                12f, TEXT_SECONDARY
            ).apply { setPadding(0, px(2), 0, 0) })
            left.addView(text(d.getString("location").orEmpty().ifBlank { "—" }, 12f, TEXT_SECONDARY).apply {
                setPadding(0, px(2), 0, px(8))
            })

            val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val groups = (d.get("blood_groups_needed") as? List<*>)?.map { it.toString() }.orEmpty()
            (if (groups.isEmpty()) listOf("All groups") else groups).forEach { chipRow.addView(chip(it)) }
            left.addView(chipRow)

            card.addView(left)
            card.addView(text("›", 22f, CORAL))
            return card
        }

        private fun chip(label: String) = TextView(ctx).apply {
            text = label
            textSize = 11f
            setTextColor(CORAL)
            setPadding(px(8), px(2), px(8), px(2))
            background = GradientDrawable().apply { setStroke(px(1), CORAL); setColor(0) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = px(4) }
        }

        // ── Step 2: full camp details ────────────────────────────────────────

        fun showDetail(d: DocumentSnapshot, canGoBack: Boolean) {
            reset()

            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (canGoBack) {
                header.addView(text("←", 22f, TEXT_PRIMARY).apply {
                    setPadding(0, 0, px(10), 0)
                    setOnClickListener { showList() }
                })
            }
            header.addView(title(d.getString("campName").orEmpty().ifBlank { "Camp details" }).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(text(d.getString("verificationStatus").orEmpty().replaceFirstChar { it.uppercase() }, 11f, CORAL).apply {
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(px(8), px(3), px(8), px(3))
            })
            content.addView(header)
            content.addView(text(d.getString("ngoName").orEmpty(), 12f, TEXT_SECONDARY).apply {
                setPadding(if (canGoBack) px(32) else 0, px(2), 0, 0)
            })

            section("Organization")
            row("Type", d.getString("orgType"))
            row("Registration no.", d.getString("regNumber"))
            row("Year established", d.getString("yearEstablished"))

            section("Camp")
            row("Date", d.getString("date"))
            row("Time", "${d.getString("startTime") ?: "—"} – ${d.getString("endTime") ?: "—"}")
            row("Blood groups needed", (d.get("blood_groups_needed") as? List<*>)?.joinToString(", "))
            row("Facilities", (d.get("facilities") as? List<*>)?.joinToString(", "))
            row("Conducts camps", yesNo(d.getBoolean("conductsCamps")))
            row("Stores / supplies blood", yesNo(d.getBoolean("storesBlood")))

            section("Location")
            row("Address", d.getString("location"))
            row("City / District", d.getString("city"))
            row("State", d.getString("state"))
            row("Pincode", d.getString("pincode"))
            row("Coordinates", "%.5f, %.5f".format(d.getDouble("latitude") ?: 0.0, d.getDouble("longitude") ?: 0.0))

            section("Contact")
            row("Contact person", listOfNotNull(d.getString("contact_name"), d.getString("designation")).joinToString(" · "))
            row("Phone", d.getString("phone"))
            row("WhatsApp", d.getString("whatsapp"))
            row("Email", d.getString("email"))
            row("Website", d.getString("website"))

            section("Verification")
            row("PAN", d.getString("panNumber"))
            row("NGO Darpan ID", d.getString("ngoDarpanId"))
            row("FSSAI / licence", d.getString("fssaiLicense"))

            link("📎 View registration certificate", d.getString("certificateUrl"))
            link("🖼️ View logo", d.getString("logoUrl"))
        }

        // ── view helpers ─────────────────────────────────────────────────────

        private fun yesNo(b: Boolean?) = when (b) { true -> "Yes"; false -> "No"; null -> null }

        private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        private fun title(t: String) = text(t, 18f, TEXT_PRIMARY, bold = true)

        private fun section(t: String) {
            content.addView(text(t.uppercase(), 11f, CORAL, bold = true).apply {
                letterSpacing = 0.08f
                setPadding(0, px(20), 0, px(6))
            })
        }

        private fun row(label: String, value: String?) {
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(6), 0, px(6))
            }
            r.addView(text(label, 12f, TEXT_SECONDARY).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f)
            })
            r.addView(text(if (value.isNullOrBlank()) "—" else value, 13f, TEXT_PRIMARY).apply {
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f)
            })
            content.addView(r)
            content.addView(android.view.View(ctx).apply {
                setBackgroundColor(DIVIDER)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
        }

        private fun link(label: String, url: String?) {
            if (url.isNullOrBlank()) return
            content.addView(text(label, 13f, CORAL).apply {
                setPadding(0, px(14), 0, 0)
                setOnClickListener {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Can't open file", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }
}