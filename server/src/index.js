import Fastify from 'fastify';
import cors from '@fastify/cors';
import { config } from './config/env.js';
import { WebSocketGateway } from './websocket/gateway.js';
import { generateDevToken } from './middleware/auth.js';
import { createLogger } from './utils/logger.js';

const log = createLogger('Server');

const fastify = Fastify({ logger: false });

await fastify.register(cors, { origin: true });

// Health check
fastify.get('/health', async () => ({ status: 'ok', timestamp: Date.now() }));

// Dev-only: generate a test JWT (remove in production)
fastify.get('/dev/token', async (request, reply) => {
  if (process.env.NODE_ENV === 'production') {
    return reply.code(404).send({ error: 'Not found' });
  }
  const token = generateDevToken();
  return { token };
});

const start = async () => {
  try {
    await fastify.listen({ port: config.port, host: config.host });

    const gateway = new WebSocketGateway(fastify.server);

    log.info(`Server listening on ${config.host}:${config.port}`);
    log.info(`WebSocket endpoint: ws://${config.host}:${config.port}/`);

    if (process.env.NODE_ENV !== 'production') {
      const devToken = generateDevToken();
      log.info(`Dev JWT token: ${devToken}`);
    }

    const shutdown = async (signal) => {
      log.info(`${signal} received, shutting down`);
      gateway.destroy();
      await fastify.close();
      process.exit(0);
    };

    process.on('SIGINT', () => shutdown('SIGINT'));
    process.on('SIGTERM', () => shutdown('SIGTERM'));
  } catch (err) {
    log.error('Failed to start server', { error: err.message });
    process.exit(1);
  }
};

start();
