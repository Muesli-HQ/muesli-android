package com.phequals7.muesli.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration scaffold: hand-builds a v1-shaped database (as Room v1 wrote
 * it), runs all migrations to the current version via the real
 * [AppDatabase] open path, and asserts data survives with new columns
 * defaulted. Extend with per-version fixtures as the schema evolves.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV1Database() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                """CREATE TABLE dictation_results (
                     id TEXT NOT NULL PRIMARY KEY,
                     requestID TEXT NOT NULL,
                     sessionID TEXT,
                     text TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     engineIdentifier TEXT NOT NULL
                   )"""
            )
            db.execSQL(
                """CREATE TABLE recording_sessions (
                     id TEXT NOT NULL PRIMARY KEY,
                     requestID TEXT,
                     kind TEXT NOT NULL,
                     title TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     startedAt INTEGER,
                     endedAt INTEGER,
                     phase TEXT NOT NULL,
                     audioFileName TEXT,
                     transcriptID TEXT,
                     engineIdentifier TEXT,
                     errorMessage TEXT
                   )"""
            )
            db.execSQL(
                """CREATE TABLE transcripts (
                     id TEXT NOT NULL PRIMARY KEY,
                     sessionID TEXT NOT NULL,
                     text TEXT NOT NULL,
                     createdAt INTEGER NOT NULL,
                     engineIdentifier TEXT NOT NULL
                   )"""
            )
            db.execSQL(
                """CREATE TABLE custom_words (
                     id TEXT NOT NULL PRIMARY KEY,
                     word TEXT NOT NULL,
                     replacement TEXT,
                     matchingThreshold REAL NOT NULL,
                     createdAt INTEGER NOT NULL,
                     isEnabled INTEGER NOT NULL
                   )"""
            )
            db.execSQL("INSERT INTO dictation_results VALUES ('d1','r1',NULL,'hello from v1',1700000000000,'parakeet')")
            db.execSQL("INSERT INTO recording_sessions VALUES ('s1',NULL,'meeting','v1 meeting',1700000000000,NULL,NULL,'completed',NULL,NULL,'parakeet',NULL)")
            // Without this Room sees user_version=0, treats the DB as fresh,
            // and fails schema validation instead of running migrations.
            db.version = 1
        }
    }

    @Test
    fun migrate1ToLatest_preservesDataAndAddsColumns() {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

        // Opening the DB forces the migration chain v1 -> v4.
        val results = runBlocking { db.dictationResultDao().getAllResults() }
        assertEquals(1, results.size)
        assertEquals("hello from v1", results[0].text)
        assertEquals(0L, results[0].durationMs) // MIGRATION_1_2 default

        val session = runBlocking { db.recordingSessionDao().getSessionById("s1") }
        assertNotNull(session)
        assertEquals("v1 meeting", session!!.title)
        assertEquals("general", session.templateId) // MIGRATION_2_3 default
        assertEquals("", session.manualNotes)
        assertEquals("", session.summaryText) // MIGRATION_3_4 default
        assertEquals("notStarted", session.summaryState)

        db.close()
    }
}
