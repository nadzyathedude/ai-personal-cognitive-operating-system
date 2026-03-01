package com.example.assistant.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.assistant.engine.PolicyPersistence
import com.example.assistant.engine.SupportProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidPolicyPersistence(context: Context) : PolicyPersistence {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setKeyGenParameterSpec(
            KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "policy_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(policy: Map<String, Map<String, Double>>, profile: SupportProfile) {
        prefs.edit()
            .putString("policy_data", json.encodeToString(policy))
            .putString("profile_user_id", profile.userId)
            .putString("profile_style", profile.preferredStyle)
            .putFloat("profile_resilience", profile.resilienceIndex)
            .putFloat("profile_adaptation", profile.adaptationSpeed)
            .putStringSet("profile_strategies", profile.effectiveStrategies.toSet())
            .apply()
    }

    override fun load(): Map<String, Map<String, Double>>? {
        val raw = prefs.getString("policy_data", null) ?: return null
        return try {
            json.decodeFromString<Map<String, Map<String, Double>>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
