import jwt from 'jsonwebtoken';
import { config } from '../config/env.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('Auth');

export function verifyToken(token) {
  try {
    const decoded = jwt.verify(token, config.jwtSecret);
    return { valid: true, payload: decoded };
  } catch (err) {
    log.warn('Token verification failed', { error: err.message });
    return { valid: false, payload: null };
  }
}

export function generateDevToken(userId = 'dev-user-1') {
  return jwt.sign(
    { sub: userId, iat: Math.floor(Date.now() / 1000) },
    config.jwtSecret,
    { expiresIn: '24h' }
  );
}
