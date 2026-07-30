package com.phequals7.muesli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "custom_words")
data class CustomWord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val replacement: String? = null,
    val matchingThreshold: Double = 0.85,
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
) {
    val targetWord: String
        get() = if (replacement.isNullOrBlank()) word else replacement
}
