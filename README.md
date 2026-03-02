# AI Personal Cognitive Operating System



**Это персональная когнитивная операционная система** -- AI-платформа для управления благополучием и продуктивностьюю

Система непрерывно отслеживает эмоциональное состояние пользователя через голос, текст и физиологические сигналы (пульс, вариабельность сердечного ритма), прогнозирует риск выгорания на 7 дней вперёд и автономно адаптирует стратегии коучинга и расписание календаря, сохраняя устойчивость и продуктивность пользователя.



- **Мультимодальный анализ эмоций** -- объединяет акустическую просодию (40+ голосовых признаков через wav2vec2), текстовую тональность (DistilRoBERTa) и данные пульса через BLE в единый индекс стресса. Распознаёт, когда человек говорит «всё хорошо», но голос выдаёт обратное.
- **On-device обучение с подкреплением** -- алгоритм Thompson Sampling обучается, какие стратегии коучинга работают для конкретного пользователя, без передачи данных за пределы устройства. Приватность на уровне архитектуры, а не политики.
- **Прогнозирование выгорания** -- анализ 14-дневных временных признаков с проекцией на 7 дней вперёд. Не реактивный («вы в стрессе»), а предиктивный («при текущей динамике критическая точка через 5 дней»).
- **Стресс-адаптивный календарь** -- первый ассистент, интегрирующий физиологические данные стресса в реальном времени в управление расписанием: автоматическая вставка восстановительных блоков и перенос некритичных задач.
- **Голосовая биометрия** -- верификация говорящего через ECAPA-TDNN с защитой от спуфинга предотвращает несанкционированный доступ и replay-атаки.

### Архитектура и масштабируемость

Продакшн-готовый полиглотный стек: Kotlin Multiplatform (общая кодовая база Android + iOS), Node.js сервер реального времени (Fastify/WebSocket), Python ML-бэкенд (FastAPI, SpeechBrain, HuggingFace). Горизонтально масштабируемый, полностью асинхронный, готов к 10K+ одновременных пользователей.

### Модель монетизации

| Канал | Цена | Целевая аудитория |
|-------|------|-------------------|
| B2C подписка | $9.99/мес | Индивидуальные пользователи |
| B2B корпоративное здоровье | $2-5/сотрудник/мес | Корпоративные EAP-программы, страхование |
| API-лицензирование | Оплата за вызов | Аналитика эмоций для сторонних платформ |
| OEM-партнёрства | Revenue share | Производители носимых устройств (интеграция с Garmin готова) |

---

A full-stack AI voice assistant with wake-word detection, voice biometrics, emotion-aware coaching, real-time BLE heart rate monitoring, stress-aware calendar scheduling, and predictive burnout analytics. Built with Kotlin Multiplatform, Jetpack Compose, Node.js, and Python FastAPI.

## Architecture

```
Android App (Kotlin/Compose)  <──WebSocket──>  Node.js Server (Fastify)
     │                                              │
     ├── Wake-word detection (Porcupine)            ├── Whisper STT
     ├── Audio capture (16kHz PCM)                  ├── GPT-4 streaming
     ├── Mood journal UI                            ├── Tool calling
     ├── Analytics charts (MPAndroidChart)           ├── JWT auth
     ├── BLE Heart Rate (Garmin/generic)            └── Session management
     ├── HRV & Composite Stress Index
     ├── Smart Calendar (stress-based scheduling)
     └── On-device RL coaching

                    │
     KMP Shared Module (commonMain)
     ├── GoalEngine, MoodEngine
     ├── MemoryGraph, EmotionalTimeline
     ├── LocalPolicyEngine (Thompson Sampling)
     ├── CalendarStressAdjuster
     └── SecureSessionManager

Android App  <──WebSocket──>  Python Biometrics Server (FastAPI)
                                    │
                                    ├── Speaker verification (ECAPA-TDNN)
                                    ├── Emotion classification (DistilRoBERTa)
                                    ├── Acoustic emotion analysis (wav2vec2)
                                    ├── Prosody analysis (pitch, energy, tempo)
                                    ├── Emotion fusion (text + voice)
                                    ├── Voice stress analysis (librosa)
                                    ├── Anti-spoof detection
                                    ├── Burnout prediction (LightGBM)
                                    ├── Goal restructuring (LLM)
                                    ├── Garmin HRV data ingestion
                                    └── Adaptive coaching (multi-armed bandit)
```

## Project Structure

