package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.BloodRequest

class AcceptedRequestAdapter(
    private val items: List<BloodRequest>,
    private val donorPhones: Map<String, String>,
    private val onCallClick: (BloodRequest) -> Unit,
    private val onMarkDonatedClick: (BloodRequest, Int) -> Unit
) : RecyclerView.Adapter<AcceptedRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvAcceptedName)
        val location: TextView = view.findViewById(R.id.tvAcceptedLocation)
        val bloodGroup: TextView = view.findViewById(R.id.tvAcceptedBloodGroup)
        val distance: TextView = view.findViewById(R.id.tvAcceptedDistance)
        val phone: TextView = view.findViewById(R.id.tvAcceptedPhone)
        val callButton: ImageView = view.findViewById(R.id.btnCallDonor)
        val donatedButton: TextView = view.findViewById(R.id.btnMarkDonated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_accepted_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = items[position]
        holder.name.text = request.toUserName
        holder.location.text = request.toUserLocation.ifBlank { "Location not shared" }
        holder.bloodGroup.text = request.bloodGroup
        holder.distance.text = "%.1f km".format(request.distanceKm)

        val phoneNumber = donorPhones[request.toUserId]
        holder.phone.text = phoneNumber ?: "Phone unavailable"

        holder.callButton.setOnClickListener { onCallClick(request) }

        if (request.donated) {
            holder.donatedButton.text = "Donated ✓"
            holder.donatedButton.setBackgroundResource(R.drawable.bg_pill_outline)
            holder.donatedButton.setTextColor(
                holder.itemView.resources.getColor(R.color.text_secondary, null)
            )
            holder.donatedButton.isEnabled = false
            holder.donatedButton.setOnClickListener(null)
        } else {
            holder.donatedButton.text = "Mark as donated"
            holder.donatedButton.setBackgroundResource(R.drawable.bg_pill_filled)
            holder.donatedButton.setTextColor(android.graphics.Color.WHITE)
            holder.donatedButton.isEnabled = true
            holder.donatedButton.setOnClickListener { onMarkDonatedClick(request, position) }
        }
    }

    override fun getItemCount(): Int = items.size
}