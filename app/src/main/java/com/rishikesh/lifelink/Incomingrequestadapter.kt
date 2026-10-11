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
    private val onDecline: (BloodRequest, Int) -> Unit,
    private val onNavigate: (BloodRequest) -> Unit
) : RecyclerView.Adapter<IncomingRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvIncomingName)
        val meta: TextView = view.findViewById(R.id.tvIncomingMeta)
        val acceptButton: TextView = view.findViewById(R.id.btnAccept)
        val declineButton: TextView = view.findViewById(R.id.btnDecline)
        val actionRow: View = view.findViewById(R.id.actionRow)
        val navigateButton: TextView = view.findViewById(R.id.btnNavigate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incoming_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = items[position]
        val accepted = request.status == BloodRequest.STATUS_ACCEPTED

        holder.name.text = request.fromUserName
        holder.meta.text = "Needs ${request.bloodGroup} · %.1f km away".format(request.distanceKm) +
                if (accepted) " · Accepted ✓" else ""

        holder.actionRow.visibility = if (accepted) View.GONE else View.VISIBLE
        holder.navigateButton.visibility = if (accepted) View.VISIBLE else View.GONE

        holder.acceptButton.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onAccept(request, pos)
        }
        holder.declineButton.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onDecline(request, pos)
        }
        holder.navigateButton.setOnClickListener { onNavigate(request) }
    }

    override fun getItemCount(): Int = items.size
}