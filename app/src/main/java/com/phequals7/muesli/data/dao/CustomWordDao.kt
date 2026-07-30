package com.phequals7.muesli.data.dao

import androidx.room.*
import com.phequals7.muesli.data.entity.CustomWord
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomWordDao {
    @Query("SELECT * FROM custom_words ORDER BY createdAt DESC")
    fun getAllCustomWordsFlow(): Flow<List<CustomWord>>

    @Query("SELECT * FROM custom_words ORDER BY createdAt DESC")
    suspend fun getAllCustomWords(): List<CustomWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomWord(word: CustomWord)

    @Update
    suspend fun updateCustomWord(word: CustomWord)

    @Query("DELETE FROM custom_words WHERE id = :id")
    suspend fun deleteCustomWordById(id: String)
}
