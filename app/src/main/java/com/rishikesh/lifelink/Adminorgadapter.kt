package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.rishikesh.lifelink.model.NgoRegistration

class AdminOrgAdapter(
    private val items: List<NgoRegistration>,
    private val showActions: Boolean,
    private val onApprove: (NgoRegistration) -> Unit,
    private val onReject: (NgoRegistration) -> Unit,
    private val onViewCertificate: (NgoRegistration) -> Unit
) : RecyclerView.Adapter<AdminOrgAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.OrgName)
        val status: TextView = view.findViewById(R.id.OrgStatus)
        val typeSubmitted: TextView = view.findViewById(R.id.OrgTypeSubmitted)
        val pan: TextView = view.findViewById(R.id.OrgPan)
        val darpan: TextView = view.findViewById(R.id.OrgDarpan)
        val contact: TextView = view.findViewById(R.id.OrgContact)
        val viewCert: TextView = view.findViewById(R.id.ViewCertificate)
        val actionRow: View = view.findViewById(R.id.actionRow)
        val approveBtn: MaterialButton = view.findViewById(R.id.btnApprove)
        val rejectBtn: MaterialButton = view.findViewById(R.id.btnReject)
        val rejectionReason: TextView = view.findViewById(R.id.tvRejectionReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_org, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val org = items[position]

        holder.name.text = org.orgName.ifBlank { "Untitled organization" }
        holder.status.text = org.verificationStatus.replaceFirstChar { it.uppercase() }
        holder.typeSubmitted.text = org.orgType.ifBlank { "Organization" }
        holder.pan.text = org.panNumber.ifBlank { "—" }
        holder.darpan.text = org.ngoDarpanId.ifBlank { "—" }
        holder.contact.text = "${org.contactName} · ${org.phone}"

        holder.viewCert.setOnClickListener { onViewCertificate(org) }

        holder.actionRow.visibility = if (showActions) View.VISIBLE else View.GONE
        holder.approveBtn.setOnClickListener { onApprove(org) }
        holder.rejectBtn.setOnClickListener { onReject(org) }

        if (!showActions && org.rejectionReason.isNotBlank()) {
            holder.rejectionReason.visibility = View.VISIBLE
            holder.rejectionReason.text = "Reason: ${org.rejectionReason}"
        } else {
            holder.rejectionReason.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}