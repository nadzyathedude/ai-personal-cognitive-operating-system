import logging
from enum import Enum

import numpy as np

logger = logging.getLogger(__name__)


class CoachingStrategy(str, Enum):
    GOAL_DECOMPOSITION = "goal_decomposition"
    TIMELINE_EXTENSION = "timeline_extension"
    MOTIVATIONAL_REFRAME = "motivational_reframe"
    REFLECTIVE_QUESTIONING = "reflective_questioning"
    RECOVERY_SUGGESTION = "recovery_suggestion"


class MultiArmedBanditPolicy:
    def __init__(self, strategies: list[str]):
        self.strategies = strategies
        self.successes = {s: 1.0 for s in strategies}
        self.failures = {s: 1.0 for s in strategies}

    def select(self) -> str:
        samples = {}
        for strategy in self.strategies:
            alpha = self.successes[strategy]
            beta = self.failures[strategy]
            samples[strategy] = np.random.beta(alpha, beta)

        return max(samples, key=samples.get)

    def update(self, strategy: str, reward: float):
        if reward > 0:
            self.successes[strategy] += reward
        else:
            self.failures[strategy] += abs(reward)

    def get_stats(self) -> dict:
        stats = {}
        for strategy in self.strategies:
            total = self.successes[strategy] + self.failures[strategy]
            stats[strategy] = {
                "success_rate": round(
                    self.successes[strategy] / total, 4
                ),
                "total_interactions": int(total - 2),
            }
        return stats

    def to_dict(self) -> dict:
        return {
            "successes": dict(self.successes),
            "failures": dict(self.failures),
        }

    @classmethod
    def from_dict(cls, data: dict, strategies: list[str]) -> "MultiArmedBanditPolicy":
        policy = cls(strategies)
        policy.successes = data.get("successes", policy.successes)
        policy.failures = data.get("failures", policy.failures)
        return policy


class RewardEstimator:
    @staticmethod
    def calculate(
        stress_before: float,
        stress_after: float,
        valence_before: float,
        valence_after: float,
        hrv_before: float = 0.0,
        hrv_after: float = 0.0,
    ) -> float:
        stress_reduction = stress_before - stress_after
        valence_increase = valence_after - valence_before
        hrv_increase = (hrv_after - hrv_before) / 100.0

        reward = (
            stress_reduction * 0.4
            + valence_increase * 0.4
            + hrv_increase * 0.2
        )

        return max(-1.0, min(1.0, reward))


class UserSupportProfile:
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.preferred_tone: str = "supportive"
        self.effective_strategies: list[str] = []
        self.resilience_index: float = 0.5
        self.adaptation_speed: float = 0.5

    def update_from_feedback(self, strategy: str, reward: float):
        if reward > 0.3 and strategy not in self.effective_strategies:
            self.effective_strategies.append(strategy)

        self.resilience_index = 0.9 * self.resilience_index + 0.1 * (
            1.0 if reward > 0 else 0.0
        )

    def to_dict(self) -> dict:
        return {
            "user_id": self.user_id,
            "preferred_tone": self.preferred_tone,
            "effective_strategies": self.effective_strategies,
            "resilience_index": round(self.resilience_index, 4),
            "adaptation_speed": round(self.adaptation_speed, 4),
        }


# Per-user policy storage (in-memory; production should use DB)
_user_policies: dict[str, MultiArmedBanditPolicy] = {}
_user_profiles: dict[str, UserSupportProfile] = {}

ALL_STRATEGIES = [s.value for s in CoachingStrategy]


def get_user_policy(user_id: str) -> MultiArmedBanditPolicy:
    if user_id not in _user_policies:
        _user_policies[user_id] = MultiArmedBanditPolicy(ALL_STRATEGIES)
    return _user_policies[user_id]


def get_user_profile(user_id: str) -> UserSupportProfile:
    if user_id not in _user_profiles:
        _user_profiles[user_id] = UserSupportProfile(user_id)
    return _user_profiles[user_id]


reward_estimator = RewardEstimator()
