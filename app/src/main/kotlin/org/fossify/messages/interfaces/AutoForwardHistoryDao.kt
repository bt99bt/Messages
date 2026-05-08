package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.AutoForwardHistory

@Dao
interface AutoForwardHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(history: AutoForwardHistory)

    @Query("SELECT * FROM auto_forward_history ORDER BY created_at DESC")
    fun getAll(): List<AutoForwardHistory>

    @Query(
        "UPDATE auto_forward_history SET status = :status, error_message = :errorMessage, " +
            "used_subscription_id = :usedSubscriptionId, finished_at = :finishedAt WHERE id = :id"
    )
    fun updateStatus(
        id: Long,
        status: String,
        errorMessage: String,
        usedSubscriptionId: Int?,
        finishedAt: Long
    )

    @Query("DELETE FROM auto_forward_history")
    fun deleteAll()
}
