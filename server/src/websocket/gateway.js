import { WebSocketServer } from 'ws';
import { verifyToken } from '../middleware/auth.js';
import { RateLimiter } from '../middleware/rateLimit.js';
import { SessionManager } from '../services/sessionManager.js';
import { transcribeAudio } from '../services/sttService.js';
import { streamLlmResponse } from '../services/llmService.js';
import { parseClientMessage, serverMsg, ClientMessageType, ServerMessageType } from './protocol.js';
import { config } from '../config/env.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('WSGateway');

export class WebSocketGateway {
  constructor(server) {
    this.wss = new WebSocketServer({ server, path: '/' });
    this.sessionManager = new SessionManager();
    this.rateLimiter = new RateLimiter(config.rateLimitMax, config.rateLimitWindowMs);

    this.wss.on('connection', (ws, req) => this.handleConnection(ws, req));
    log.info('WebSocket gateway initialized');
  }

  handleConnection(ws, req) {
    const ip = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress;
    log.info('New connection', { ip });

    let authenticated = false;
    let session = null;

    const authTimeout = setTimeout(() => {
      if (!authenticated) {
        ws.send(serverMsg(ServerMessageType.ERROR, {
          message: 'Authentication timeout',
          code: 'AUTH_TIMEOUT',
        }));
        ws.close(4001, 'Authentication timeout');
      }
    }, 10000);

    ws.on('message', async (raw) => {
      const data = raw.toString();
      const parsed = parseClientMessage(data);

      if (!parsed.valid) {
        ws.send(serverMsg(ServerMessageType.ERROR, {
          message: parsed.error,
          code: 'INVALID_MESSAGE',
        }));
        return;
      }

      const { message } = parsed;

      try {
        if (message.type === ClientMessageType.AUTHENTICATE) {
          await this.handleAuth(ws, message, authTimeout, (s) => {
            authenticated = true;
            session = s;
          });
          return;
        }

        if (!authenticated || !session) {
          ws.send(serverMsg(ServerMessageType.ERROR, {
            message: 'Not authenticated',
            code: 'UNAUTHORIZED',
          }));
          return;
        }

        if (!this.rateLimiter.check(session.userId)) {
          ws.send(serverMsg(ServerMessageType.ERROR, {
            message: 'Rate limit exceeded',
            code: 'RATE_LIMITED',
          }));
          return;
        }

        switch (message.type) {
          case ClientMessageType.AUDIO_CHUNK:
            this.handleAudioChunk(session, message);
            break;
          case ClientMessageType.END_AUDIO:
            await this.handleEndAudio(ws, session);
            break;
          case ClientMessageType.WAKE_TRIGGERED:
            this.handleWakeTriggered(session);
            break;
          case ClientMessageType.PING:
            ws.send(serverMsg(ServerMessageType.PONG));
            break;
        }
      } catch (err) {
        log.error('Message handling error', { error: err.message, type: message.type });
        ws.send(serverMsg(ServerMessageType.ERROR, {
          message: 'Internal server error',
          code: 'INTERNAL_ERROR',
        }));
      }
    });

    ws.on('close', (code, reason) => {
      clearTimeout(authTimeout);
      if (session) {
        this.sessionManager.remove(session.id);
      }
      log.info('Connection closed', { code, sessionId: session?.id });
    });

    ws.on('error', (err) => {
      log.error('WebSocket error', { error: err.message, sessionId: session?.id });
    });
  }

  async handleAuth(ws, message, authTimeout, onSuccess) {
    const token = message.token;
    if (!token) {
      ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'Token required',
        code: 'AUTH_FAILED',
      }));
      return;
    }

    const { valid, payload } = verifyToken(token);
    if (!valid || !payload) {
      ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'Invalid token',
        code: 'AUTH_FAILED',
      }));
      ws.close(4003, 'Invalid token');
      return;
    }

    clearTimeout(authTimeout);
    const session = this.sessionManager.create(payload.sub, ws);
    onSuccess(session);

    ws.send(serverMsg(ServerMessageType.AUTHENTICATED, { sessionId: session.id }));
    log.info('Client authenticated', { userId: payload.sub, sessionId: session.id });
  }

  handleAudioChunk(session, message) {
    if (!message.data || typeof message.data !== 'string') {
      session.ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'Invalid audio data',
        code: 'INVALID_AUDIO',
      }));
      return;
    }

    try {
      session.addAudioChunk(message.data);
    } catch (err) {
      session.ws.send(serverMsg(ServerMessageType.ERROR, {
        message: err.message,
        code: 'AUDIO_BUFFER_ERROR',
      }));
      session.clearAudioBuffer();
    }
  }

  handleWakeTriggered(session) {
    session.clearAudioBuffer();
    session.lastActivity = Date.now();
    log.info('Wake word triggered', { sessionId: session.id, userId: session.userId });
  }

  async handleEndAudio(ws, session) {
    const audioBuffer = session.getAudioBuffer();
    session.clearAudioBuffer();

    if (audioBuffer.length === 0) {
      ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'No audio data received',
        code: 'EMPTY_AUDIO',
      }));
      return;
    }

    // Add WAV header for Whisper
    const wavBuffer = addWavHeader(audioBuffer, 16000, 1, 16);

    // Transcribe
    let transcription;
    try {
      transcription = await transcribeAudio(wavBuffer);
    } catch (err) {
      log.error('Transcription failed', { error: err.message });
      ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'Transcription failed',
        code: 'STT_ERROR',
      }));
      return;
    }

    // Send partial transcription for preview, then final
    ws.send(serverMsg(ServerMessageType.TRANSCRIPTION, {
      text: transcription,
      isFinal: true,
    }));

    // Add user message to conversation
    session.addMessage('user', transcription);

    // Stream LLM response
    try {
      const llmStream = streamLlmResponse(session.conversationHistory);

      for await (const event of llmStream) {
        if (ws.readyState !== ws.OPEN) break;

        if (event.type === 'token') {
          ws.send(serverMsg(ServerMessageType.LLM_TOKEN, { token: event.token }));
        } else if (event.type === 'done') {
          session.addMessage('assistant', event.fullText);
          ws.send(serverMsg(ServerMessageType.LLM_DONE, { fullText: event.fullText }));
        }
      }
    } catch (err) {
      log.error('LLM streaming failed', { error: err.message });
      ws.send(serverMsg(ServerMessageType.ERROR, {
        message: 'Assistant response failed',
        code: 'LLM_ERROR',
      }));
    }
  }

  destroy() {
    this.sessionManager.destroy();
    this.rateLimiter.destroy();
    this.wss.close();
  }
}

function addWavHeader(pcmBuffer, sampleRate, numChannels, bitsPerSample) {
  const byteRate = sampleRate * numChannels * (bitsPerSample / 8);
  const blockAlign = numChannels * (bitsPerSample / 8);
  const dataSize = pcmBuffer.length;
  const headerSize = 44;
  const buffer = Buffer.alloc(headerSize + dataSize);

  // RIFF header
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataSize, 4);
  buffer.write('WAVE', 8);

  // fmt chunk
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20); // PCM format
  buffer.writeUInt16LE(numChannels, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(byteRate, 28);
  buffer.writeUInt16LE(blockAlign, 32);
  buffer.writeUInt16LE(bitsPerSample, 34);

  // data chunk
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataSize, 40);
  pcmBuffer.copy(buffer, 44);

  return buffer;
}
