package com.example.assistant.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

enum class CoachingStrategy {
    GOAL_DECOMPOSITION,
    TIMELINE_EXTENSION,
    MOTIVATIONAL_REFRAME,
    REFLECTIVE_QUESTIONING,
    RECOVERY_SUGGESTION
}

data class ContextVector(
    val stress: Float = 0f,
    val valence: Float = 0f,
    val hrv: Float = 0f,
    val burnoutRisk: Float = 0f,
    val goalComplexity: Float = 0f
)

class ThompsonSamplingBandit(strategies: List<CoachingStrategy>) {
    private val alphas = strategies.associateWith { 1.0 }.toMutableMap()
    private val betas = strategies.associateWith { 1.0 }.toMutableMap()

    fun select(): CoachingStrategy {
        return alphas.keys.maxByOrNull { strategy ->
            sampleBeta(alphas[strategy]!!, betas[strategy]!!)
        } ?: CoachingStrategy.REFLECTIVE_QUESTIONING
    }

    fun update(strategy: CoachingStrategy, reward: Float) {
        if (reward > 0) {
            alphas[strategy] = (alphas[strategy] ?: 1.0) + reward
        } else {
            betas[strategy] = (betas[strategy] ?: 1.0) + abs(reward)
        }
    }

    fun getSuccessRate(strategy: CoachingStrategy): Float {
        val a = alphas[strategy] ?: 1.0
        val b = betas[strategy] ?: 1.0
        return (a / (a + b)).toFloat()
    }

    fun toMap(): Map<String, Map<String, Double>> {
        return mapOf(
            "alphas" to alphas.map { (k, v) -> k.name to v }.toMap(),
            "betas" to betas.map { (k, v) -> k.name to v }.toMap()
        )
    }

    fun loadFrom(data: Map<String, Map<String, Double>>) {
        data["alphas"]?.forEach { (name, value) ->
            try {
                alphas[CoachingStrategy.valueOf(name)] = value
            } catch (_: Exception) {}
        }
        data["betas"]?.forEach { (name, value) ->
            try {
                betas[CoachingStrategy.valueOf(name)] = value
            } catch (_: Exception) {}
        }
    }

    private fun sampleBeta(alpha: Double, beta: Double): Double {
        // Box-Muller approximation for Beta distribution
        val x = gammaVariate(alpha)
        val y = gammaVariate(beta)
        return if (x + y > 0) x / (x + y) else 0.5
    }

    private fun gammaVariate(shape: Double): Double {
        if (shape < 1.0) {
            val u = Random.nextDouble()
            return gammaVariate(shape + 1.0) * u.pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = Random.nextGaussian()
                v = 1.0 + c * x
            } while (v <= 0)
            v = v * v * v
            val u = Random.nextDouble()
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) return d * v
            if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return d * v
        }
    }

    private fun Random.nextGaussian(): Double {
        val u1 = nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * kotlin.math.PI * u2)
    }
}

object RewardEstimator {
    fun calculate(
        stressBefore: Float,
        stressAfter: Float,
        valenceBefore: Float,
        valenceAfter: Float,
        hrvBefore: Float = 0f,
        hrvAfter: Float = 0f
    ): Float {
        val stressReduction = stressBefore - stressAfter
        val valenceIncrease = valenceAfter - valenceBefore
        val hrvIncrease = (hrvAfter - hrvBefore) / 100f

        val reward = stressReduction * 0.4f + valenceIncrease * 0.4f + hrvIncrease * 0.2f
        return reward.coerceIn(-1f, 1f)
    }
}

data class SupportProfile(
    val userId: String,
    var preferredStyle: String = "supportive",
    val effectiveStrategies: MutableList<String> = mutableListOf(),
    var resilienceIndex: Float = 0.5f,
    var adaptationSpeed: Float = 0.5f
) {
    fun updateFromFeedback(strategy: String, reward: Float) {
        if (reward > 0.3f && strategy !in effectiveStrategies) {
            effectiveStrategies.add(strategy)
        }
        resilienceIndex = 0.9f * resilienceIndex + 0.1f * (if (reward > 0) 1f else 0f)
    }
}

class LocalPolicyEngine(
    private val userId: String,
    private val persistence: PolicyPersistence? = null
) {
    private val bandit = ThompsonSamplingBandit(CoachingStrategy.entries)
    private val profile = SupportProfile(userId)

    init {
        persistence?.let { loadPolicy() }
    }

    fun selectStrategy(context: ContextVector): CoachingStrategy {
        return bandit.select()
    }

    fun updatePolicy(strategy: CoachingStrategy, reward: Float) {
        bandit.update(strategy, reward)
        profile.updateFromFeedback(strategy.name, reward)
        persistence?.save(bandit.toMap(), profile)
    }

    fun getProfile(): SupportProfile = profile

    fun getStrategyStats(): Map<CoachingStrategy, Float> {
        return CoachingStrategy.entries.associateWith { bandit.getSuccessRate(it) }
    }

    fun reset() {
        CoachingStrategy.entries.forEach {
            bandit.update(it, 0f) // Reset to priors
        }
        persistence?.clear()
    }

    private fun loadPolicy() {
        persistence?.load()?.let { data ->
            bandit.loadFrom(data)
        }
    }
}

interface PolicyPersistence {
    fun save(policy: Map<String, Map<String, Double>>, profile: SupportProfile)
    fun load(): Map<String, Map<String, Double>>?
    fun clear()
}
