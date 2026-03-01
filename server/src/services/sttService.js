import OpenAI, { toFile } from 'openai';
import { config } from '../config/env.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('STT');

const openai = new OpenAI({ apiKey: config.openaiApiKey });

export async function transcribeAudio(audioBuffer) {
  if (!audioBuffer || audioBuffer.length === 0) {
    throw new Error('Empty audio buffer');
  }

  log.info('Transcribing audio', { bytes: audioBuffer.length });

  const file = await toFile(audioBuffer, 'audio.wav', { type: 'audio/wav' });

  const response = await openai.audio.transcriptions.create({
    model: 'whisper-1',
    file,
    response_format: 'json',
  });

  log.info('Transcription complete', { text: response.text.slice(0, 80) });
  return response.text;
}
