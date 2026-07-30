package com.phequals7.muesli.data.dao

import androidx.room.*
import com.phequals7.muesli.data.entity.DictationResult
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationResultDao {
    @Query("SELECT * FROM dictation_results ORDER BY createdAt DESC")
    fun getAllResultsFlow(): Flow<List<DictationResult>>

    @Query("SELECT * FROM dictation_results ORDER BY createdAt DESC")
    suspend fun getAllResults(): List<DictationResult>

    @Query("SELECT * FROM dictation_results WHERE requestID = :requestId LIMIT 1")
    suspend fun getResultForRequest(requestId: String): DictationResult?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: DictationResult)

    @Query("DELETE FROM dictation_results WHERE id = :id")
    suspend fun deleteResult(id: String)
}
