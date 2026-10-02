package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TrafficState
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrafficEntity)

    @Update
    suspend fun update(entity: TrafficEntity)

    @Query("SELECT * FROM traffic_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TrafficEntity>>

    @Query("SELECT * FROM traffic_records WHERE id = :id")
    suspend fun getById(id: String): TrafficEntity?

    @Query("SELECT * FROM traffic_records WHERE state = :state ORDER BY timestamp DESC")
    fun getByStateFlow(state: TrafficState): Flow<List<TrafficEntity>>

    @Query("DELETE FROM traffic_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM traffic_records")
    suspend fun clearAll()
}
