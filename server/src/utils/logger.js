const LOG_LEVELS = { debug: 0, info: 1, warn: 2, error: 3 };
const currentLevel = LOG_LEVELS[process.env.LOG_LEVEL || 'info'] ?? LOG_LEVELS.info;

function fmt(level, context, msg, data) {
  const ts = new Date().toISOString();
  const base = `${ts} [${level.toUpperCase()}]${context ? ` [${context}]` : ''} ${msg}`;
  if (data !== undefined) {
    return `${base} ${JSON.stringify(data)}`;
  }
  return base;
}

export function createLogger(context) {
  return {
    debug(msg, data) {
      if (currentLevel <= LOG_LEVELS.debug) console.debug(fmt('debug', context, msg, data));
    },
    info(msg, data) {
      if (currentLevel <= LOG_LEVELS.info) console.log(fmt('info', context, msg, data));
    },
    warn(msg, data) {
      if (currentLevel <= LOG_LEVELS.warn) console.warn(fmt('warn', context, msg, data));
    },
    error(msg, data) {
      if (currentLevel <= LOG_LEVELS.error) console.error(fmt('error', context, msg, data));
    },
  };
}
