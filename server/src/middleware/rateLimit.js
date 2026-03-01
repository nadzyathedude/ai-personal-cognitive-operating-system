import { createLogger } from '../utils/logger.js';

const log = createLogger('RateLimit');

export class RateLimiter {
  constructor(maxRequests, windowMs) {
    this.maxRequests = maxRequests;
    this.windowMs = windowMs;
    this.clients = new Map();
    this.cleanupInterval = setInterval(() => this.cleanup(), windowMs);
  }

  check(userId) {
    const now = Date.now();
    let record = this.clients.get(userId);

    if (!record || now - record.windowStart > this.windowMs) {
      record = { windowStart: now, count: 0 };
      this.clients.set(userId, record);
    }

    record.count++;

    if (record.count > this.maxRequests) {
      log.warn('Rate limit exceeded', { userId, count: record.count });
      return false;
    }

    return true;
  }

  cleanup() {
    const now = Date.now();
    for (const [key, record] of this.clients) {
      if (now - record.windowStart > this.windowMs) {
        this.clients.delete(key);
      }
    }
  }

  destroy() {
    clearInterval(this.cleanupInterval);
    this.clients.clear();
  }
}
