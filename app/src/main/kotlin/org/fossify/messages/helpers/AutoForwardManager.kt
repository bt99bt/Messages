package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.fossify.messages.extensions.autoForwardHistoryDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.messaging.sendMessageCompat
import org.fossify.messages.models.AutoForwardDestinationType
import org.fossify.messages.models.AutoForwardHistory
import org.fossify.messages.models.AutoForwardMatchType
import org.fossify.messages.models.AutoForwardRule
import org.fossify.messages.models.AutoForwardSimPolicy
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AutoForwardManager(private val context: Context) {
    private val json = Json { encodeDefaults = true }

    fun forwardIncomingSms(
        messageId: Long,
        threadId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSubscriptionId: Int
    ) {
        context.config.autoForwardRules
            .filter { it.enabled }
            .forEach { rule ->
                val matchResult = findMatch(rule, body) ?: return@forEach
                forwardMatchedRule(
                    rule = rule,
                    matchResult = matchResult,
                    messageId = messageId,
                    threadId = threadId,
                    sender = sender,
                    body = body,
                    receivedAt = receivedAt,
                    sourceSubscriptionId = sourceSubscriptionId
                )
            }
    }

    private fun findMatch(rule: AutoForwardRule, body: String): MatchResult? {
        return when (rule.matchType) {
            AutoForwardMatchType.KEYWORDS -> {
                rule.keywords
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && body.contains(it, ignoreCase = true) }
                    ?.let { MatchResult(matchedText = it, captures = emptyList()) }
            }

            AutoForwardMatchType.REGEX -> {
                runCatching { Regex(rule.regex) }.getOrNull()
                    ?.find(body)
                    ?.let { match ->
                        MatchResult(
                            matchedText = match.value,
                            captures = match.groupValues.drop(1)
                        )
                    }
            }
        }
    }

    private fun forwardMatchedRule(
        rule: AutoForwardRule,
        matchResult: MatchResult,
        messageId: Long,
        threadId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSubscriptionId: Int
    ) {
        val historyId = generateRandomId()
        val destination = when (rule.destinationType) {
            AutoForwardDestinationType.SMS -> rule.phoneNumber
            AutoForwardDestinationType.WEBHOOK -> rule.webhookUrl
        }
        val history = AutoForwardHistory(
            id = historyId,
            sourceMessageId = messageId,
            sourceThreadId = threadId,
            sourceSender = sender,
            sourceBodyPreview = body.take(AUTO_FORWARD_BODY_PREVIEW_LENGTH),
            sourceSubscriptionId = sourceSubscriptionId,
            ruleId = rule.id,
            ruleName = rule.name,
            destinationType = rule.destinationType.name,
            destination = destination,
            simPolicy = rule.simPolicy.name,
            usedSubscriptionId = null,
            matchedText = matchResult.matchedText,
            capturesJson = json.encodeToString(matchResult.captures),
            status = AutoForwardHistory.STATUS_QUEUED,
            errorMessage = "",
            createdAt = System.currentTimeMillis(),
            finishedAt = null
        )
        context.autoForwardHistoryDB.insert(history)

        when (rule.destinationType) {
            AutoForwardDestinationType.SMS -> forwardSms(
                rule = rule,
                historyId = historyId,
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                sourceSubscriptionId = sourceSubscriptionId
            )

            AutoForwardDestinationType.WEBHOOK -> forwardWebhook(
                rule = rule,
                matchResult = matchResult,
                historyId = historyId,
                messageId = messageId,
                threadId = threadId,
                sender = sender,
                body = body,
                receivedAt = receivedAt
            )
        }
    }

    private fun forwardSms(
        rule: AutoForwardRule,
        historyId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSubscriptionId: Int
    ) {
        var usedSubscriptionId: Int? = null
        try {
            usedSubscriptionId = resolveSubscriptionId(rule, sourceSubscriptionId)
            val forwardedText = "From: $sender\nTime: $receivedAt\n\n$body"
            context.sendMessageCompat(
                text = forwardedText,
                addresses = listOf(rule.phoneNumber),
                subId = usedSubscriptionId,
                attachments = emptyList()
            )
            updateHistory(historyId, AutoForwardHistory.STATUS_SUCCESS, "", usedSubscriptionId)
        } catch (e: Exception) {
            updateHistory(
                historyId = historyId,
                status = AutoForwardHistory.STATUS_FAILED,
                errorMessage = e.message ?: e.javaClass.simpleName,
                usedSubscriptionId = usedSubscriptionId
            )
        }
    }

    private fun forwardWebhook(
        rule: AutoForwardRule,
        matchResult: MatchResult,
        historyId: Long,
        messageId: Long,
        threadId: Long,
        sender: String,
        body: String,
        receivedAt: Long
    ) {
        try {
            val payload = buildJsonObject {
                put("sender", sender)
                put("body", body)
                put("receivedAt", receivedAt)
                put("threadId", threadId)
                put("messageId", messageId)
                put("ruleId", rule.id)
                put("ruleName", rule.name)
                put("matchedText", matchResult.matchedText)
                putJsonArray("captures") {
                    matchResult.captures.forEach { add(JsonPrimitive(it)) }
                }
            }.toString()
            postWebhook(rule.webhookUrl, payload)
            updateHistory(historyId, AutoForwardHistory.STATUS_SUCCESS, "", null)
        } catch (e: Exception) {
            updateHistory(
                historyId = historyId,
                status = AutoForwardHistory.STATUS_FAILED,
                errorMessage = e.message ?: e.javaClass.simpleName,
                usedSubscriptionId = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveSubscriptionId(rule: AutoForwardRule, sourceSubscriptionId: Int): Int {
        val activeSubscriptions = context.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()
        val subscriptionId = when (rule.simPolicy) {
            AutoForwardSimPolicy.INCOMING -> sourceSubscriptionId
            AutoForwardSimPolicy.DEFAULT -> SmsManager.getDefaultSmsSubscriptionId()
            AutoForwardSimPolicy.SPECIFIC -> rule.selectedSubscriptionId
        }

        if (subscriptionId < 0 || activeSubscriptions.none { it.subscriptionId == subscriptionId }) {
            throw IllegalStateException(context.getString(org.fossify.messages.R.string.auto_forward_sim_unavailable))
        }

        return subscriptionId
    }

    private fun postWebhook(webhookUrl: String, payload: String) {
        val connection = URL(webhookUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = WEBHOOK_TIMEOUT_MS
        connection.readTimeout = WEBHOOK_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(connection.outputStream).use {
            it.write(payload)
        }

        val responseCode = connection.responseCode
        connection.disconnect()
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode")
        }
    }

    private fun updateHistory(
        historyId: Long,
        status: String,
        errorMessage: String,
        usedSubscriptionId: Int?
    ) {
        context.autoForwardHistoryDB.updateStatus(
            id = historyId,
            status = status,
            errorMessage = errorMessage,
            usedSubscriptionId = usedSubscriptionId,
            finishedAt = System.currentTimeMillis()
        )
    }

    private data class MatchResult(
        val matchedText: String,
        val captures: List<String>
    )

    companion object {
        private const val WEBHOOK_TIMEOUT_MS = 10_000
    }
}
