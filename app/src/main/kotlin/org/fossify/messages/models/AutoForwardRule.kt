package org.fossify.messages.models

import kotlinx.serialization.Serializable

@Serializable
data class AutoForwardRule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val matchType: AutoForwardMatchType,
    val keywords: List<String> = emptyList(),
    val regex: String = "",
    val destinationType: AutoForwardDestinationType,
    val phoneNumber: String = "",
    val webhookUrl: String = "",
    val simPolicy: AutoForwardSimPolicy = AutoForwardSimPolicy.INCOMING,
    val selectedSubscriptionId: Int = -1
)

@Serializable
enum class AutoForwardMatchType {
    KEYWORDS,
    REGEX
}

@Serializable
enum class AutoForwardDestinationType {
    SMS,
    WEBHOOK
}

@Serializable
enum class AutoForwardSimPolicy {
    INCOMING,
    DEFAULT,
    SPECIFIC
}
