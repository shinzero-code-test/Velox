package com.exapps.velox.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.exapps.velox.core.domain.player.PlaybackPositionStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [PlaybackPositionStore]. One key per track
 * (`resume_pos_<mediaId>`); entries are deliberately never garbage-collected —
 * they're 8 bytes each and stale entries for deleted files are simply ignored at
 * read time (the resume logic validates against the item's duration).
 */
@Singleton
class PlaybackPositionStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PlaybackPositionStore {

    override suspend fun get(mediaItemId: Long): Long? =
        dataStore.data.first()[key(mediaItemId)]

    override suspend fun put(mediaItemId: Long, positionMs: Long) {
        dataStore.edit { it[key(mediaItemId)] = positionMs }
    }

    private fun key(mediaItemId: Long) = longPreferencesKey("resume_pos_$mediaItemId")
}
