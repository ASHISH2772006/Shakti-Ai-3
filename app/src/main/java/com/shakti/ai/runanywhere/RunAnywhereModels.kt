package com.shakti.ai.runanywhere

import android.location.Location
import java.security.MessageDigest

/**
 * RunAnywhere Digital Bodyguard - Data Models
 *
 * Complete data structures for privacy-first, offline-capable threat detection
 * and evidence management with blockchain anchoring.
 */

// ============================================================================
// THREAT DETECTION MODELS
// ============================================================================

/**
 * Multi-sensor threat detection result
 */
data class ThreatDetection(
    val timestamp: Long = System.currentTimeMillis(),
    val audioConfidence: Float = 0f,
    val motionConfidence: Float = 0f,
    val bleProximityScore: Float = 0f,
    val cameraScore: Float = 0f,
    val riskScore: Float = 0f,
    val threatType: ThreatType = ThreatType.NONE,
    val location: Location? = null
) {
    /**
     * Calculate combined risk score from all sensors
     */
    fun calculateRiskScore(): Float {
        return (audioConfidence * 0.4f +
                motionConfidence * 0.2f +
                bleProximityScore * 0.2f +
                cameraScore * 0.2f).coerceIn(0f, 1f)
    }

    /**
     * Check if risk exceeds threshold
     */
    fun isThreat(threshold: Float = 0.7f): Boolean {
        return calculateRiskScore() >= threshold
    }
}

/**
 * Threat types detected by the system
 */
enum class ThreatType {
    NONE,
    AUDIO_DISTRESS,      // Scream, aggressive voice, gunshot
    SUDDEN_MOTION,        // Sudden phone movement (dropped, thrown)
    SUSPICIOUS_PROXIMITY, // Unknown BLE devices approaching
    VISUAL_THREAT,        // Face detection shows aggressive behavior
    COMBINED              // Multiple sensors triggered
}

/**
 * Audio analysis result from TinyML model
 */
data class AudioThreatResult(
    val timestamp: Long = System.currentTimeMillis(),
    val isScream: Boolean = false,
    val isAggressiveVoice: Boolean = false,
    val isGunshot: Boolean = false,
    val isGlassBreak: Boolean = false,
    val confidence: Float = 0f,
    val audioType: String = "normal",
    val decibels: Float = 0f
)

/**
 * IMU (motion sensor) analysis result
 */
data class MotionAnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    val isSuddenMotion: Boolean = false,
    val accelerationMagnitude: Float = 0f,
    val gyroscopeMagnitude: Float = 0f,
    val isShaking: Boolean = false,
    val isDropped: Boolean = false
)

/**
 * BLE proximity detection result
 */
data class BLEProximityResult(
    val timestamp: Long = System.currentTimeMillis(),
    val nearbyDevices: List<BLEDevice> = emptyList(),
    val unknownDeviceCount: Int = 0,
    val shaktiUserCount: Int = 0,
    val suspiciousApproach: Boolean = false
)

data class BLEDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isShaktiUser: Boolean = false,
    val distance: Float = 0f // meters (estimated from RSSI)
)

// ============================================================================
// EVIDENCE PACKAGE MODELS
// ============================================================================

/**
 * Complete evidence package created during threat
 */