```
├── app/                              # Android app
│   └── src/main/
│       ├── assets/                   # Porcupine wake-word model
│       ├── java/.../
│       │   ├── data/
│       │   │   ├── audio/
│       │   │   │   ├── AudioRecorder.kt
│       │   │   │   ├── AssistantState.kt
│       │   │   │   ├── AssistantStateManager.kt
│       │   │   │   ├── VoiceBiometricManager.kt
│       │   │   │   └── WakeWordManager.kt
│       │   │   ├── ble/
│       │   │   │   └── BleHeartRateManager.kt    # BLE HR scanning, connection, HRV
│       │   │   ├── model/
│       │   │   │   ├── ChatMessage.kt, SocketMessage.kt
│       │   │   │   ├── BiometricMessage.kt
│       │   │   │   └── MoodState.kt
│       │   │   ├── remote/
│       │   │   │   ├── WebSocketManager.kt
│       │   │   │   └── BiometricWebSocketManager.kt
│       │   │   └── repository/
│       │   │       ├── ChatRepository.kt
│       │   │       ├── BiometricRepository.kt
│       │   │       └── AndroidCalendarRepository.kt
│       │   ├── service/
│       │   │   └── WakeWordService.kt
│       │   ├── ui/
│       │   │   ├── screen/
│       │   │   │   ├── ChatScreen.kt
│       │   │   │   ├── MoodJournalScreen.kt
│       │   │   │   ├── AnalyticsScreen.kt
│       │   │   │   ├── HrvScreen.kt              # BLE device scan/connect, live HR, stress
│       │   │   │   ├── CalendarScreen.kt          # Stress-aware smart calendar
│       │   │   │   └── SettingsScreen.kt          # Language selection (EN/RU)
│       │   │   ├── viewmodel/
│       │   │   │   ├── ChatViewModel.kt
│       │   │   │   ├── MoodViewModel.kt
│       │   │   │   ├── HrvViewModel.kt
│       │   │   │   └── CalendarViewModel.kt
│       │   │   └── theme/
│       │   └── util/
│       │       └── LocaleHelper.kt               # Runtime locale switching
│       └── res/
│           ├── values/strings.xml                 # English strings
│           └── values-ru/strings.xml              # Russian strings
├── shared/                            # KMP shared module
│   └── src/
│       ├── commonMain/
│       │   └── .../
│       │       ├── engine/            # GoalEngine, MoodEngine, CalendarStressAdjuster
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
│       │   ├── emotion_analyzer.py    # DistilRoBERTa + text emotion
│       │   ├── acoustic_emotion.py    # wav2vec2 voice emotion
│       │   ├── prosody_analyzer.py    # Pitch, energy, tempo analysis
│       │   ├── emotion_fusion.py      # Multi-modal emotion fusion
│       │   ├── voice_stress.py        # Pitch, jitter, shimmer
│       │   ├── mood_journal.py        # LLM-driven journaling
│       │   ├── weekly_analytics.py    # Trend analysis
│       │   ├── antispoof.py           # Replay/synthetic detection
│       │   ├── coaching.py            # Thompson Sampling bandit
│       │   └── burnout/
│       │       ├── feature_builder.py # Temporal features
│       │       ├── burnout_model.py   # Risk prediction
│       │       └── restructuring.py   # Goal restructuring
│       ├── routers/                   # REST endpoints (enrollment, mood, tone, garmin)
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
5. Grant RECORD_AUDIO, notification, Bluetooth, and location permissions

## Features

### Wake-Word Detection
Always-on listening via Picovoice Porcupine foreground service. Say "Hi Dude" to activate, or tap the mic manually. Exclusive mic handoff ensures Porcupine and AudioRecorder never conflict.

### Voice Biometrics
Speaker verification using SpeechBrain ECAPA-TDNN embeddings with cosine similarity. Anti-spoof detection guards against replay attacks and synthetic voices.

### Emotion-Aware Mood Journal
Real-time emotion classification from text (DistilRoBERTa) combined with voice stress features (pitch, jitter, shimmer, MFCC). Voice recording support for mood check-ins with acoustic emotion analysis. Adaptive follow-up questions generate personalized daily summaries.

### Voice Tone Analysis
Multi-modal emotion analysis combining acoustic features (wav2vec2), prosody analysis (pitch, energy, tempo), and text sentiment. Detects mismatches between spoken tone and text content with fusion stress scoring.

### BLE Heart Rate & HRV Monitoring
Real-time heart rate monitoring via Bluetooth Low Energy. Supports standard BLE Heart Rate Profile (0x180D) compatible with Garmin and other HR-capable devices. Features include:
- Device scanning with HR-capability detection
- Saved device list for quick reconnection
- RMSSD-based HRV calculation from RR intervals
- Median-filtered HR display with stabilization period
- Connection timeout and auto-reconnect on unexpected disconnect
- Garmin device detection with setup instructions for HR broadcast

### Composite Stress Index
Multi-source stress estimation combining:
- Voice stress (acoustic analysis) — 40% weight
- Text stress (sentiment analysis) — 30% weight
- HRV/HR stress (physiological) — 30% weight

### Smart Calendar
Stress-aware schedule management with Google Calendar integration:
- Reads calendar events and assesses stress risk per event
- Auto-adjusts schedule based on real-time stress level
- Inserts recovery blocks between high-stress events
- Reschedules non-essential tasks when stress is elevated
- Coaching reminders for stress management

### Predictive Burnout Analytics
14-day temporal feature extraction with weighted burnout scoring. Risk categorization (LOW/MODERATE/HIGH) with LLM-driven goal restructuring strategies. Weekly analytics with stress trends, emotion distribution, and mood valence charts.

### Adaptive Coaching
Multi-armed bandit (Thompson Sampling) selects optimal coaching strategies. On-device reinforcement learning with encrypted policy persistence. Strategies: goal decomposition, timeline extension, motivational reframe, reflective questioning, recovery suggestion.

### Localization
Full English and Russian language support with runtime locale switching via Settings screen. All UI strings externalized for easy translation.

### KMP Shared Module
Kotlin Multiplatform module with shared business logic: goal engine, episodic memory graph, emotional timeline, calendar stress adjustment, Garmin HRV integration interface, and secure session management.

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
| BLE | Android Bluetooth LE API, Heart Rate Profile |
| Networking | OkHttp WebSocket, Ktor Client |
| Architecture | MVVM, KMP, Kotlin Coroutines, StateFlow |
| On-device ML | Thompson Sampling bandit, EncryptedSharedPreferences |
| Server (Node.js) | Fastify 5, ws, OpenAI API |
| Server (Python) | FastAPI, SpeechBrain, HuggingFace Transformers |
| Voice Analysis | librosa, torchaudio, wav2vec2 |
| Database | SQLAlchemy + aiosqlite |
| Auth | JWT (python-jose / jsonwebtoken) |

## License

MIT
