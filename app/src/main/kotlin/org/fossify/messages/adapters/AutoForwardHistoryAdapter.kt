package org.fossify.messages.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemAutoForwardHistoryBinding
import org.fossify.messages.models.AutoForwardHistory

class AutoForwardHistoryAdapter(
    private val activity: SimpleActivity,
    private val history: List<AutoForwardHistory>,
    private val onClick: (AutoForwardHistory) -> Unit
) : RecyclerView.Adapter<AutoForwardHistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAutoForwardHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(history[position])
    }

    override fun getItemCount() = history.size

    inner class ViewHolder(private val binding: ItemAutoForwardHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AutoForwardHistory) = binding.apply {
            root.setupViewBackground(activity)
            autoForwardHistoryTitle.text = "${getStatusText(item.status)} - ${item.ruleName}"
            autoForwardHistorySummary.text =
                "${item.sourceSender} -> ${item.destinationType.lowercase()} ${item.destination}"
            autoForwardHistoryTime.text = item.createdAt.formatDateOrTime(activity, true, false)
            val textColor = activity.getProperTextColor()
            autoForwardHistoryTitle.setTextColor(textColor)
            autoForwardHistorySummary.setTextColor(textColor)
            autoForwardHistoryTime.setTextColor(textColor)
            root.setOnClickListener {
                onClick(item)
            }
        }
    }

    private fun getStatusText(status: String) = activity.getString(
        when (status) {
            AutoForwardHistory.STATUS_SUCCESS -> R.string.auto_forward_status_success
            AutoForwardHistory.STATUS_FAILED -> R.string.auto_forward_status_failed
            else -> R.string.auto_forward_status_queued
        }
    )
}
