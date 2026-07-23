package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.BloodRequest

class IncomingRequestAdapter(
    private val items: List<BloodRequest>,
    private val onAccept: (BloodRequest, Int) -> Unit,
    private val onDecline: (BloodRequest, Int) -> Unit
) : RecyclerView.Adapter<IncomingRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvIncomingName)
        val meta: TextView = view.findViewById(R.id.tvIncomingMeta)
        val acceptButton: TextView = view.findViewById(R.id.btnAccept)
        val declineButton: TextView = view.findViewById(R.id.btnDecline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incoming_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = items[position]
        holder.name.text = request.fromUserName
        holder.meta.text = "Needs ${request.bloodGroup} · %.1f km away".format(request.distanceKm)

        holder.acceptButton.setOnClickListener { onAccept(request, position) }
        holder.declineButton.setOnClickListener { onDecline(request, position) }
    }

    override fun getItemCount(): Int = items.size
}