package com.example.proxy

import com.example.data.model.CapturedRequest
import com.example.data.model.CapturedResponse
import com.example.data.model.TrafficState
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ProxyServer(
    private val settingsRepository: SettingsRepository,
    private val httpForwarder: HttpForwarder = HttpForwarder()
) : ProxyEngine {

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    private var listener: ProxyListener? = null

    private val requestInterceptor = RequestInterceptor()
    private val responseInterceptor = ResponseInterceptor()

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (running.get()) return@withContext
        val settings = settingsRepository.settingsFlow.value
        try {
            val bindAddr = try {
                InetAddress.getByName(settings.host)
            } catch (_: Exception) {
                InetAddress.getByName("0.0.0.0")
            }
            val socket = ServerSocket(settings.port, 50, bindAddr)
            socket.reuseAddress = true
            serverSocket = socket
            running.set(true)

            serverJob = serverScope.launch {
                while (isActive && running.get()) {
                    try {
                        val clientSocket = socket.accept()
                        launch {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!running.get()) break
                    }
                }
            }
        } catch (e: Exception) {
            running.set(false)
            listener?.onError("SERVER_START", "Failed to start proxy on ${settings.host}:${settings.port}: ${e.message}")
            throw e
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverScope.coroutineContext.cancelChildren()
        requestInterceptor.cancelAll()
        responseInterceptor.cancelAll()
    }

    override fun isRunning(): Boolean = running.get()

    override fun isPaused(): Boolean = paused.get()

    override fun pause() {
        paused.set(true)
    }

    override fun resume() {
        paused.set(false)
    }

    override fun setListener(listener: ProxyListener) {
        this.listener = listener
    }

    override fun forwardRequest(requestId: String, request: CapturedRequest) {
        requestInterceptor.forward(requestId, request)
    }

    override fun blockRequest(requestId: String) {
        requestInterceptor.block(requestId)
    }

    override fun returnResponse(requestId: String, response: CapturedResponse) {
        responseInterceptor.returnResponse(requestId, response)
    }

    override fun blockResponse(requestId: String) {
        responseInterceptor.block(requestId)
    }

    private suspend fun handleClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString().take(8)
        var capturedRequest: CapturedRequest? = null
        try {
            socket.soTimeout = 30000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP request line and headers
            val headerBytes = readHeaderBytes(input)
            if (headerBytes.isEmpty()) {
                socket.close()
                return@withContext
            }

            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) {
                socket.close()
                return@withContext
            }

            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return@withContext
            }

            val method = parts[0].uppercase()
            var rawUri = parts[1]
            val protocol = if (parts.size > 2) parts[2] else "HTTP/1.1"

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val colonIdx = lines[i].indexOf(':')
                if (colonIdx > 0) {
                    val key = lines[i].substring(0, colonIdx).trim()
                    val value = lines[i].substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // Handle HTTPS CONNECT tunnel handshake
            if (method == "CONNECT") {
                handleConnectTunnel(socket, input, output, rawUri, requestId)
                return@withContext
            }

            // Resolve full URL
            val fullUrl = resolveFullUrl(rawUri, headers)
            val uri = try { URI(fullUrl) } catch (_: Exception) { null }
            val queryParams = parseQueryParams(uri?.rawQuery)

            // Read Body if Content-Length is provided
            val contentLength = headers["Content-Length"]?.toLongOrNull() ?: 0L
            val contentType = headers["Content-Type"]
            val bodyString = if (contentLength > 0 && contentLength < 5 * 1024 * 1024) { // limit to 5MB
                val bodyBuf = ByteArray(contentLength.toInt())
                var totalRead = 0
                while (totalRead < contentLength.toInt()) {
                    val read = input.read(bodyBuf, totalRead, contentLength.toInt() - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(bodyBuf, 0, totalRead, Charsets.UTF_8)
            } else {
                null
            }

            val request = CapturedRequest(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                method = method,
                url = fullUrl,
                scheme = uri?.scheme ?: "http",
                host = uri?.host ?: headers["Host"] ?: "",
                port = if (uri?.port != null && uri.port != -1) uri.port else if (uri?.scheme == "https") 443 else 80,
                path = uri?.path?.ifBlank { "/" } ?: "/",
                queryParameters = queryParams,
                headers = headers,
                body = bodyString,
                contentType = contentType,
                contentLength = if (contentLength > 0) contentLength else null,
                protocol = protocol,
                state = TrafficState.WAITING
            )
            capturedRequest = request

            val settings = settingsRepository.settingsFlow.value
            val shouldIntercept = paused.get() || settings.interceptRequests

            // Register before publishing to the UI so an immediate FORWARD/BLOCK cannot race.
            val requestDecision = if (shouldIntercept) {
                requestInterceptor.register(requestId)
            } else {
                null
            }

            listener?.onRequestCaptured(request)

            val finalRequestToForward: CapturedRequest
            if (shouldIntercept) {
                val decision = requestDecision!!.await()
                when (decision) {
                    is RequestDecision.Forward -> {
                        finalRequestToForward = decision.request
                    }
                    is RequestDecision.Block -> {
                        sendBlockedResponse(output, "Request blocked by user in DevTraffic Inspector")
                        listener?.onRequestBlocked(request)
                        socket.close()
                        return@withContext
                    }
                }
            } else {
                finalRequestToForward = request
            }

            // Forward the request to the destination server
            val response = try {
                httpForwarder.forward(finalRequestToForward)
            } catch (e: Exception) {
                listener?.onError(requestId, e.message ?: "Network forwarding error")
                sendErrorResponse(output, 502, "Bad Gateway", "DevTraffic Inspector could not forward request to ${finalRequestToForward.url}: ${e.message}")
                socket.close()
                return@withContext
            }

            // Register before publishing so an immediate RETURN/BLOCK cannot race.
            val shouldInterceptResponse = settings.interceptResponses
            val responseDecision = if (shouldInterceptResponse) {
                responseInterceptor.register(requestId)
            } else {
                null
            }

            listener?.onResponseCaptured(response)

            val finalResponseToReturn: CapturedResponse
            if (shouldInterceptResponse) {
                val respDecision = responseDecision!!.await()
                when (respDecision) {
                    is ResponseDecision.Return -> {
                        finalResponseToReturn = respDecision.response
                    }
                    is ResponseDecision.Block -> {
                        sendBlockedResponse(output, "Response blocked by user in DevTraffic Inspector")
                        socket.close()
                        return@withContext
                    }
                }
            } else {
                finalResponseToReturn = response
            }

            // Write final response back to client socket
            writeResponseToClient(output, finalResponseToReturn)
            socket.close()

        } catch (e: Exception) {
            listener?.onError(requestId, "Socket error: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnectTunnel(
        socket: Socket,
        input: BufferedInputStream,
        output: BufferedOutputStream,
        rawUri: String,
        requestId: String
    ) {
        val parts = rawUri.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443

        val capturedReq = CapturedRequest(
            id = requestId,
            timestamp = System.currentTimeMillis(),
            method = "CONNECT",
            url = "https://$host:$port",
            scheme = "https",
            host = host,
            port = port,
            path = "/",
            state = TrafficState.FORWARDED
        )
        listener?.onRequestCaptured(capturedReq)

        try {
            val targetSocket = Socket(host, port)
            targetSocket.soTimeout = 30000

            val established = "HTTP/1.1 200 Connection Established\r\n\r\n"
            output.write(established.toByteArray(Charsets.ISO_8859_1))
            output.flush()

            val targetIn = BufferedInputStream(targetSocket.getInputStream())
            val targetOut = BufferedOutputStream(targetSocket.getOutputStream())

            serverScope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        targetOut.write(buffer, 0, read)
                        targetOut.flush()
                    }
                } catch (_: Exception) {} finally {
                    try { targetSocket.close() } catch (_: Exception) {}
                    try { socket.close() } catch (_: Exception) {}
                }
            }

            serverScope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (targetIn.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        output.flush()
                    }
                } catch (_: Exception) {} finally {
                    try { socket.close() } catch (_: Exception) {}
                    try { targetSocket.close() } catch (_: Exception) {}
                }
            }

        } catch (e: Exception) {
            listener?.onError(requestId, "HTTPS Tunnel error to $host:$port: ${e.message}")
            try {
                sendErrorResponse(output, 502, "Bad Gateway", "Failed to connect to $host:$port: ${e.message}")
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun resolveFullUrl(rawUri: String, headers: Map<String, String>): String {
        if (rawUri.startsWith("http://", ignoreCase = true) || rawUri.startsWith("https://", ignoreCase = true)) {
            return rawUri
        }
        val targetOverride = headers["X-Target-Url"] ?: headers["x-target-url"]
        if (!targetOverride.isNullOrBlank()) {
            return if (targetOverride.endsWith("/") && rawUri.startsWith("/")) {
                targetOverride.removeSuffix("/") + rawUri
            } else {
                targetOverride + rawUri
            }
        }
        val hostHeader = headers["Host"] ?: headers["host"] ?: "localhost"
        return "http://$hostHeader$rawUri"
    }

    private fun parseQueryParams(queryString: String?): Map<String, String> {
        if (queryString.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            } else if (pair.isNotEmpty()) {
                result[pair] = ""
            }
        }
        return result
    }

    private fun readHeaderBytes(input: BufferedInputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        var lastFour = 0
        var b: Int
        while (input.read().also { b = it } != -1) {
            baos.write(b)
            lastFour = (lastFour shl 8) or (b and 0xFF)
            if (lastFour == 0x0D0A0D0A) { // \r\n\r\n
                break
            }
        }
        return baos.toByteArray()
    }

    private fun sendBlockedResponse(output: BufferedOutputStream, message: String) {
        val bodyBytes = message.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 403 Forbidden\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.ISO_8859_1))
        output.write(bodyBytes)
        output.flush()
    }

    private fun sendErrorResponse(output: BufferedOutputStream, code: Int, status: String, message: String) {
        val bodyBytes = message.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $code $status\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.ISO_8859_1))
        output.write(bodyBytes)
        output.flush()
    }

    private fun writeResponseToClient(output: BufferedOutputStream, response: CapturedResponse) {
        val statusMessage = response.statusMessage ?: when (response.statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Response"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ${response.statusCode} $statusMessage\r\n")

        val bodyBytes = response.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)

        response.headers.forEach { (key, value) ->
            if (!key.equals("Content-Length", ignoreCase = true) && !key.equals("Transfer-Encoding", ignoreCase = true)) {
                sb.append("$key: $value\r\n")
            }
        }
        sb.append("Content-Length: ${bodyBytes.size}\r\n")
        sb.append("Connection: close\r\n\r\n")

        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    suspend fun executeDirectTestRequest(request: CapturedRequest): CapturedResponse {
        listener?.onRequestCaptured(request)
        return try {
            val response = httpForwarder.forward(request)
            listener?.onResponseCaptured(response)
            response
        } catch (e: Exception) {
            listener?.onError(request.id, e.message ?: "Test request error")
            throw e
        }
    }
}
