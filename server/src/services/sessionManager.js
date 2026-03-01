import { randomUUID } from 'crypto';
import { createLogger } from '../utils/logger.js';
import { config } from '../config/env.js';

const log = createLogger('SessionManager');

class Session {
  constructor(userId, ws) {
    this.id = randomUUID();
    this.userId = userId;
    this.ws = ws;
    this.audioChunks = [];
    this.audioBufferSize = 0;
    this.conversationHistory = [];
    this.createdAt = Date.now();
    this.lastActivity = Date.now();
  }

  addAudioChunk(base64Data) {
    const bytes = Buffer.from(base64Data, 'base64');
    this.audioBufferSize += bytes.length;

    if (this.audioBufferSize > config.maxAudioBufferBytes) {
      throw new Error('Audio buffer exceeded maximum size');
    }

    this.audioChunks.push(bytes);
    this.lastActivity = Date.now();
  }

  getAudioBuffer() {
    return Buffer.concat(this.audioChunks);
  }

  clearAudioBuffer() {
    this.audioChunks = [];
    this.audioBufferSize = 0;
  }

  addMessage(role, content) {
    this.conversationHistory.push({ role, content });
    if (this.conversationHistory.length > 50) {
      this.conversationHistory = this.conversationHistory.slice(-40);
    }
    this.lastActivity = Date.now();
  }
}

export class SessionManager {
  constructor() {
    this.sessions = new Map();
    this.userSessions = new Map();
    this.cleanupInterval = setInterval(() => this.cleanup(), 60000);
  }

  create(userId, ws) {
    const existingSessionId = this.userSessions.get(userId);
    if (existingSessionId) {
      this.remove(existingSessionId);
    }

    const session = new Session(userId, ws);
    this.sessions.set(session.id, session);
    this.userSessions.set(userId, session.id);

    log.info('Session created', { sessionId: session.id, userId });
    return session;
  }

  get(sessionId) {
    return this.sessions.get(sessionId) || null;
  }

  getByWs(ws) {
    for (const session of this.sessions.values()) {
      if (session.ws === ws) return session;
    }
    return null;
  }

  remove(sessionId) {
    const session = this.sessions.get(sessionId);
    if (session) {
      this.userSessions.delete(session.userId);
      this.sessions.delete(sessionId);
      log.info('Session removed', { sessionId, userId: session.userId });
    }
  }

  removeByWs(ws) {
    const session = this.getByWs(ws);
    if (session) this.remove(session.id);
  }

  cleanup() {
    const staleThreshold = Date.now() - 30 * 60 * 1000;
    for (const [id, session] of this.sessions) {
      if (session.lastActivity < staleThreshold) {
        log.info('Cleaning stale session', { sessionId: id });
        this.remove(id);
      }
    }
  }

  destroy() {
    clearInterval(this.cleanupInterval);
    this.sessions.clear();
    this.userSessions.clear();
  }
}
