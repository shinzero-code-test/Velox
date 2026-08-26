package com.exapps.velox.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exapps.velox.core.data.local.entity.AlbumEntity
import com.exapps.velox.core.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: Long): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    /** C2 (data-layer review): skip on empty list — `NOT IN ()` is invalid SQL. */
    @Query("DELETE FROM albums WHERE id NOT IN (:currentIds)")
    suspend fun deleteMissing(currentIds: List<Long>)
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: Long): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    /** C2 (data-layer review): skip on empty list — `NOT IN ()` is invalid SQL. */
    @Query("DELETE FROM artists WHERE id NOT IN (:currentIds)")
    suspend fun deleteMissing(currentIds: List<Long>)
}
