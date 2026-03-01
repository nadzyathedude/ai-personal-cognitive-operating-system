import OpenAI from 'openai';
import { config } from '../config/env.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('LLM');

const openai = new OpenAI({ apiKey: config.openaiApiKey });

const tools = [
  {
    type: 'function',
    function: {
      name: 'get_current_time',
      description: 'Get the current date and time',
      parameters: { type: 'object', properties: {}, required: [] },
    },
  },
  {
    type: 'function',
    function: {
      name: 'calculate',
      description: 'Evaluate a mathematical expression',
      parameters: {
        type: 'object',
        properties: {
          expression: { type: 'string', description: 'The math expression to evaluate' },
        },
        required: ['expression'],
      },
    },
  },
];

function executeToolCall(name, args) {
  switch (name) {
    case 'get_current_time':
      return JSON.stringify({ time: new Date().toISOString() });
    case 'calculate':
      try {
        const result = Function(`"use strict"; return (${args.expression.replace(/[^0-9+\-*/().%\s]/g, '')})`)();
        return JSON.stringify({ result });
      } catch {
        return JSON.stringify({ error: 'Invalid expression' });
      }
    default:
      return JSON.stringify({ error: 'Unknown tool' });
  }
}

export async function* streamLlmResponse(conversationHistory) {
  const messages = [
    { role: 'system', content: config.llmSystemPrompt },
    ...conversationHistory,
  ];

  log.info('Starting LLM stream', { messageCount: messages.length });

  const stream = await openai.chat.completions.create({
    model: config.llmModel,
    messages,
    stream: true,
    tools,
    max_tokens: 1024,
  });

  let fullText = '';
  let toolCalls = [];
  let currentToolCall = null;

  for await (const chunk of stream) {
    const delta = chunk.choices[0]?.delta;
    if (!delta) continue;

    if (delta.tool_calls) {
      for (const tc of delta.tool_calls) {
        if (tc.index !== undefined) {
          if (!toolCalls[tc.index]) {
            toolCalls[tc.index] = { id: tc.id || '', function: { name: '', arguments: '' } };
          }
          currentToolCall = toolCalls[tc.index];
        }
        if (tc.id) currentToolCall.id = tc.id;
        if (tc.function?.name) currentToolCall.function.name += tc.function.name;
        if (tc.function?.arguments) currentToolCall.function.arguments += tc.function.arguments;
      }
    }

    if (delta.content) {
      fullText += delta.content;
      yield { type: 'token', token: delta.content };
    }

    if (chunk.choices[0]?.finish_reason === 'tool_calls' && toolCalls.length > 0) {
      log.info('Tool calls detected', { count: toolCalls.length });

      const assistantMessage = { role: 'assistant', content: null, tool_calls: toolCalls.map((tc, i) => ({
        id: tc.id,
        type: 'function',
        function: { name: tc.function.name, arguments: tc.function.arguments },
      })) };

      const toolMessages = toolCalls.map(tc => {
        let args = {};
        try { args = JSON.parse(tc.function.arguments); } catch {}
        const result = executeToolCall(tc.function.name, args);
        return { role: 'tool', tool_call_id: tc.id, content: result };
      });

      messages.push(assistantMessage, ...toolMessages);

      const followUp = await openai.chat.completions.create({
        model: config.llmModel,
        messages,
        stream: true,
        max_tokens: 1024,
      });

      for await (const chunk2 of followUp) {
        const content = chunk2.choices[0]?.delta?.content;
        if (content) {
          fullText += content;
          yield { type: 'token', token: content };
        }
      }
    }
  }

  yield { type: 'done', fullText };
}
