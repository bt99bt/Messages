package org.fossify.messages.dialogs

import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.DialogAutoForwardRuleBinding
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.helpers.generateRandomId
import org.fossify.messages.models.AutoForwardDestinationType
import org.fossify.messages.models.AutoForwardMatchType
import org.fossify.messages.models.AutoForwardRule
import org.fossify.messages.models.AutoForwardSimPolicy

class AutoForwardRuleDialog(
    private val activity: SimpleActivity,
    originalRule: AutoForwardRule? = null,
    private val callback: (AutoForwardRule) -> Unit
) {
    private val binding = DialogAutoForwardRuleBinding.inflate(activity.layoutInflater)
    private var matchType = originalRule?.matchType ?: AutoForwardMatchType.KEYWORDS
    private var destinationType =
        originalRule?.destinationType ?: AutoForwardDestinationType.SMS
    private var simPolicy = originalRule?.simPolicy ?: AutoForwardSimPolicy.INCOMING
    private var selectedSubscriptionId = originalRule?.selectedSubscriptionId ?: -1

    init {
        binding.apply {
            autoForwardRuleEnabled.isChecked = originalRule?.enabled ?: false
            autoForwardRuleName.setText(originalRule?.name.orEmpty())
            autoForwardRuleKeywords.setText(originalRule?.keywords?.joinToString("\n").orEmpty())
            autoForwardRuleRegex.setText(originalRule?.regex.orEmpty())
            autoForwardRulePhone.setText(originalRule?.phoneNumber.orEmpty())
            autoForwardRuleWebhook.setText(originalRule?.webhookUrl.orEmpty())
            autoForwardRuleMatchType.setOnClickListener { chooseMatchType() }
            autoForwardRuleDestinationType.setOnClickListener { chooseDestinationType() }
            autoForwardRuleSimPolicy.setOnClickListener { chooseSimPolicy() }
        }
        refreshDynamicFields()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.autoForwardRuleName)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        buildRule(originalRule)?.let { rule ->
                            callback(rule)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun chooseMatchType() {
        val items = arrayListOf(
            RadioItem(
                AutoForwardMatchType.ALL.ordinal,
                activity.getString(R.string.auto_forward_all_match)
            ),
            RadioItem(
                AutoForwardMatchType.KEYWORDS.ordinal,
                activity.getString(R.string.auto_forward_keyword_match)
            ),
            RadioItem(
                AutoForwardMatchType.REGEX.ordinal,
                activity.getString(R.string.auto_forward_regex_match)
            )
        )
        RadioGroupDialog(activity, items, matchType.ordinal) {
            matchType = AutoForwardMatchType.entries[it as Int]
            refreshDynamicFields()
        }
    }

    private fun chooseDestinationType() {
        val items = arrayListOf(
            RadioItem(
                AutoForwardDestinationType.SMS.ordinal,
                activity.getString(R.string.auto_forward_destination_sms)
            ),
            RadioItem(
                AutoForwardDestinationType.WEBHOOK.ordinal,
                activity.getString(R.string.auto_forward_destination_webhook)
            )
        )
        RadioGroupDialog(activity, items, destinationType.ordinal) {
            destinationType = AutoForwardDestinationType.entries[it as Int]
            refreshDynamicFields()
        }
    }

    @SuppressLint("MissingPermission")
    private fun chooseSimPolicy() {
        val items = arrayListOf(
            RadioItem(
                AutoForwardSimPolicy.INCOMING.ordinal,
                activity.getString(R.string.auto_forward_sim_incoming)
            ),
            RadioItem(
                AutoForwardSimPolicy.DEFAULT.ordinal,
                activity.getString(R.string.auto_forward_sim_default)
            )
        )
        val activeSubscriptions = activity.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()
        activeSubscriptions.forEach {
            items.add(
                RadioItem(
                    SPECIFIC_SIM_OFFSET + it.subscriptionId,
                    "${activity.getString(R.string.auto_forward_sim_specific)}: ${it.displayName}"
                )
            )
        }

        val checkedItemId = if (simPolicy == AutoForwardSimPolicy.SPECIFIC) {
            SPECIFIC_SIM_OFFSET + selectedSubscriptionId
        } else {
            simPolicy.ordinal
        }
        RadioGroupDialog(activity, items, checkedItemId) {
            val id = it as Int
            if (id >= SPECIFIC_SIM_OFFSET) {
                simPolicy = AutoForwardSimPolicy.SPECIFIC
                selectedSubscriptionId = id - SPECIFIC_SIM_OFFSET
            } else {
                simPolicy = AutoForwardSimPolicy.entries[id]
                selectedSubscriptionId = -1
            }
            refreshDynamicFields()
        }
    }

    private fun refreshDynamicFields() = binding.apply {
        autoForwardRuleMatchType.text = activity.getString(
            R.string.auto_forward_match_type,
            getMatchTypeText()
        )
        autoForwardRuleDestinationType.text = activity.getString(
            R.string.auto_forward_destination_type,
            getDestinationTypeText()
        )
        autoForwardRuleSimPolicy.text = activity.getString(
            R.string.auto_forward_sim_policy,
            getSimPolicyText()
        )
        autoForwardRuleKeywordsHint.visibility =
            if (matchType == AutoForwardMatchType.KEYWORDS) View.VISIBLE else View.GONE
        autoForwardRuleRegexHint.visibility =
            if (matchType == AutoForwardMatchType.REGEX) View.VISIBLE else View.GONE
        autoForwardRulePhoneHint.visibility =
            if (destinationType == AutoForwardDestinationType.SMS) View.VISIBLE else View.GONE
        autoForwardRuleSimPolicy.visibility =
            if (destinationType == AutoForwardDestinationType.SMS) View.VISIBLE else View.GONE
        autoForwardRuleWebhookHint.visibility =
            if (destinationType == AutoForwardDestinationType.WEBHOOK) View.VISIBLE else View.GONE
        autoForwardRulePrivacyNotice.visibility =
            if (destinationType == AutoForwardDestinationType.WEBHOOK) View.VISIBLE else View.GONE
    }

    private fun buildRule(originalRule: AutoForwardRule?): AutoForwardRule? {
        val name = binding.autoForwardRuleName.value.trim()
        val keywords = binding.autoForwardRuleKeywords.value
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val regex = binding.autoForwardRuleRegex.value.trim()
        val phoneNumber = binding.autoForwardRulePhone.value.trim()
        val webhookUrl = binding.autoForwardRuleWebhook.value.trim()

        if (name.isEmpty()) {
            activity.toast(R.string.name)
            return null
        }
        when (matchType) {
            AutoForwardMatchType.ALL -> Unit
            AutoForwardMatchType.KEYWORDS -> if (keywords.isEmpty()) {
                activity.toast(R.string.auto_forward_missing_matcher)
                return null
            }
            AutoForwardMatchType.REGEX -> {
                if (regex.isEmpty()) {
                    activity.toast(R.string.auto_forward_missing_matcher)
                    return null
                }
                if (runCatching { Regex(regex) }.isFailure) {
                    activity.toast(R.string.auto_forward_invalid_regex)
                    return null
                }
            }
        }
        if (destinationType == AutoForwardDestinationType.SMS && phoneNumber.isEmpty()) {
            activity.toast(R.string.auto_forward_missing_destination)
            return null
        }
        if (destinationType == AutoForwardDestinationType.WEBHOOK) {
            if (webhookUrl.isEmpty()) {
                activity.toast(R.string.auto_forward_missing_destination)
                return null
            }
            if (!webhookUrl.startsWith("https://")) {
                activity.toast(R.string.auto_forward_invalid_webhook)
                return null
            }
        }

        return AutoForwardRule(
            id = originalRule?.id ?: generateRandomId(),
            name = name,
            enabled = binding.autoForwardRuleEnabled.isChecked,
            matchType = matchType,
            keywords = keywords,
            regex = regex,
            destinationType = destinationType,
            phoneNumber = phoneNumber,
            webhookUrl = webhookUrl,
            simPolicy = simPolicy,
            selectedSubscriptionId = selectedSubscriptionId
        )
    }

    private fun getMatchTypeText() = activity.getString(
        when (matchType) {
            AutoForwardMatchType.ALL -> R.string.auto_forward_all_match
            AutoForwardMatchType.KEYWORDS -> R.string.auto_forward_keyword_match
            AutoForwardMatchType.REGEX -> R.string.auto_forward_regex_match
        }
    )

    private fun getDestinationTypeText() = activity.getString(
        when (destinationType) {
            AutoForwardDestinationType.SMS -> R.string.auto_forward_destination_sms
            AutoForwardDestinationType.WEBHOOK -> R.string.auto_forward_destination_webhook
        }
    )

    private fun getSimPolicyText() = activity.getString(
        when (simPolicy) {
            AutoForwardSimPolicy.INCOMING -> R.string.auto_forward_sim_incoming
            AutoForwardSimPolicy.DEFAULT -> R.string.auto_forward_sim_default
            AutoForwardSimPolicy.SPECIFIC -> R.string.auto_forward_sim_specific
        }
    )

    companion object {
        private const val SPECIFIC_SIM_OFFSET = 10_000
    }
}
