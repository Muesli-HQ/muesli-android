package com.phequals7.muesli.data.dao

import androidx.room.*
import com.phequals7.muesli.data.entity.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    fun getAllSessionsFlow(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<RecordingSession>

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): RecordingSession?

    @Query("SELECT * FROM recording_sessions WHERE requestID = :requestId LIMIT 1")
    suspend fun getSessionByRequestId(requestId: String): RecordingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSession)

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Delete
    suspend fun deleteSession(session: RecordingSession)
}
