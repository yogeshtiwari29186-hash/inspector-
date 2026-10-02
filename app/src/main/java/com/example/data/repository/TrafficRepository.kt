package com.example.data.repository

import com.example.data.db.TrafficDao
import com.example.data.db.TrafficEntity
import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import com.example.data.model.TrafficRecord
import com.example.data.model.TrafficState
import com.example.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrafficRepository(
    private val trafficDao: TrafficDao,
    private val settingsRepository: SettingsRepository
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _records = MutableStateFlow<List<TrafficRecord>>(emptyList())
    val records: StateFlow<List<TrafficRecord>> = _records.asStateFlow()

    val pendingRequests: StateFlow<List<TrafficRecord>> = _records
        .map { list -> list.filter { it.state == TrafficState.WAITING || it.state == TrafficState.EDITING } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val dbHistoryFlow: Flow<List<TrafficEntity>> = trafficDao.getAllFlow()

    fun recordRequest(request: CapturedRequest): TrafficRecord {
        val record = TrafficRecord(
            request = request,
            state = request.state
        )
        synchronized(this) {
            val updated = listOf(record) + _records.value
            _records.value = updated
        }
        persistRecord(record)
        return record
    }

    fun updateModifiedRequest(requestId: String, modifiedRequest: CapturedRequest) {
        synchronized(this) {
            _records.value = _records.value.map { rec ->
                if (rec.id == requestId) {
                    val updated = rec.copy(
                        modifiedRequest = modifiedRequest,
                        state = TrafficState.MODIFIED
                    )
                    persistRecord(updated)
                    updated
                } else rec
            }
        }
    }

    fun recordResponse(requestId: String, response: CapturedResponse) {
        synchronized(this) {
            _records.value = _records.value.map { rec ->
                if (rec.id == requestId) {
                    val finalState = if (rec.modifiedRequest != null) TrafficState.MODIFIED else TrafficState.COMPLETED
                    val updated = rec.copy(
                        response = response,
                        state = finalState
                    )
                    persistRecord(updated)
                    updated
                } else rec
            }
        }
    }

    fun updateModifiedResponse(requestId: String, modifiedResponse: CapturedResponse) {
        synchronized(this) {
            _records.value = _records.value.map { rec ->
                if (rec.id == requestId) {
                    val updated = rec.copy(
                        modifiedResponse = modifiedResponse,
                        state = TrafficState.MODIFIED
                    )
                    persistRecord(updated)
                    updated
                } else rec
            }
        }
    }

    fun updateState(requestId: String, newState: TrafficState, errorMessage: String? = null) {
        synchronized(this) {
            _records.value = _records.value.map { rec ->
                if (rec.id == requestId) {
                    val updated = rec.copy(
                        state = newState,
                        errorMessage = errorMessage ?: rec.errorMessage
                    )
                    persistRecord(updated)
                    updated
                } else rec
            }
        }
    }

    fun getRecordById(id: String): TrafficRecord? {
        return _records.value.find { it.id == id }
    }

    suspend fun loadFromDb(id: String): TrafficRecord? {
        val entity = trafficDao.getById(id) ?: return null
        return entityToRecord(entity)
    }

    fun clearLiveTraffic() {
        _records.value = emptyList()
    }

    suspend fun clearAllHistory() {
        _records.value = emptyList()
        trafficDao.clearAll()
    }

    suspend fun deleteRecord(id: String) {
        synchronized(this) {
            _records.value = _records.value.filterNot { it.id == id }
        }
        trafficDao.deleteById(id)
    }

    private fun persistRecord(record: TrafficRecord) {
        if (!settingsRepository.settingsFlow.value.saveHistory) return
        repositoryScope.launch {
            try {
                val entity = recordToEntity(record)
                trafficDao.insert(entity)
            } catch (_: Exception) {
                // Ignore transient write errors
            }
        }
    }

    private fun recordToEntity(record: TrafficRecord): TrafficEntity {
        return TrafficEntity(
            id = record.id,
            timestamp = record.timestamp,
            method = record.request.method,
            url = record.request.url,
            originalRequestJson = JsonUtils.requestToJson(record.request),
            modifiedRequestJson = record.modifiedRequest?.let { JsonUtils.requestToJson(it) },
            originalResponseJson = record.response?.let { JsonUtils.responseToJson(it) },
            modifiedResponseJson = record.modifiedResponse?.let { JsonUtils.responseToJson(it) },
            state = record.state,
            durationMs = record.response?.durationMs ?: 0,
            statusCode = record.response?.statusCode,
            errorMessage = record.errorMessage
        )
    }

    fun entityToRecord(entity: TrafficEntity): TrafficRecord {
        val originalReq = JsonUtils.jsonToRequest(entity.originalRequestJson)
        val modifiedReq = entity.modifiedRequestJson?.let { JsonUtils.jsonToRequest(it) }
        val originalRes = entity.originalResponseJson?.let { JsonUtils.jsonToResponse(it) }
        val modifiedRes = entity.modifiedResponseJson?.let { JsonUtils.jsonToResponse(it) }

        return TrafficRecord(
            request = originalReq,
            response = originalRes,
            modifiedRequest = modifiedReq,
            modifiedResponse = modifiedRes,
            state = entity.state,
            errorMessage = entity.errorMessage
        )
    }
}
