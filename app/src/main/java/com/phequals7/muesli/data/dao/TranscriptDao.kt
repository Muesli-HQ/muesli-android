package com.phequals7.muesli.data.dao

import androidx.room.*
import com.phequals7.muesli.data.entity.Transcript
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE sessionID = :sessionId LIMIT 1")
    fun getTranscriptForSessionFlow(sessionId: String): Flow<Transcript?>

    @Query("SELECT * FROM transcripts WHERE sessionID = :sessionId LIMIT 1")
    suspend fun getTranscriptForSession(sessionId: String): Transcript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: Transcript)

    @Delete
    suspend fun deleteTranscript(transcript: Transcript)
}
