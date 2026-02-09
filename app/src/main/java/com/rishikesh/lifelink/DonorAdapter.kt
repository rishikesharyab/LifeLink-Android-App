package com.rishikesh.lifelink

import Donor
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DonorAdapter(
    private val donors: List<Donor>,
    private val onClick: (Donor) -> Unit

) : RecyclerView.Adapter<DonorAdapter.DonorViewHolder>() {

    class DonorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.itemName)
        val blood: TextView = view.findViewById(R.id.itemBlood)
        val phone: TextView = view.findViewById(R.id.itemPhone)

        val distance: TextView = view.findViewById(R.id.itemDistance)
        val callBtn: ImageButton = view.findViewById(R.id.callBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donor, parent, false)
        return DonorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DonorViewHolder, position: Int) {
        val donor = donors[position]

        holder.name.text = donor.name
        holder.blood.text = "Blood: ${donor.bloodGroup}"
        holder.phone.text = donor.phone
        holder.distance.text = " • ${"%.1f".format(donor.distanceKm)} km"

        holder.callBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${donor.phone}")
            holder.itemView.context.startActivity(intent)
        }

        holder.itemView.setOnClickListener {
            onClick(donor)
        }
    }


    override fun getItemCount() = donors.size
}
