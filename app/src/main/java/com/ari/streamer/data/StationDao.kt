package com.ari.streamer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT * FROM stations WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getStationsByCategory(categoryId: Long): Flow<List<Station>>

    @Query("SELECT * FROM stations ORDER BY orderIndex ASC")
    fun getAllStations(): Flow<List<Station>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: Station): Long

    @Update
    suspend fun updateStation(station: Station)

    @Delete
    suspend fun deleteStation(station: Station)

    @Query("DELETE FROM stations")
    suspend fun clearAllStations()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()
}
