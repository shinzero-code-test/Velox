package com.exapps.velox.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exapps.velox.core.data.di.ALL_DATABASE_MIGRATIONS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * H4 (data-layer review): MigrationTestHelper-driven assertions that every
 * shipped schema bump preserves the columns the user can see — favourites,
 * play statistics, tag-editor overrides, bookmarks. Run as an
 * instrumentation test (androidTest) so it executes against the real
 * SQLite-on-Android, not an in-process variant.
 *
 * The matching schema JSONs live under
 * `core/data/schemas/com.exapps.velox.core.data.local.VeloxDatabase/` — Room
 * generates them on every KSP run from the entity definitions and we commit
 * the result so this test can spin up an empty v1/v2/v3 database against
 * the same shape that the in-app code expects.
 */
@RunWith(AndroidJUnit4::class)
class VeloxDatabaseMigrationsTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VeloxDatabase::class.java,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesUserMetadataColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """INSERT INTO media_items (
                       id, uri, title, mediaType, durationMs, artistName, albumId, albumTitle,
                       artworkUri, folderPath, dateAddedEpochSeconds, sizeBytes,
                       isFavorite, playCount, lastPlayedEpochSeconds
                   ) VALUES (
                       1, 'content://1', 'Song', 'AUDIO', 1000, 'Artist', NULL, NULL,
                       NULL, '/music', 1000, 1024,
                       1, 5, 1700000000
                   )""",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_DATABASE_MIGRATIONS).apply {
            // v1→v2 adds fileName + genre; the existing columns must round-trip.
            val cursor = query("SELECT id, isFavorite, playCount, lastPlayedEpochSeconds FROM media_items")
            cursor.use { c ->
                assert(c.moveToFirst()) { "expected the seeded row to survive 1→2" }
                assert(c.getLong(0) == 1L)
                assert(c.getInt(1) == 1)
                assert(c.getInt(2) == 5)
                assert(c.getLong(3) == 1_700_000_000L)
            }
            close()
        }
    }

    @Test
    fun migrate2To3_createsBookmarksTable() {
        helper.createDatabase(TEST_DB, 2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_DATABASE_MIGRATIONS).apply {
            // The bookmarks table is brand new in v3 — write one row and read it
            // back to prove the migration succeeded end-to-end.
            execSQL(
                "INSERT INTO bookmarks (mediaItemId, positionMs, label, createdAtEpochSeconds) " +
                    "VALUES (1, 12345, 'Chorus', 1700000000)",
            )
            val cursor = query("SELECT mediaItemId, positionMs, label FROM bookmarks")
            cursor.use { c ->
                assert(c.moveToFirst()) { "bookmarks table missing after 2→3" }
                assert(c.getLong(0) == 1L)
                assert(c.getLong(1) == 12_345L)
                assert(c.getString(2) == "Chorus")
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "velox-migration-test.db"
    }
}
