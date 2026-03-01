# Voice Assistant

A full-stack voice assistant app that captures audio on Android, transcribes it with OpenAI Whisper, processes it through GPT-4, and streams responses back in real time over WebSocket.

## Architecture

```
Android App (Kotlin/Compose)  <──WebSocket──>  Node.js Server (Fastify)
     │                                              │
     ├── Audio capture (16kHz PCM)                  ├── Whisper STT
     ├── Real-time transcription display             ├── GPT-4 streaming
     ├── Streaming chat UI                          ├── Tool calling (time, math)
     └── Auto-reconnection                          ├── JWT auth
                                                    └── Session management
```

## Project Structure

```
├── app/                          # Android app
│   └── src/main/java/.../
│       ├── data/
│       │   ├── audio/AudioRecorder.kt
│       │   ├── model/ChatMessage.kt, SocketMessage.kt
│       │   ├── remote/WebSocketManager.kt
│       │   └── repository/ChatRepository.kt
│       └── ui/
│           ├── screen/ChatScreen.kt
│           ├── viewmodel/ChatViewModel.kt
│           └── theme/
├── server/                       # Node.js backend
│   └── src/
│       ├── index.js
│       ├── config/env.js
│       ├── middleware/auth.js, rateLimit.js
│       ├── services/llmService.js, sttService.js, sessionManager.js
│       └── websocket/gateway.js, protocol.js
```

## Prerequisites

- **Node.js** 18+
- **Android Studio** with SDK 36
- **OpenAI API key** with Whisper & GPT-4 access

## Setup

### Server

```bash
cd server
npm install
```

Create a `.env` file (see `.env.example`):

```env
OPENAI_API_KEY=sk-proj-...       # Required
JWT_SECRET=your-secret-min-32    # Required

# Optional
PORT=3001
HOST=0.0.0.0
LLM_MODEL=gpt-4o
MAX_AUDIO_BUFFER_BYTES=5242880
RATE_LIMIT_MAX=100
RATE_LIMIT_WINDOW_MS=60000
```

Start the server:

```bash
npm run dev    # Development (auto-reload)
npm start      # Production
```

A dev JWT token is printed to the console on startup for testing.

### Android

1. Open the project in Android Studio
2. Update the WebSocket URL in `app/build.gradle.kts` to match your server IP:
   ```kotlin
   buildConfigField("String", "WS_URL", "\"ws://<YOUR_IP>:3001\"")
   ```
3. Build and run on a device/emulator

The app requires `RECORD_AUDIO` permission, which is requested at runtime.

## How It Works

1. User taps the microphone button to start recording
2. Audio is captured at 16kHz, chunked every 200ms, base64-encoded, and sent via WebSocket
3. User taps stop &mdash; client sends `end_audio`
4. Server converts PCM buffer to WAV and sends it to Whisper for transcription
5. Transcription is returned to the client and added to conversation history
6. Server calls GPT-4 with streaming enabled
7. Response tokens stream back to the client in real time
8. If GPT-4 invokes tools (`get_current_time`, `calculate`), the server executes them and continues generation

## WebSocket Protocol

**Client &rarr; Server:**

| Type | Description |
|------|-------------|
| `authenticate` | JWT token for auth |
| `audio_chunk` | Base64-encoded PCM audio |
| `end_audio` | End of recording signal |
| `ping` | Keep-alive |

**Server &rarr; Client:**

| Type | Description |
|------|-------------|
| `authenticated` | Auth success + session ID |
| `transcription` | STT result (partial or final) |
| `llm_token` | Streamed GPT token |
| `llm_done` | Full response text |
| `error` | Error with message and code |
| `pong` | Keep-alive response |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Android UI | Jetpack Compose, Material 3 |
| Android networking | OkHttp WebSocket |
| Android architecture | MVVM, Kotlin Coroutines, StateFlow |
| Server framework | Fastify 5 |
| WebSocket | ws |
| AI | OpenAI API (GPT-4o, Whisper) |
| Auth | JWT |
