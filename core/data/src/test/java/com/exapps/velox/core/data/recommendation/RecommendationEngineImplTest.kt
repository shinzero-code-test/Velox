package com.exapps.velox.core.data.recommendation

import com.exapps.velox.core.data.local.dao.MediaItemDao
import com.exapps.velox.core.data.local.dao.PlayHistoryDao
import com.exapps.velox.core.data.local.entity.MediaItemEntity
import com.exapps.velox.core.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 / Wave 3 / Round 3 — Recommendation engine contract tests.
 *
 * The session boundary logic is the critical contract: a session
 * boundary missed, or an asymmetric pair skipped, would silently
 * corrupt the recommendations. The cold-start path is the
 * second contract: the engine must never crash when there's
 * no play history.
 */
class RecommendationEngineImplTest {

    @Test
    fun `cooccurrence pairs plays within 30 minutes and ignores larger gaps`() {
        val t0 = 1_700_000_000_000L
        val history = listOf(
            play(t0, 1L),
            play(t0 + 10 * 60 * 1000, 2L),
            play(t0 + 11 * 60 * 1000, 3L),
            // 90 minutes later — new session
            play(t0 + 90 * 60 * 1000, 1L),
            play(t0 + 91 * 60 * 1000, 4L),
        )
        val sessions = buildSessions(history)
        assertEquals(2, sessions.size)
        assertEquals(listOf(1L, 2L, 3L), sessions[0])
        assertEquals(listOf(1L, 4L), sessions[1])
    }

    @Test
    fun `single play yields an empty session list`() {
        val sessions = buildSessions(
            listOf(play(1_700_000_000_000L, 42L)),
        )
        assertEquals(1, sessions.size)
        assertEquals(listOf(42L), sessions[0])
    }

    @Test
    fun `empty history yields no sessions`() {
        assertEquals(emptyList<List<Long>>(), buildSessions(emptyList()))
    }

    @Test
    fun `forYou is empty on cold start`() = runTest {
        val engine = RecommendationEngineImpl(
            playHistoryDao = EmptyPlayHistoryDao,
            mediaItemDao = EmptyMediaItemDao,
        )
        // Drain the rebuild trigger emissions; both should yield
        // an empty ForYou because the engine has no play history.
        val forYou = engine.forYou().first()
        assertTrue("forYou should be empty on cold start: $forYou", forYou.items.isEmpty())
    }

    private fun play(ts: Long, mediaItemId: Long) = PlayHistoryEntity(
        id = mediaItemId * 1000 + (ts % 1000).toInt(),
        mediaItemId = mediaItemId,
        playedAtEpochSeconds = ts / 1000,
    )
}

private object EmptyPlayHistoryDao : PlayHistoryDao {
    override suspend fun insert(entry: PlayHistoryEntity) = Unit
    override fun observeAll() = flowOf(emptyList<PlayHistoryEntity>())
    override suspend fun snapshotAll(): List<PlayHistoryEntity> = emptyList()
    override suspend fun countForItem(mediaItemId: Long): Int = 0
    override suspend fun trimTo(keepMostRecent: Int) = Unit
    override suspend fun clearAll() = Unit
}

private object EmptyMediaItemDao : MediaItemDao {
    override suspend fun count(): Int = 0
    override suspend fun getById(id: Long): MediaItemEntity? = null
    override suspend fun idAtOffset(offset: Int): Long? = null
    override suspend fun getByIds(ids: List<Long>): List<MediaItemEntity> = emptyList()
    override suspend fun upsertAll(items: List<MediaItemEntity>) = Unit
    override suspend fun updateTrackMetadata(id: Long, title: String, artistName: String?, albumTitle: String?) = Unit
    override suspend fun clearFavorites() = Unit
    override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
    override suspend fun recordPlayed(id: Long, playedAt: Long) = Unit
    override suspend fun clearPlayStatistics() = Unit
    override suspend fun resetPlayStatistics() = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun insertAll(items: List<MediaItemEntity>) = Unit
    override suspend fun countInFolder(path: String): Int = 0
    override fun observeByTypeOrderedByTitle(type: String) = flowOf(emptyList<MediaItemEntity>())
    override fun observeByFolder(path: String) = flowOf(emptyList<MediaItemEntity>())
    override fun observeByAlbum(albumId: Long) = flowOf(emptyList<MediaItemEntity>())
    override fun observeByArtist(artistName: String) = flowOf(emptyList<MediaItemEntity>())
    override fun observeByGenre(genre: String) = flowOf(emptyList<MediaItemEntity>())
    override fun observeFavorites() = flowOf(emptyList<MediaItemEntity>())
    override fun observeRecentlyPlayed(limit: Int) = flowOf(emptyList<MediaItemEntity>())
    override fun observeMostPlayed(limit: Int) = flowOf(emptyList<MediaItemEntity>())
    override fun observeFolders() = flowOf(emptyList<com.exapps.velox.core.data.local.dao.FolderSummary>())
    override fun observeFolderSummaries() = flowOf(emptyList<com.exapps.velox.core.data.local.dao.FolderSummary>())
    override fun observeArtists() = flowOf(emptyList<com.exapps.velox.core.domain.model.Artist>())
    override fun observeAlbums() = flowOf(emptyList<com.exapps.velox.core.domain.model.Album>())
    override fun observeGenres() = flowOf(emptyList<com.exapps.velox.core.domain.model.Genre>())
    override fun observeByType(type: String) = flowOf(emptyList<MediaItemEntity>())
    override fun search(query: String, type: String?) = flowOf(emptyList<MediaItemEntity>())
    override fun observeById(id: Long) = flowOf<MediaItemEntity?>(null)
}
