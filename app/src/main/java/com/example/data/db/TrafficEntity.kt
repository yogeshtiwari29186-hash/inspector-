package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.TrafficState

@Entity(tableName = "traffic_records")
data class TrafficEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val method: String,
    val url: String,
    val originalRequestJson: String,
    val modifiedRequestJson: String?,
    val originalResponseJson: String?,
    val modifiedResponseJson: String?,
    val state: TrafficState,
    val durationMs: Long,
    val statusCode: Int?,
    val errorMessage: String?
)
