export const ClientMessageType = Object.freeze({
  AUTHENTICATE: 'authenticate',
  AUDIO_CHUNK: 'audio_chunk',
  END_AUDIO: 'end_audio',
  PING: 'ping',
  WAKE_TRIGGERED: 'wake_triggered',
});

export const ServerMessageType = Object.freeze({
  AUTHENTICATED: 'authenticated',
  TRANSCRIPTION: 'transcription',
  LLM_TOKEN: 'llm_token',
  LLM_DONE: 'llm_done',
  ERROR: 'error',
  PONG: 'pong',
});

const VALID_CLIENT_TYPES = new Set(Object.values(ClientMessageType));

export function parseClientMessage(raw) {
  try {
    const msg = JSON.parse(raw);
    if (!msg.type || !VALID_CLIENT_TYPES.has(msg.type)) {
      return { valid: false, error: 'Invalid message type' };
    }
    return { valid: true, message: msg };
  } catch {
    return { valid: false, error: 'Malformed JSON' };
  }
}

export function serverMsg(type, payload = {}) {
  return JSON.stringify({ type, ...payload });
}
