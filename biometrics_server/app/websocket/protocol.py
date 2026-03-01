import json
from enum import Enum


class ClientMessageType(str, Enum):
    VOICE_VERIFICATION_START = "voice_verification_start"
    VOICE_VERIFICATION_AUDIO = "voice_verification_audio"
    VOICE_VERIFICATION_END = "voice_verification_end"
    STRESS_ANALYSIS_START = "stress_analysis_start"
    STRESS_ANALYSIS_AUDIO = "stress_analysis_audio"
    STRESS_ANALYSIS_END = "stress_analysis_end"


class ServerMessageType(str, Enum):
    VERIFICATION_RESULT = "verification_result"
    VERIFICATION_ERROR = "verification_error"
    STRESS_RESULT = "stress_result"
    STRESS_ERROR = "stress_error"
    ERROR = "error"


def parse_message(raw: str) -> dict | None:
    try:
        data = json.loads(raw)
        if "type" not in data:
            return None
        return data
    except json.JSONDecodeError:
        return None


def server_msg(msg_type: ServerMessageType, payload: dict | None = None) -> str:
    msg = {"type": msg_type.value}
    if payload:
        msg.update(payload)
    return json.dumps(msg)
