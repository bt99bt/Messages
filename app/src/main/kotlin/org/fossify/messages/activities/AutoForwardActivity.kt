package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.AutoForwardHistoryAdapter
import org.fossify.messages.adapters.AutoForwardRulesAdapter
import org.fossify.messages.databinding.ActivityAutoForwardBinding
import org.fossify.messages.dialogs.AutoForwardRuleDialog
import org.fossify.messages.extensions.autoForwardHistoryDB
import org.fossify.messages.extensions.config
import org.fossify.messages.models.AutoForwardHistory
import org.fossify.messages.models.AutoForwardRule

class AutoForwardActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAutoForwardBinding::inflate)
    private var currentTab = TAB_RULES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.autoForwardList))
        setupMaterialScrollListener(
            scrollingView = binding.autoForwardList,
            topAppBar = binding.autoForwardAppbar
        )
        setupOptionsMenu()
        setupTabs()
        updateTextColors(binding.autoForwardWrapper)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.autoForwardAppbar, NavigationIcon.Arrow)
        refreshCurrentTab()
    }

    private fun setupOptionsMenu() {
        binding.autoForwardToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_auto_forward_rule -> {
                    addOrEditRule()
                    true
                }

                R.id.clear_auto_forward_history -> {
                    askClearHistory()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupTabs() = binding.apply {
        autoForwardRulesTab.setOnClickListener {
            currentTab = TAB_RULES
            refreshCurrentTab()
        }
        autoForwardHistoryTab.setOnClickListener {
            currentTab = TAB_HISTORY
            refreshCurrentTab()
        }
    }

    private fun refreshCurrentTab() {
        updateTabs()
        if (currentTab == TAB_RULES) {
            refreshRules()
        } else {
            refreshHistory()
        }
    }

    private fun updateTabs() = binding.apply {
        val primaryColor = getProperPrimaryColor()
        val textColor = getProperTextColor()
        autoForwardRulesTab.setTextColor(if (currentTab == TAB_RULES) primaryColor else textColor)
        autoForwardHistoryTab.setTextColor(if (currentTab == TAB_HISTORY) primaryColor else textColor)
        autoForwardToolbar.menu.findItem(R.id.add_auto_forward_rule).isVisible = currentTab == TAB_RULES
        autoForwardToolbar.menu.findItem(R.id.clear_auto_forward_history).isVisible = currentTab == TAB_HISTORY
    }

    private fun refreshRules() {
        val rules = config.autoForwardRules
        binding.autoForwardList.adapter = AutoForwardRulesAdapter(
            activity = this,
            rules = rules,
            onToggle = { rule, enabled ->
                config.addOrUpdateAutoForwardRule(rule.copy(enabled = enabled))
                refreshRules()
            },
            onEdit = { rule ->
                addOrEditRule(rule)
            },
            onDelete = { rule ->
                config.removeAutoForwardRule(rule.id)
                refreshRules()
            }
        )
        binding.autoForwardPlaceholder.text = getString(R.string.auto_forward_no_rules)
        binding.autoForwardPlaceholder.beVisibleIf(rules.isEmpty())
    }

    private fun refreshHistory() {
        ensureBackgroundThread {
            val history = autoForwardHistoryDB.getAll()
            runOnUiThread {
                binding.autoForwardList.adapter = AutoForwardHistoryAdapter(
                    activity = this,
                    history = history,
                    onClick = { showHistoryDetails(it) }
                )
                binding.autoForwardPlaceholder.text = getString(R.string.auto_forward_no_history)
                binding.autoForwardPlaceholder.beVisibleIf(history.isEmpty())
            }
        }
    }

    private fun addOrEditRule(rule: AutoForwardRule? = null) {
        AutoForwardRuleDialog(this, rule) {
            config.addOrUpdateAutoForwardRule(it)
            refreshRules()
        }
    }

    private fun askClearHistory() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.auto_forward_clear_history_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            ensureBackgroundThread {
                autoForwardHistoryDB.deleteAll()
                runOnUiThread {
                    refreshHistory()
                }
            }
        }
    }

    private fun showHistoryDetails(history: AutoForwardHistory) {
        val message = buildString {
            appendLine("${getString(R.string.auto_forward_history)}: ${getStatusText(history.status)}")
            appendLine("${getString(R.string.auto_forward_rules)}: ${history.ruleName}")
            appendLine("From: ${history.sourceSender}")
            appendLine("To: ${history.destinationType.lowercase()} ${history.destination}")
            appendLine("SIM: ${history.usedSubscriptionId ?: history.sourceSubscriptionId}")
            appendLine("Matched: ${history.matchedText}")
            if (history.capturesJson.isNotEmpty() && history.capturesJson != "[]") {
                appendLine("Captures: ${history.capturesJson}")
            }
            if (history.errorMessage.isNotEmpty()) {
                appendLine("Error: ${history.errorMessage}")
            }
            appendLine()
            append(history.sourceBodyPreview)
        }

        getAlertDialogBuilder()
            .setTitle(R.string.auto_forward_history_details)
            .setMessage(message)
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .show()
    }

    private fun getStatusText(status: String) = getString(
        when (status) {
            AutoForwardHistory.STATUS_SUCCESS -> R.string.auto_forward_status_success
            AutoForwardHistory.STATUS_FAILED -> R.string.auto_forward_status_failed
            else -> R.string.auto_forward_status_queued
        }
    )

    companion object {
        private const val TAB_RULES = 0
        private const val TAB_HISTORY = 1
    }
}
