import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadEnv() {
  try {
    const envPath = resolve(__dirname, '../../.env');
    const content = readFileSync(envPath, 'utf-8');
    for (const line of content.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx === -1) continue;
      const key = trimmed.slice(0, eqIdx).trim();
      const value = trimmed.slice(eqIdx + 1).trim();
      if (!process.env[key]) {
        process.env[key] = value;
      }
    }
  } catch {
    // .env file is optional if env vars are set externally
  }
}

loadEnv();

const required = ['OPENAI_API_KEY', 'JWT_SECRET'];
for (const key of required) {
  if (!process.env[key]) {
    console.error(`Missing required environment variable: ${key}`);
    process.exit(1);
  }
}

export const config = Object.freeze({
  port: parseInt(process.env.PORT || '3001', 10),
  host: process.env.HOST || '0.0.0.0',
  openaiApiKey: process.env.OPENAI_API_KEY,
  jwtSecret: process.env.JWT_SECRET,
  llmModel: process.env.LLM_MODEL || 'gpt-4o',
  llmSystemPrompt: process.env.LLM_SYSTEM_PROMPT || 'You are a helpful voice assistant. Keep responses concise and conversational.',
  maxAudioBufferBytes: parseInt(process.env.MAX_AUDIO_BUFFER_BYTES || '5242880', 10),
  rateLimitMax: parseInt(process.env.RATE_LIMIT_MAX || '100', 10),
  rateLimitWindowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
});
