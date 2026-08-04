package com.phequals7.muesli.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phequals7.muesli.data.dao.CustomWordDao
import com.phequals7.muesli.data.dao.DictationResultDao
import com.phequals7.muesli.data.dao.RecordingSessionDao
import com.phequals7.muesli.data.dao.TranscriptDao
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.data.entity.Transcript

@Database(
    entities = [
        CustomWord::class,
        DictationResult::class,
        RecordingSession::class,
        Transcript::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customWordDao(): CustomWordDao
    abstract fun dictationResultDao(): DictationResultDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds DictationResult.durationMs (keeps existing rows). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dictation_results ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds RecordingSession.templateId + manualNotes for meeting notes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN templateId TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN manualNotes TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds RecordingSession.summaryText + summaryState for AI summaries. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN summaryState TEXT NOT NULL DEFAULT 'notStarted'")
            }
        }

        /** Exposed for migration tests. */
        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "muesli_database"
                )
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigration() // Useful during scaffolding iteration
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
