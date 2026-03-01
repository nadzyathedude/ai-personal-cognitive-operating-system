# Voice Assistant

A full-stack AI voice assistant with wake-word detection, voice biometrics, emotion-aware coaching, and predictive burnout analytics. Built with Kotlin Multiplatform, Jetpack Compose, Node.js, and Python FastAPI.

## Architecture

```
Android App (Kotlin/Compose)  <──WebSocket──>  Node.js Server (Fastify)
     │                                              │
     ├── Wake-word detection (Porcupine)            ├── Whisper STT
     ├── Audio capture (16kHz PCM)                  ├── GPT-4 streaming
     ├── Mood journal UI                            ├── Tool calling
     ├── Analytics charts (MPAndroidChart)           ├── JWT auth
     └── On-device RL coaching                      └── Session management

                    │
     KMP Shared Module (commonMain)
     ├── GoalEngine, MoodEngine
     ├── MemoryGraph, EmotionalTimeline
     ├── LocalPolicyEngine (Thompson Sampling)
     └── SecureSessionManager

Android App  <──WebSocket──>  Python Biometrics Server (FastAPI)
                                    │
                                    ├── Speaker verification (ECAPA-TDNN)
                                    ├── Emotion classification (DistilRoBERTa)
                                    ├── Voice stress analysis (librosa)
                                    ├── Anti-spoof detection
                                    ├── Burnout prediction (LightGBM)
                                    ├── Goal restructuring (LLM)
                                    └── Adaptive coaching (multi-armed bandit)
```

## Project Structure

```
├── app/                              # Android app
│   └── src/main/java/.../
│       ├── data/
│       │   ├── audio/
│       │   │   ├── AudioRecorder.kt
│       │   │   ├── AssistantState.kt
│       │   │   ├── AssistantStateManager.kt
│       │   │   ├── VoiceBiometricManager.kt
│       │   │   └── WakeWordManager.kt
│       │   ├── model/
│       │   │   ├── ChatMessage.kt, SocketMessage.kt
│       │   │   ├── BiometricMessage.kt
│       │   │   └── MoodState.kt
│       │   ├── remote/
│       │   │   ├── WebSocketManager.kt
│       │   │   └── BiometricWebSocketManager.kt
│       │   └── repository/
│       │       ├── ChatRepository.kt
│       │       └── BiometricRepository.kt
│       ├── service/
│       │   └── WakeWordService.kt
│       └── ui/
│           ├── screen/
│           │   ├── ChatScreen.kt
│           │   ├── MoodJournalScreen.kt
│           │   └── AnalyticsScreen.kt
│           ├── viewmodel/
│           │   ├── ChatViewModel.kt
│           │   └── MoodViewModel.kt
│           └── theme/
├── shared/                            # KMP shared module
│   └── src/
│       ├── commonMain/
│       │   └── .../
│       │       ├── engine/            # GoalEngine, MoodEngine, LocalPolicyEngine
│       │       ├── memory/            # MemoryGraphManager
│       │       ├── timeline/          # EmotionalTimelineManager
│       │       ├── models/            # Shared data models
│       │       ├── repository/        # Repository interfaces
│       │       ├── security/          # SecureSessionManager
│       │       ├── state/             # State sealed classes
│       │       └── integration/       # GarminIntegration
│       ├── androidMain/               # EncryptedSharedPreferences, Garmin SDK
│       └── iosMain/                   # Keychain, HealthKit stubs
├── server/                            # Node.js backend
│   └── src/
│       ├── index.js
│       ├── config/env.js
│       ├── middleware/auth.js, rateLimit.js
│       ├── services/llmService.js, sttService.js, sessionManager.js
│       └── websocket/gateway.js, protocol.js
├── biometrics_server/                 # Python FastAPI backend
│   └── app/
│       ├── main.py
│       ├── config.py
│       ├── db/database.py
│       ├── models/tables.py
│       ├── services/
│       │   ├── speaker_embedding.py   # ECAPA-TDNN
│       │   ├── emotion_analyzer.py    # DistilRoBERTa
│       │   ├── voice_stress.py        # Pitch, jitter, shimmer
│       │   ├── mood_journal.py        # LLM-driven journaling
│       │   ├── weekly_analytics.py    # Trend analysis
│       │   ├── antispoof.py           # Replay/synthetic detection
│       │   ├── coaching.py            # Thompson Sampling bandit
│       │   └── burnout/
│       │       ├── feature_builder.py # Temporal features
│       │       ├── burnout_model.py   # Risk prediction
│       │       └── restructuring.py   # Goal restructuring
│       ├── routers/                   # REST endpoints
│       └── websocket/                 # Real-time verification
```

