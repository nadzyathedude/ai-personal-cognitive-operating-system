import logging
import uuid

from openai import AsyncOpenAI
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.tables import MoodJournal
from app.services.emotion_analyzer import emotion_analyzer
from app.services.emotion_fusion import emotion_fusion_engine

logger = logging.getLogger(__name__)

JOURNAL_SYSTEM_PROMPT = """You are a supportive, reflective listening companion. Your role is to help users process their day through thoughtful conversation.

Guidelines:
- Use a warm, coaching tone
- Encourage self-reflection without being directive
- Ask open-ended questions that explore feelings and experiences
- Never diagnose, prescribe, or give medical/therapeutic advice
- Avoid clinical or therapy-specific language
- Be genuinely curious about the person's experience
- Validate emotions without judgment
- Focus on what the person felt and experienced, not on solving problems"""

FOLLOWUP_PROMPT_TEMPLATE = """Based on the user's response about their day and the detected emotional state, generate exactly 3 follow-up questions.

User's answer: "{answer}"
Detected emotion: {emotion} (confidence: {confidence})
Sentiment: {sentiment}

Generate 3 questions that:
1. Are open-ended and invite reflection
2. Are emotionally intelligent and sensitive to the detected mood
3. Do not give medical advice or diagnose
4. Are supportive and encouraging
5. Progress naturally from surface-level to deeper reflection

Return ONLY the 3 questions, one per line, numbered 1-3. No other text."""

SUMMARY_PROMPT_TEMPLATE = """Based on this mood journal session, write a brief, supportive reflective summary (2-3 sentences).

Initial question: "How was your day?"
User's initial answer: "{initial_answer}"
Detected emotion: {emotion}

Follow-up questions and answers:
{qa_pairs}

Write a warm, validating summary that:
- Acknowledges the user's emotional experience
- Highlights any positive moments or growth
- Ends with an encouraging note
- Does NOT diagnose or give medical advice

Return ONLY the summary text."""


