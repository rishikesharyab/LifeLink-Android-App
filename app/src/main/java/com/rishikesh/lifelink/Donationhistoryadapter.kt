package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.DonationRecord
import java.text.SimpleDateFormat
import java.util.Locale

class DonationHistoryAdapter(
    private val items: List<DonationRecord>
) : RecyclerView.Adapter<DonationHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val campName: TextView = view.findViewById(R.id.tvItemCampName)
        val location: TextView = view.findViewById(R.id.tvItemLocation)
        val date: TextView = view.findViewById(R.id.tvItemDate)
        val bloodGroup: TextView = view.findViewById(R.id.tvItemBloodGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        holder.campName.text = record.campName.ifBlank { "Blood donation" }
        holder.location.text = record.location.ifBlank { "—" }
        holder.date.text = record.date?.let { dateFormat.format(it) } ?: "—"
        holder.bloodGroup.text = record.bloodGroup.ifBlank { "—" }
    }

    override fun getItemCount(): Int = items.size
}