## Prerequisites

- **Node.js** 18+
- **Python** 3.10+
- **Android Studio** with SDK 36
- **OpenAI API key** with Whisper & GPT-4 access
- **Picovoice access key** for wake-word detection

## Setup

### Node.js Server

```bash
cd server
npm install
```

Create a `.env` file (see `.env.example`):

```env
OPENAI_API_KEY=sk-proj-...
JWT_SECRET=your-secret-min-32
PORT=3001
HOST=0.0.0.0
LLM_MODEL=gpt-4o
```

```bash
npm run dev    # Development (auto-reload)
npm start      # Production
```

### Biometrics Server

```bash
cd biometrics_server
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Create a `.env` file (see `.env.example`):

```env
JWT_SECRET=your-secret-min-32
DATABASE_URL=sqlite+aiosqlite:///./biometrics.db
OPENAI_API_KEY=sk-proj-...
```

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Android

1. Open the project in Android Studio
2. Add to `local.properties`:
   ```properties
   PORCUPINE_ACCESS_KEY=your-picovoice-key
   ```
3. Update `WS_URL` in `app/build.gradle.kts` to your server IP
4. Build and run on a device/emulator
5. Grant RECORD_AUDIO and notification permissions

## Features

### Wake-Word Detection
Always-on listening via Picovoice Porcupine foreground service. Say "Porcupine" to activate, or tap the mic manually. Exclusive mic handoff ensures Porcupine and AudioRecorder never conflict.

### Voice Biometrics
Speaker verification using SpeechBrain ECAPA-TDNN embeddings with cosine similarity. Anti-spoof detection guards against replay attacks and synthetic voices.

### Emotion-Aware Mood Journal
Real-time emotion classification from text (DistilRoBERTa) combined with voice stress features (pitch, jitter, shimmer, MFCC). Adaptive follow-up questions generate personalized daily summaries.

### Predictive Burnout Analytics
14-day temporal feature extraction with weighted burnout scoring. Risk categorization (LOW/MODERATE/HIGH) with LLM-driven goal restructuring strategies.

### Adaptive Coaching
Multi-armed bandit (Thompson Sampling) selects optimal coaching strategies. On-device reinforcement learning with encrypted policy persistence. Strategies: goal decomposition, timeline extension, motivational reframe, reflective questioning, recovery suggestion.

### Weekly Analytics
Stress trends via linear regression, emotion distribution tracking, and MPAndroidChart visualizations.

### KMP Shared Module
Kotlin Multiplatform module with shared business logic: goal engine, episodic memory graph, emotional timeline, Garmin HRV integration interface, and secure session management.

## WebSocket Protocol

**Client → Server (Node.js):**

| Type | Description |
|------|-------------|
| `authenticate` | JWT token for auth |
| `audio_chunk` | Base64-encoded PCM audio |
| `end_audio` | End of recording signal |
| `wake_triggered` | Wake-word detection event |
| `ping` | Keep-alive |

**Server → Client:**

| Type | Description |
|------|-------------|
| `authenticated` | Auth success + session ID |
| `transcription` | STT result |
| `llm_token` | Streamed GPT token |
| `llm_done` | Full response text |
| `error` | Error with message and code |
| `pong` | Keep-alive response |

**Biometrics WebSocket:**

| Type | Description |
|------|-------------|
| `verify_start` / `verify_audio` | Speaker verification flow |
| `verification_result` | Match result with confidence |
| `stress_start` / `stress_audio` | Voice stress analysis flow |
| `stress_result` | Stress level + emotion data |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Android UI | Jetpack Compose, Material 3, MPAndroidChart |
| Wake Word | Picovoice Porcupine |
| Networking | OkHttp WebSocket, Ktor Client |
| Architecture | MVVM, KMP, Kotlin Coroutines, StateFlow |
| On-device ML | Thompson Sampling bandit, EncryptedSharedPreferences |
| Server (Node.js) | Fastify 5, ws, OpenAI API |
| Server (Python) | FastAPI, SpeechBrain, HuggingFace Transformers |
| Voice Analysis | librosa, torchaudio |
| Database | SQLAlchemy + aiosqlite |
| Auth | JWT (python-jose / jsonwebtoken) |

## License

MIT
