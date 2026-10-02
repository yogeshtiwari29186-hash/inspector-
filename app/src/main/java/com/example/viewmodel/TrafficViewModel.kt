package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.DevTrafficInspectorApp
import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import com.example.data.model.TrafficRecord
import com.example.data.model.TrafficState
import com.example.service.InspectorForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TrafficMetrics(
    val totalRequests: Int = 0,
    val totalResponses: Int = 0,
    val waitingCount: Int = 0,
    val forwardedCount: Int = 0,
    val modifiedCount: Int = 0,
    val blockedCount: Int = 0,
    val errorCount: Int = 0
)

enum class TestScenario {
    LOGIN_POST,
    PROFILE_GET,
    UPDATE_PUT,
    DELETE_API
}

class TrafficViewModel : ViewModel() {

    private val app = DevTrafficInspectorApp.instance
    private val trafficRepo = app.trafficRepository
    private val proxyServer = app.proxyServer

    val trafficRecords: StateFlow<List<TrafficRecord>> = trafficRepo.records
    val pendingRequests: StateFlow<List<TrafficRecord>> = trafficRepo.pendingRequests

    private val _isProxyRunning = MutableStateFlow(proxyServer.isRunning())
    val isProxyRunning: StateFlow<Boolean> = _isProxyRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(proxyServer.isPaused())
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _selectedRecord = MutableStateFlow<TrafficRecord?>(null)
    val selectedRecord: StateFlow<TrafficRecord?> = _selectedRecord.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val metrics: StateFlow<TrafficMetrics> = trafficRepo.records.map { list ->
        var responses = 0
        var waiting = 0
        var forwarded = 0
        var modified = 0
        var blocked = 0
        var errors = 0

        list.forEach { rec ->
            if (rec.response != null || rec.modifiedResponse != null) responses++
            when (rec.state) {
                TrafficState.WAITING, TrafficState.EDITING -> waiting++
                TrafficState.FORWARDED, TrafficState.COMPLETED -> {
                    if (rec.isModified) modified++ else forwarded++
                }
                TrafficState.MODIFIED -> modified++
                TrafficState.BLOCKED -> blocked++
                TrafficState.ERROR -> errors++
            }
        }

        TrafficMetrics(
            totalRequests = list.size,
            totalResponses = responses,
            waitingCount = waiting,
            forwardedCount = forwarded,
            modifiedCount = modified,
            blockedCount = blocked,
            errorCount = errors
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, TrafficMetrics())

    fun selectRecord(id: String) {
        viewModelScope.launch {
            val record = trafficRepo.getRecordById(id) ?: trafficRepo.loadFromDb(id)
            _selectedRecord.value = record
        }
    }

    fun clearSelectedRecord() {
        _selectedRecord.value = null
    }

    fun startProxy(context: Context) {
        InspectorForegroundService.start(context)
        _isProxyRunning.value = true
        _isPaused.value = false
    }

    fun stopProxy(context: Context) {
        InspectorForegroundService.stop(context)
        _isProxyRunning.value = false
        _isPaused.value = false
    }

    fun pauseProxy(context: Context) {
        InspectorForegroundService.pause(context)
        _isPaused.value = true
    }

    fun resumeProxy(context: Context) {
        InspectorForegroundService.resume(context)
        _isPaused.value = false
    }

    fun forwardOriginalRequest(record: TrafficRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateState(record.id, TrafficState.FORWARDED)
            proxyServer.forwardRequest(record.id, record.request)
        }
    }

