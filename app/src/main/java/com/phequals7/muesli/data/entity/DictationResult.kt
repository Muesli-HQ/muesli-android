package com.phequals7.muesli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "dictation_results")
data class DictationResult(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val requestID: String = UUID.randomUUID().toString(),
    val sessionID: String? = null,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val engineIdentifier: String,
    /** Recording duration, used for the WPM stat. 0 for legacy rows. */
    val durationMs: Long = 0
)
