package com.example.speach_recognotion_llm.data.remote

import com.example.speach_recognotion_llm.data.model.ClientMessage
import com.example.speach_recognotion_llm.data.model.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATED, RECONNECTING }

class WebSocketManager(private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<ServerMessage> = _messages

    private var authToken: String? = null
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelayMs = 1000L

    fun connect(token: String) {
        authToken = token
        reconnectAttempt = 0
        doConnect()
    }

    private fun doConnect() {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        _connectionState.value = if (reconnectAttempt > 0) {
            ConnectionState.RECONNECTING
        } else {
            ConnectionState.CONNECTING
        }

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                authToken?.let { token ->
                    send(ClientMessage.Authenticate(token))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = ServerMessage.fromJson(text)
                if (message is ServerMessage.Authenticated) {
                    _connectionState.value = ConnectionState.AUTHENTICATED
                }
                scope.launch { _messages.emit(message) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                if (code != 1000) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.DISCONNECTED
                scope.launch {
                    _messages.emit(
                        ServerMessage.Error(
                            message = t.message ?: "Connection failed",
                            code = "CONNECTION_ERROR"
                        )
                    )
                }
                scheduleReconnect()
            }
        })
    }

    fun send(message: ClientMessage) {
        webSocket?.send(message.toJson())
    }

    fun disconnect() {
        reconnectAttempt = maxReconnectAttempts
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= maxReconnectAttempts) return

        scope.launch {
            val delayMs = baseReconnectDelayMs * (1L shl reconnectAttempt.coerceAtMost(4))
            reconnectAttempt++
            delay(delayMs)
            doConnect()
        }
    }
}
