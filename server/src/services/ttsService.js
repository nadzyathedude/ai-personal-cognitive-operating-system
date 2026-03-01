import OpenAI from 'openai';
import { config } from '../config/env.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('TTS');

const openai = new OpenAI({ apiKey: config.openaiApiKey });

export async function generateSpeech(text) {
  if (!text || text.trim().length === 0) return null;

  log.info('Generating TTS', { length: text.length });

  const response = await openai.audio.speech.create({
    model: 'tts-1',
    voice: 'alloy',
    input: text,
    response_format: 'opus',
  });

  const buffer = Buffer.from(await response.arrayBuffer());
  log.info('TTS complete', { bytes: buffer.length });
  return buffer;
}
