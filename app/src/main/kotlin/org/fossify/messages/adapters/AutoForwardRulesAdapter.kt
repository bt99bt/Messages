package org.fossify.messages.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemAutoForwardRuleBinding
import org.fossify.messages.models.AutoForwardDestinationType
import org.fossify.messages.models.AutoForwardMatchType
import org.fossify.messages.models.AutoForwardRule

class AutoForwardRulesAdapter(
    private val activity: SimpleActivity,
    private val rules: List<AutoForwardRule>,
    private val onToggle: (AutoForwardRule, Boolean) -> Unit,
    private val onEdit: (AutoForwardRule) -> Unit,
    private val onDelete: (AutoForwardRule) -> Unit
) : RecyclerView.Adapter<AutoForwardRulesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAutoForwardRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    override fun getItemCount() = rules.size

    inner class ViewHolder(private val binding: ItemAutoForwardRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: AutoForwardRule) = binding.apply {
            root.setupViewBackground(activity)
            autoForwardRuleTitle.text = rule.name
            autoForwardRuleSummary.text = buildRuleSummary(rule)
            autoForwardRuleTitle.setTextColor(activity.getProperTextColor())
            autoForwardRuleSummary.setTextColor(activity.getProperTextColor())
            autoForwardRuleEnabled.setOnCheckedChangeListener(null)
            autoForwardRuleEnabled.isChecked = rule.enabled
            autoForwardRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }
            autoForwardRuleMenu.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }
            autoForwardRuleMenu.setOnClickListener {
                showPopupMenu(rule)
            }
            root.setOnClickListener {
                onEdit(rule)
            }
        }

        private fun showPopupMenu(rule: AutoForwardRule) {
            val contextTheme = ContextThemeWrapper(activity, activity.getPopupMenuTheme())
            PopupMenu(contextTheme, binding.autoForwardRuleMenu, Gravity.END).apply {
                inflate(R.menu.menu_auto_forward_rule_item)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.edit_auto_forward_rule -> onEdit(rule)
                        R.id.delete_auto_forward_rule -> onDelete(rule)
                    }
                    true
                }
                show()
            }
        }
    }

    private fun buildRuleSummary(rule: AutoForwardRule): String {
        val matchType = when (rule.matchType) {
            AutoForwardMatchType.KEYWORDS -> activity.getString(R.string.auto_forward_keyword_match)
            AutoForwardMatchType.REGEX -> activity.getString(R.string.auto_forward_regex_match)
        }
        val destinationType = when (rule.destinationType) {
            AutoForwardDestinationType.SMS -> activity.getString(R.string.auto_forward_destination_sms)
            AutoForwardDestinationType.WEBHOOK -> activity.getString(R.string.auto_forward_destination_webhook)
        }
        val destination = when (rule.destinationType) {
            AutoForwardDestinationType.SMS -> rule.phoneNumber
            AutoForwardDestinationType.WEBHOOK -> rule.webhookUrl
        }
        return "$matchType -> $destinationType $destination"
    }
}