data class EvidencePackage(
    val evidenceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val threatDetection: ThreatDetection,
    val audioRecordingPath: String? = null,
    val videoRecordingPath: String? = null,
    val photoPath: String? = null,
    val location: LocationEvidence? = null,
    val sensorLogs: SensorLogs,
    val encryptionKey: ByteArray? = null,
    val evidenceHash: String = "",
    val blockchainTxHash: String? = null,
    val blockHeight: Long? = null,
    val isEncrypted: Boolean = false,
    val isAnchoredOnChain: Boolean = false
) {
    companion object {
        fun generateEvidenceId(): String {
            return "EVIDENCE_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
    }

    /**
     * Calculate SHA-256 hash of evidence package
     */
    fun calculateHash(): String {
        val content = buildString {
            append(evidenceId)
            append(timestamp)
            append(audioRecordingPath ?: "")
            append(videoRecordingPath ?: "")
            append(photoPath ?: "")
            append(location?.latitude ?: 0.0)
            append(location?.longitude ?: 0.0)
        }

        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert to JSON format for storage
     */
    fun toJson(): String {
        return """
        {
          "evidenceId": "$evidenceId",
          "timestamp": $timestamp,
          "location": {
            "latitude": ${location?.latitude ?: 0.0},
            "longitude": ${location?.longitude ?: 0.0},
            "accuracy": ${location?.accuracy ?: 0f},
            "address": "${location?.address ?: ""}"
          },
          "threatDetails": {
            "type": "${threatDetection.threatType}",
            "riskScore": ${threatDetection.riskScore},
            "audioConfidence": ${threatDetection.audioConfidence}
          },
          "files": {
            "audio": "${audioRecordingPath ?: ""}",
            "video": "${videoRecordingPath ?: ""}",
            "photo": "${photoPath ?: ""}"
          },
          "encrypted": $isEncrypted,
          "evidenceHash": "$evidenceHash",
          "blockchainTxHash": "${blockchainTxHash ?: ""}"
        }
        """.trimIndent()
    }
}

/**
 * Location information with address
 */
data class LocationEvidence(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val address: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Comprehensive sensor logs
 */
data class SensorLogs(
    val accelerometer: List<Float> = emptyList(),
    val gyroscope: List<Float> = emptyList(),
    val magnetometer: List<Float> = emptyList(),
    val lightLevel: Float = 0f,
    val batteryLevel: Int = 0,
    val networkType: String = "unknown"
)

// ============================================================================
// BLE MESH NETWORK MODELS
// ============================================================================

/**
 * SOS message broadcast via BLE mesh
 */
data class SOSBroadcast(
    val messageId: String = generateMessageId(),
    val senderId: String,
    val senderName: String,
    val urgency: UrgencyLevel = UrgencyLevel.HIGH,
    val location: LocationEvidence? = null,
    val threatType: ThreatType = ThreatType.NONE,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 0,
    val isOnline: Boolean = false
) {
    companion object {
        fun generateMessageId(): String {
            return "SOS_${System.currentTimeMillis()}_${(100..999).random()}"
        }

        fun fromBytes(bytes: ByteArray): SOSBroadcast? {
            return try {
                val parts = String(bytes).split("|")
                SOSBroadcast(
                    messageId = parts[0],
                    senderId = parts[1],
                    senderName = "User",
                    urgency = UrgencyLevel.values()[parts[2].toInt()],
                    location = LocationEvidence(
                        latitude = parts[3].toDouble(),
                        longitude = parts[4].toDouble(),
                        accuracy = 0f
                    ),
                    timestamp = parts[5].toLong()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convert to compact byte array for BLE transmission
     */
    fun toBytes(): ByteArray {
        // Create compact binary format for efficient BLE transmission
        return buildString {
            append(messageId)
            append("|")
            append(senderId)
            append("|")
            append(urgency.ordinal)
            append("|")
            append(location?.latitude ?: 0.0)
            append("|")
            append(location?.longitude ?: 0.0)
            append("|")
            append(timestamp)
        }.toByteArray()
    }
}

enum class UrgencyLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Nearby helper (SHAKTI user) available for rescue
 */
data class NearbyHelper(
    val userId: String,
    val name: String,
    val distance: Float, // meters
    val rssi: Int,
    val isAvailable: Boolean = true,
    val responseTime: Long = 0,
    val lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * Calculate priority score for helper selection
     */
    fun calculatePriority(): Float {
        var score = 1000f / (distance + 1f) // Closer is better
        if (isAvailable) score *= 2f
        if (responseTime < 60000) score *= 1.5f // Responded within 1 minute
        return score
    }
}

// ============================================================================
// AUTOMATED RESPONSE MODELS
// ============================================================================

/**
 * Automated response configuration
 */
data class AutomatedResponse(
    val isEnabled: Boolean = true,
    val isSilent: Boolean = false,
    val flashSiren: Boolean = true,
    val vibrate: Boolean = true,
    val playSiren: Boolean = false,
    val autoCall: Boolean = true,
    val autoSMS: Boolean = true,
    val autoWhatsApp: Boolean = false,
    val dialPolice: Boolean = false,
    val bleBroadcast: Boolean = true,
    val recordEvidence: Boolean = true
)

/**
 * Response action result
 */
data class ResponseAction(
    val actionType: ActionType,
    val status: ActionStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)

enum class ActionType {
    FLASH_SIREN,
    VIBRATE,
    PLAY_SIREN,
    CALL_CONTACT,
    SEND_SMS,
    SEND_WHATSAPP,
    DIAL_POLICE,
    BLE_BROADCAST,
    START_RECORDING,
    ANCHOR_BLOCKCHAIN
}

enum class ActionStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

// ============================================================================
// APTOS BLOCKCHAIN MODELS
// ============================================================================

/**
 * Evidence hash anchor for Aptos blockchain
 */
data class AptosEvidenceAnchor(
    val evidenceHash: String,
    val timestamp: Long = System.currentTimeMillis(),
    val location: LocationEvidence? = null,
    val threatType: ThreatType = ThreatType.NONE,
    val userId: String = "anonymous"
) {
    /**
     * Convert to Aptos Move transaction payload
     */
    fun toMovePayload(): String {
        return """
        {
          "function": "0x1::shakti::anchor_evidence",
          "type_arguments": [],
          "arguments": [
            "$evidenceHash",
            "$timestamp",
            "${location?.latitude ?: 0.0}",
            "${location?.longitude ?: 0.0}",
            "${threatType.name}"
          ]
        }
        """.trimIndent()
    }
}

/**
 * Blockchain anchor status
 */
data class BlockchainAnchorStatus(
    val evidenceId: String,
    val evidenceHash: String,
    val status: AnchorStatus = AnchorStatus.PENDING,
    val txHash: String? = null,
    val blockHeight: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val errorMessage: String? = null
)

enum class AnchorStatus {
    PENDING,
    QUEUED,
    SUBMITTED,
    CONFIRMED,
    FAILED
}

// ============================================================================
// MODEL MANAGEMENT
// ============================================================================

/**
 * TinyML model information
 */
data class TinyMLModel(
    val modelName: String,
    val modelType: ModelType,
    val filePath: String,
    val version: String = "1.0.0",
    val sizeBytes: Long = 0,
    val isLoaded: Boolean = false,
    val accuracy: Float = 0f,
    val inferenceTimeMs: Long = 0
)

enum class ModelType {
    AUDIO_CLASSIFIER,
    FACE_DETECTOR,
    EMOTION_CLASSIFIER,
    KEYWORD_SPOTTER
}

// ============================================================================
// USER PREFERENCES & SETTINGS
// ============================================================================

/**
 * Digital Bodyguard settings
 */
data class BodyguardSettings(
    val isEnabled: Boolean = false,
    val isStealthMode: Boolean = true,
    val sensitivity: Float = 0.7f, // 0.0 to 1.0
    val autoEscalate: Boolean = true,
    val confirmationTimeoutMs: Long = 2000, // 2 seconds
    val automatedResponse: AutomatedResponse = AutomatedResponse(),
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val privacySettings: PrivacySettings = PrivacySettings()
)

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isPrimary: Boolean = false
)

data class PrivacySettings(
    val encryptLocalStorage: Boolean = true,
    val autoDeleteAfterDays: Int = 30,
    val requireUserConsent: Boolean = true,
    val anonymizeBlockchainData: Boolean = true,
    val shareLocationWithHelpers: Boolean = true
)

// ============================================================================
// MONITORING STATE
// ============================================================================

/**
 * Current monitoring state
 */
data class MonitoringState(
    val isActive: Boolean = false,
    val startTime: Long = 0,
    val lastThreatDetection: ThreatDetection? = null,
    val totalThreatsDetected: Int = 0,
    val falsePositiveCount: Int = 0,
    val evidencePackagesCreated: Int = 0,
    val sosMessagesSent: Int = 0,
    val helpersNotified: Int = 0
)

/**
 * Real-time sensor status
 */
data class SensorStatus(
    val audioEnabled: Boolean = false,
    val cameraEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val bleEnabled: Boolean = false,
    val imuEnabled: Boolean = false,
    val batteryLevel: Int = 100,
    val storageAvailableGB: Float = 0f
)
