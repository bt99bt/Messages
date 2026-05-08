package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_forward_history")
data class AutoForwardHistory(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: Long,
    @ColumnInfo(name = "source_thread_id") val sourceThreadId: Long,
    @ColumnInfo(name = "source_sender") val sourceSender: String,
    @ColumnInfo(name = "source_body_preview") val sourceBodyPreview: String,
    @ColumnInfo(name = "source_subscription_id") val sourceSubscriptionId: Int,
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "rule_name") val ruleName: String,
    @ColumnInfo(name = "destination_type") val destinationType: String,
    @ColumnInfo(name = "destination") val destination: String,
    @ColumnInfo(name = "sim_policy") val simPolicy: String,
    @ColumnInfo(name = "used_subscription_id") val usedSubscriptionId: Int?,
    @ColumnInfo(name = "matched_text") val matchedText: String,
    @ColumnInfo(name = "captures_json") val capturesJson: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}
