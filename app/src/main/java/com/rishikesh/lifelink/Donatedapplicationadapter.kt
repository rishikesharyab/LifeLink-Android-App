package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.CampApplication
import java.text.SimpleDateFormat
import java.util.Locale

class DonatedApplicationAdapter(
    private val items: List<CampApplication>
) : RecyclerView.Adapter<DonatedApplicationAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvDonatedName)
        val age: TextView = view.findViewById(R.id.tvDonatedAge)
        val bloodGroup: TextView = view.findViewById(R.id.tvDonatedBloodGroup)
        val phone: TextView = view.findViewById(R.id.tvDonatedPhone)
        val date: TextView = view.findViewById(R.id.tvDonatedDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donated_application, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = items[position]
        holder.name.text = application.name
        holder.age.text = if (application.age > 0) "Age ${application.age}" else "Age —"
        holder.bloodGroup.text = application.bloodGroup.ifBlank { "—" }
        holder.phone.text = application.phone.ifBlank { "No phone on file" }
        holder.date.text = application.donatedAt?.let { "Donated on ${dateFormat.format(it)}" } ?: "Donated"
    }

    override fun getItemCount(): Int = items.size
}