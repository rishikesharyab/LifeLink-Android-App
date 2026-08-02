package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.Donor

class DonorAdapter(
    private val donors: List<Donor>,
    private val requestedDonorIds: MutableSet<String>,
    private val onItemClick: (Donor) -> Unit,
    private val onRequestClick: (Donor, Int) -> Unit
) : RecyclerView.Adapter<DonorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvDonorCardName)
        val location: TextView = view.findViewById(R.id.tvDonorCardLocation)
        val bloodGroup: TextView = view.findViewById(R.id.tvDonorCardBloodGroup)
        val distance: TextView = view.findViewById(R.id.tvDonorCardDistance)
        val requestButton: TextView = view.findViewById(R.id.btnDonorRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donor_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val donor = donors[position]

        holder.name.text = donor.name
        holder.location.text = donor.location.ifBlank { "Location not shared" }
        holder.bloodGroup.text = donor.bloodGroup
        holder.distance.text = "%.1f km".format(donor.distanceKm)

        holder.itemView.setOnClickListener { onItemClick(donor) }

        val alreadyRequested = requestedDonorIds.contains(donor.id)
        if (alreadyRequested) {
            holder.requestButton.text = "Requested"
            holder.requestButton.setBackgroundResource(R.drawable.bg_pill_outline)
            holder.requestButton.setTextColor(
                holder.itemView.resources.getColor(R.color.text_secondary, null)
            )
            holder.requestButton.isEnabled = false
            holder.requestButton.setOnClickListener(null)
        } else {
            holder.requestButton.text = "Request"
            holder.requestButton.setBackgroundResource(R.drawable.bg_pill_filled)
            holder.requestButton.setTextColor(android.graphics.Color.WHITE)
            holder.requestButton.isEnabled = true
            holder.requestButton.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onRequestClick(donor, currentPos)
                }
            }
        }
    }

    override fun getItemCount(): Int = donors.size
}