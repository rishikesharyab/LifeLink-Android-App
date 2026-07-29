package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.CampApplication

class CampApplicationAdapter(
    private val items: List<CampApplication>,
    private val onMarkDonatedClick: (CampApplication, Int) -> Unit
) : RecyclerView.Adapter<CampApplicationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvApplicantName)
        val age: TextView = view.findViewById(R.id.tvApplicantAge)
        val bloodGroup: TextView = view.findViewById(R.id.tvApplicantBloodGroup)
        val phone: TextView = view.findViewById(R.id.tvApplicantPhone)
        val donatedButton: TextView = view.findViewById(R.id.btnMarkApplicantDonated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_camp_application, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = items[position]
        holder.name.text = application.name
        holder.age.text = if (application.age > 0) "Age ${application.age}" else "Age —"
        holder.bloodGroup.text = application.bloodGroup.ifBlank { "—" }
        holder.phone.text = application.phone.ifBlank { "No phone on file" }

        holder.donatedButton.setOnClickListener { onMarkDonatedClick(application, position) }
    }

    override fun getItemCount(): Int = items.size
}