class MoodJournalService:
    def __init__(self):
        self._client: AsyncOpenAI | None = None

    def _get_client(self) -> AsyncOpenAI:
        if self._client is None:
            self._client = AsyncOpenAI(api_key=settings.openai_api_key)
        return self._client

    async def start_session(self, user_id: str, db: AsyncSession) -> dict:
        session_id = str(uuid.uuid4())
        journal = MoodJournal(session_id=session_id, user_id=user_id)
        db.add(journal)
        await db.commit()

        return {
            "session_id": session_id,
            "question": "How was your day?",
        }

    async def process_initial_answer(
        self, session_id: str, answer: str, db: AsyncSession
    ) -> dict:
        result = await db.execute(
            select(MoodJournal).where(MoodJournal.session_id == session_id)
        )
        journal = result.scalar_one_or_none()
        if journal is None:
            raise ValueError("Session not found")

        emotion_result = await emotion_analyzer.analyze_async(answer)

        journal.initial_answer = answer
        journal.detected_sentiment = emotion_result["sentiment"]
        journal.detected_emotion = emotion_result["primary_emotion"]
        journal.emotion_confidence = emotion_result["confidence"]
        journal.valence = emotion_result["valence"]
        journal.arousal = emotion_result["arousal"]

        # Derive stress score from text emotion (negative valence + high arousal = stress)
        valence = emotion_result.get("valence", 0.0)
        arousal = emotion_result.get("arousal", 0.0)
        text_stress = max(0.0, min(1.0, (1.0 - valence) / 2.0 * 0.6 + max(0.0, arousal) * 0.4))
        journal.stress_score = round(text_stress, 4)

        fusion_result = None
        if journal.acoustic_emotion and journal.vocal_stress_score is not None:
            acoustic_for_fusion = {
                "detected_emotion": journal.acoustic_emotion,
                "valence_estimate": journal.fusion_valence or 0.0,
                "arousal": journal.fusion_arousal or 0.0,
                "vocal_stress_score": journal.vocal_stress_score,
                "confidence": journal.fusion_confidence or 0.5,
                "tone_descriptor": journal.tone_descriptor or "",
            }
            fusion_result = emotion_fusion_engine.fuse(emotion_result, acoustic_for_fusion)

            journal.fusion_valence = fusion_result["final_valence"]
            journal.fusion_arousal = fusion_result["arousal_level"]
            journal.fusion_stress_index = fusion_result["stress_index"]
            journal.fusion_confidence = fusion_result["fusion_confidence"]
            journal.stress_score = fusion_result["stress_index"]  # Override with fusion stress
            if fusion_result.get("mismatch_detected"):
                journal.mismatch_detected = fusion_result["mismatch_type"]

        followup_questions = await self._generate_followup_questions(
            answer, emotion_result, fusion_result
        )
        journal.followup_questions = followup_questions
        journal.followup_answers = []

        await db.commit()

        response = {
            "emotion": emotion_result,
            "followup_questions": followup_questions,
        }
        if fusion_result:
            response["fusion"] = fusion_result
        return response

    async def process_followup_answer(
        self, session_id: str, question_index: int, answer: str, db: AsyncSession
    ) -> dict:
        result = await db.execute(
            select(MoodJournal).where(MoodJournal.session_id == session_id)
        )
        journal = result.scalar_one_or_none()
        if journal is None:
            raise ValueError("Session not found")

        answers = journal.followup_answers or []
        answers.append({"index": question_index, "answer": answer})
        journal.followup_answers = answers

        is_complete = len(answers) >= 3

        if is_complete:
            summary = await self._generate_summary(journal)
            journal.summary = summary

        await db.commit()

        return {
            "complete": is_complete,
            "progress": len(answers),
            "total": 3,
            "summary": journal.summary if is_complete else None,
        }

    async def _generate_followup_questions(
        self, answer: str, emotion: dict, fusion: dict | None = None
    ) -> list[str]:
        client = self._get_client()

        tone_context = ""
        if fusion and fusion.get("mismatch_detected"):
            tone_context = (
                f"\nNote: A mismatch was detected between the user's words and voice tone. "
                f"Type: {fusion.get('mismatch_type', 'unknown')}. "
                f"Tone descriptor: {fusion.get('tone_descriptor', 'unknown')}. "
                f"Consider gently exploring this discrepancy in one of the questions."
            )

        prompt = FOLLOWUP_PROMPT_TEMPLATE.format(
            answer=answer,
            emotion=emotion["primary_emotion"],
            confidence=emotion["confidence"],
            sentiment=emotion["sentiment"],
        ) + tone_context

        response = await client.chat.completions.create(
            model=settings.llm_model,
            messages=[
                {"role": "system", "content": JOURNAL_SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            max_tokens=300,
            temperature=0.7,
        )

        text = response.choices[0].message.content.strip()
        lines = [
            line.lstrip("0123456789.)- ").strip()
            for line in text.split("\n")
            if line.strip()
        ]
        return lines[:3]

    async def _generate_summary(self, journal: MoodJournal) -> str:
        client = self._get_client()

        qa_pairs = ""
        questions = journal.followup_questions or []
        answers = journal.followup_answers or []
        for i, q in enumerate(questions):
            a = next(
                (ans["answer"] for ans in answers if ans["index"] == i),
                "(not answered)",
            )
            qa_pairs += f"Q{i + 1}: {q}\nA{i + 1}: {a}\n\n"

        prompt = SUMMARY_PROMPT_TEMPLATE.format(
            initial_answer=journal.initial_answer or "",
            emotion=journal.detected_emotion or "unknown",
            qa_pairs=qa_pairs,
        )

        response = await client.chat.completions.create(
            model=settings.llm_model,
            messages=[
                {"role": "system", "content": JOURNAL_SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            max_tokens=200,
            temperature=0.7,
        )

        return response.choices[0].message.content.strip()


mood_journal_service = MoodJournalService()
