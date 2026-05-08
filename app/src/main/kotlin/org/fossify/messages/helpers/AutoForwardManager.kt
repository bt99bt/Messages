package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "MagicNumber", "UseCheckOrError")
class AutoForwardManager(private val context: Context) {
    private val json = Json { encodeDefaults = true }

    fun forwardIncomingSms(
        messageId: Long,
        threadId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSubscriptionId: Int,
        attachmentsSummary: String = ""
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
                    sourceSubscriptionId = sourceSubscriptionId,
                    attachmentsSummary = attachmentsSummary
                )
            }
    }

    private fun findMatch(rule: AutoForwardRule, body: String): MatchResult? {
        return when (rule.matchType) {
            AutoForwardMatchType.ALL -> MatchResult(matchedText = body, captures = emptyList())

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
        sourceSubscriptionId: Int,
        attachmentsSummary: String
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
            sourceBodyPreview = body.ifBlank { attachmentsSummary }.take(AUTO_FORWARD_BODY_PREVIEW_LENGTH),
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
                sourceSubscriptionId = sourceSubscriptionId,
                attachmentsSummary = attachmentsSummary
            )

            AutoForwardDestinationType.WEBHOOK -> forwardWebhook(
                rule = rule,
                historyId = historyId,
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                attachmentsSummary = attachmentsSummary
            )
        }
    }

    private fun forwardSms(
        rule: AutoForwardRule,
        historyId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSubscriptionId: Int,
        attachmentsSummary: String
    ) {
        var usedSubscriptionId: Int? = null
        try {
            usedSubscriptionId = resolveSubscriptionId(rule, sourceSubscriptionId)
            val forwardedText = buildForwardedSmsText(sender, body, receivedAt, attachmentsSummary)
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
        historyId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        attachmentsSummary: String
    ) {
        try {
            val payload = buildFeishuPostPayload(
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                attachmentsSummary = attachmentsSummary
            )
            if (payload.toByteArray(Charsets.UTF_8).size > FEISHU_MAX_PAYLOAD_BYTES) {
                throw IllegalStateException("Feishu payload exceeds 20 KB")
            }
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

    private fun buildFeishuPostPayload(
        sender: String,
        body: String,
        receivedAt: Long,
        attachmentsSummary: String
    ): String {
        return buildJsonObject {
            put("msg_type", "post")
            put(
                "content",
                buildJsonObject {
                    put(
                        "post",
                        buildJsonObject {
                            put(
                                "zh_cn",
                                buildJsonObject {
                                    put("title", "短信自动转发")
                                    put(
                                        "content",
                                        buildJsonArray {
                                            addPostLine("发件人：", sender)
                                            addPostLine("时间：", formatReceivedAt(receivedAt))
                                            if (body.isNotBlank()) {
                                                add(
                                                    buildJsonArray {
                                                        add(textElement("内容："))
                                                    }
                                                )
                                                add(
                                                    buildJsonArray {
                                                        add(textElement(body.take(FEISHU_MAX_BODY_CHARS)))
                                                    }
                                                )
                                            }
                                            if (attachmentsSummary.isNotBlank()) {
                                                add(
                                                    buildJsonArray {
                                                        add(textElement("附件："))
                                                    }
                                                )
                                                add(
                                                    buildJsonArray {
                                                        add(textElement(attachmentsSummary))
                                                    }
                                                )
                                            }
                                            if (body.isBlank() && attachmentsSummary.isBlank()) {
                                                add(
                                                    buildJsonArray {
                                                        add(textElement("内容："))
                                                    }
                                                )
                                                add(
                                                    buildJsonArray {
                                                        add(textElement("(无文本内容)"))
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.toString()
    }

    private fun buildForwardedSmsText(
        sender: String,
        body: String,
        receivedAt: Long,
        attachmentsSummary: String
    ): String {
        return buildString {
            appendLine("From: $sender")
            appendLine("Time: ${formatReceivedAt(receivedAt)}")
            appendLine()
            if (body.isNotBlank()) {
                appendLine(body)
            }
            if (attachmentsSummary.isNotBlank()) {
                if (body.isNotBlank()) {
                    appendLine()
                }
                append("Attachments: $attachmentsSummary")
            }
        }
    }

    private fun formatReceivedAt(receivedAt: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(receivedAt))
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addPostLine(
        label: String,
        value: String
    ) {
        add(
            buildJsonArray {
                add(textElement(label))
                add(textElement(value))
            }
        )
    }

    private fun textElement(text: String) = buildJsonObject {
        put("tag", "text")
        put("text", text)
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
        private const val FEISHU_MAX_PAYLOAD_BYTES = 20 * 1024
        private const val FEISHU_MAX_BODY_CHARS = 12_000
    }
}
