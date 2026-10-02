package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.TrafficState

class Converters {
    @TypeConverter
    fun fromTrafficState(state: TrafficState): String = state.name

    @TypeConverter
    fun toTrafficState(value: String): TrafficState {
        return try {
            TrafficState.valueOf(value)
        } catch (_: Exception) {
            TrafficState.WAITING
        }
    }
}
