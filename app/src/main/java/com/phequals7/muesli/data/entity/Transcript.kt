package com.phequals7.muesli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "transcripts")
data class Transcript(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionID: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val engineIdentifier: String
)