    fun saveAndForwardRequest(requestId: String, modifiedRequest: CapturedRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateModifiedRequest(requestId, modifiedRequest)
            trafficRepo.updateState(requestId, TrafficState.FORWARDED)
            proxyServer.forwardRequest(requestId, modifiedRequest)
            // Refresh selected record
            _selectedRecord.value = trafficRepo.getRecordById(requestId)
        }
    }

    fun blockRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateState(requestId, TrafficState.BLOCKED)
            proxyServer.blockRequest(requestId)
            _selectedRecord.value = trafficRepo.getRecordById(requestId)
        }
    }

    fun returnOriginalResponse(record: TrafficRecord) {
        val res = record.response ?: return
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateState(record.id, TrafficState.COMPLETED)
            proxyServer.returnResponse(record.id, res)
        }
    }

    fun saveAndReturnResponse(requestId: String, modifiedResponse: CapturedResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateModifiedResponse(requestId, modifiedResponse)
            trafficRepo.updateState(requestId, TrafficState.MODIFIED)
            proxyServer.returnResponse(requestId, modifiedResponse)
            _selectedRecord.value = trafficRepo.getRecordById(requestId)
        }
    }

    fun blockResponse(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trafficRepo.updateState(requestId, TrafficState.BLOCKED)
            proxyServer.blockResponse(requestId)
            _selectedRecord.value = trafficRepo.getRecordById(requestId)
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            trafficRepo.deleteRecord(id)
            if (_selectedRecord.value?.id == id) {
                _selectedRecord.value = null
            }
        }
    }

    fun clearLiveTraffic() {
        trafficRepo.clearLiveTraffic()
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            trafficRepo.clearAllHistory()
            _selectedRecord.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun sendTestRequest(scenario: TestScenario) {
        viewModelScope.launch(Dispatchers.IO) {
            val reqId = UUID.randomUUID().toString().take(8)
            val settings = app.settingsRepository.settingsFlow.value

            val testRequest = when (scenario) {
                TestScenario.LOGIN_POST -> CapturedRequest(
                    id = reqId,
                    timestamp = System.currentTimeMillis(),
                    method = "POST",
                    url = "https://httpbin.org/post",
                    scheme = "https",
                    host = "httpbin.org",
                    port = 443,
                    path = "/post",
                    queryParameters = emptyMap(),
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer dev_token_xyz987",
                        "User-Agent" to "DevTraffic-Inspector-TestClient/1.0"
                    ),
                    body = "{\n  \"email\": \"developer@test.local\",\n  \"password\": \"SecretPass123!\",\n  \"client_version\": \"2.4.0\"\n}",
                    contentType = "application/json",
                    contentLength = 98L,
                    state = if (settings.interceptRequests || _isPaused.value) TrafficState.WAITING else TrafficState.FORWARDED
                )
                TestScenario.PROFILE_GET -> CapturedRequest(
                    id = reqId,
                    timestamp = System.currentTimeMillis(),
                    method = "GET",
                    url = "https://httpbin.org/get?user_id=usr_4402&include_metadata=true",
                    scheme = "https",
                    host = "httpbin.org",
                    port = 443,
                    path = "/get",
                    queryParameters = mapOf("user_id" to "usr_4402", "include_metadata" to "true"),
                    headers = mapOf(
                        "Accept" to "application/json",
                        "X-Dev-Session" to "sess_live_test_001"
                    ),
                    body = null,
                    contentType = null,
                    contentLength = null,
                    state = if (settings.interceptRequests || _isPaused.value) TrafficState.WAITING else TrafficState.FORWARDED
                )
                TestScenario.UPDATE_PUT -> CapturedRequest(
                    id = reqId,
                    timestamp = System.currentTimeMillis(),
                    method = "PUT",
                    url = "https://httpbin.org/put",
                    scheme = "https",
                    host = "httpbin.org",
                    port = 443,
                    path = "/put",
                    queryParameters = emptyMap(),
                    headers = mapOf("Content-Type" to "application/json"),
                    body = "{\n  \"status\": \"active\",\n  \"quota_limit\": 50000,\n  \"feature_flags\": [\"v2_ui\", \"dark_mode\"]\n}",
                    contentType = "application/json",
                    contentLength = 88L,
                    state = if (settings.interceptRequests || _isPaused.value) TrafficState.WAITING else TrafficState.FORWARDED
                )
                TestScenario.DELETE_API -> CapturedRequest(
                    id = reqId,
                    timestamp = System.currentTimeMillis(),
                    method = "DELETE",
                    url = "https://httpbin.org/delete?resource_id=res_99",
                    scheme = "https",
                    host = "httpbin.org",
                    port = 443,
                    path = "/delete",
                    queryParameters = mapOf("resource_id" to "res_99"),
                    headers = mapOf("Authorization" to "Bearer admin_token_master"),
                    body = null,
                    contentType = null,
                    contentLength = null,
                    state = if (settings.interceptRequests || _isPaused.value) TrafficState.WAITING else TrafficState.FORWARDED
                )
            }

            trafficRepo.recordRequest(testRequest)

            // If not intercepting, execute immediately
            if (!settings.interceptRequests && !_isPaused.value) {
                try {
                    proxyServer.executeDirectTestRequest(testRequest)
                } catch (e: Exception) {
                    trafficRepo.updateState(reqId, TrafficState.ERROR, e.message)
                }
            }
        }
    }
}
