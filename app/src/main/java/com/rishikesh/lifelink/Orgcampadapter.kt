package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.BloodCamp

class OrgCampAdapter(
    private val camps: List<BloodCamp>,
    private val applicationCounts: Map<String, Pair<Int, Int>>, // campId -> (pending, donated)
    private val onClick: (BloodCamp) -> Unit
) : RecyclerView.Adapter<OrgCampAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvOrgCampName)
        val date: TextView = view.findViewById(R.id.tvOrgCampDate)
        val counts: TextView = view.findViewById(R.id.tvOrgCampCounts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_org_camp, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val camp = camps[position]
        holder.name.text = camp.campName.ifBlank { "Untitled camp" }
        holder.date.text = "${camp.date} · ${camp.startTime} – ${camp.endTime}"

        val (pending, donated) = applicationCounts[camp.campId] ?: (0 to 0)
        holder.counts.text = "$pending application${if (pending == 1) "" else "s"} · $donated donated"

        holder.itemView.setOnClickListener { onClick(camp) }
    }

    override fun getItemCount(): Int = camps.size
}