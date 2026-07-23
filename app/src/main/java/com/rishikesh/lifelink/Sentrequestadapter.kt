package com.rishikesh.lifelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rishikesh.lifelink.model.BloodRequest
import java.util.Date

class SentRequestAdapter(
    private val items: List<BloodRequest>,
    private val onResendClick: (BloodRequest, Int) -> Unit
) : RecyclerView.Adapter<SentRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSentName)
        val meta: TextView = view.findViewById(R.id.tvSentMeta)
        val resendButton: TextView = view.findViewById(R.id.btnResend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sent_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = items[position]
        holder.name.text = request.toUserName

        val statusLabel = when (request.status) {
            BloodRequest.STATUS_DECLINED -> "Declined"
            else -> "Waiting for response"
        }
        holder.meta.text = "${request.bloodGroup} · $statusLabel"

        val cooldownPassed = request.createdAt == null ||
                (Date().time - request.createdAt.time) >= BloodRequest.RESEND_COOLDOWN_MS

        if (cooldownPassed) {
            holder.resendButton.visibility = View.VISIBLE
            holder.resendButton.setOnClickListener { onResendClick(request, position) }
        } else {
            holder.resendButton.visibility = View.GONE
            holder.resendButton.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = items.size
}