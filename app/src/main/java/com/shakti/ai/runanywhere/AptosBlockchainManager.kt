package com.shakti.ai.runanywhere

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Aptos Blockchain Manager
 * 
 * Handles evidence hash anchoring to Aptos blockchain:
 * - Queue evidence for anchoring
 * - Retry failed transactions
 * - Verify anchored evidence
 * - Testnet and Mainnet support
 * - Generates court-admissible certificates
 * 
 * Aptos Features:
 * - Immutable timestamps
 * - SHA-256 hash storage
 * - Low transaction costs
 * - Fast finality (~1 second)
 * - Privacy-preserving (only hash stored)
 */
class AptosBlockchainManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AptosBlockchain"
        
        // Aptos RPC endpoints
        private const val TESTNET_RPC = "https://fullnode.testnet.aptoslabs.com/v1"
        private const val MAINNET_RPC = "https://fullnode.mainnet.aptoslabs.com/v1"
        
        // Contract addresses (deployed on Aptos)
        private const val TESTNET_CONTRACT = "0x1::shakti_evidence::EvidenceRegistry"
        private const val MAINNET_CONTRACT = "0x1::shakti_evidence::EvidenceRegistry"
        
        // Transaction settings
        private const val MAX_GAS_AMOUNT = 2000
        private const val GAS_UNIT_PRICE = 100
        private const val TRANSACTION_TIMEOUT_SEC = 60L
        
        // Retry settings
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L
        
        @Volatile
        private var INSTANCE: AptosBlockchainManager? = null
        
        fun getInstance(context: Context): AptosBlockchainManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AptosBlockchainManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Evidence queue (for offline/retry)
    private val evidenceQueue = mutableListOf<QueuedEvidence>()
    
    // Current network (testnet by default)
    private var useMainnet = false
    
    private val currentRPC: String
        get() = if (useMainnet) MAINNET_RPC else TESTNET_RPC
    
    private val currentContract: String
        get() = if (useMainnet) MAINNET_CONTRACT else TESTNET_CONTRACT

    /**
     * Anchor evidence hash to Aptos blockchain
     */
    suspend fun anchorEvidence(evidence: EvidencePackage): AnchorResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Anchoring evidence: ${evidence.evidenceId}")
        
        try {
            // Create anchor payload
            val anchor = AptosEvidenceAnchor(
                evidenceHash = evidence.evidenceHash,
                timestamp = evidence.timestamp,
                location = evidence.location,
                threatType = evidence.threatDetection.threatType,
                userId = "anonymous" // Privacy: no PII
            )
            
            // Submit transaction
            val txHash = submitAnchorTransaction(anchor)
            
            if (txHash != null) {
                // Wait for confirmation
                val confirmed = waitForConfirmation(txHash)
                
                if (confirmed) {
                    Log.i(TAG, "Evidence anchored successfully: $txHash")
                    return@withContext AnchorResult(
                        success = true,
                        txHash = txHash,
                        blockHeight = getBlockHeight(),
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
            
            // Failed - add to queue for retry
            queueEvidence(evidence, "Failed to anchor")
            
            return@withContext AnchorResult(
                success = false,
                error = "Transaction failed"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error anchoring evidence", e)
            queueEvidence(evidence, e.message)
            return@withContext AnchorResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Submit anchor transaction to Aptos
     */
    private suspend fun submitAnchorTransaction(anchor: AptosEvidenceAnchor): String? = withContext(Dispatchers.IO) {
        try {
            // Create Move function call payload
            val payload = createMovePayload(anchor)
            
            // Submit transaction
            val request = Request.Builder()
                .url("$currentRPC/transactions")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val txResponse = json.decodeFromString<TransactionResponse>(body)
                    return@withContext txResponse.hash
                }
            } else {
                Log.w(TAG, "Transaction submission failed: ${response.code}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting transaction", e)
            return@withContext null
        }
    }

    /**
     * Create Move function call payload
     */
    private fun createMovePayload(anchor: AptosEvidenceAnchor): String {
        // Move function: anchor_evidence(evidence_hash, timestamp, lat, lon, threat_type)
        return """
        {
          "type": "entry_function_payload",
          "function": "$currentContract::anchor_evidence",
          "type_arguments": [],
          "arguments": [
            "${anchor.evidenceHash}",
            "${anchor.timestamp}",
            "${anchor.location?.latitude ?: 0.0}",
            "${anchor.location?.longitude ?: 0.0}",
            "${anchor.threatType.name}"
          ]
        }
        """.trimIndent()
    }

    /**
     * Wait for transaction confirmation
     */
    private suspend fun waitForConfirmation(txHash: String): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 30 // 30 seconds max wait
        
        while (attempts < maxAttempts) {
            try {
                val request = Request.Builder()
                    .url("$currentRPC/transactions/by_hash/$txHash")
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val tx = json.decodeFromString<TransactionStatus>(body)
                        if (tx.success == true) {
                            return@withContext true
                        }
                    }
                }
                
                delay(1000) // Wait 1 second
                attempts++
                
            } catch (e: Exception) {
                Log.w(TAG, "Error checking transaction status", e)
                delay(1000)
                attempts++
            }
        }
        
        return@withContext false
    }

    /**
     * Get current block height
     */
    private suspend fun getBlockHeight(): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$currentRPC/")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val ledger = json.decodeFromString<LedgerInfo>(body)
                    return@withContext ledger.block_height.toLongOrNull() ?: 0L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting block height", e)
        }
        
        return@withContext 0L
    }

    /**
     * Verify evidence on blockchain
     */
    suspend fun verifyEvidence(evidenceHash: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            // Query blockchain for evidence hash
            val request = Request.Builder()
                .url("$currentRPC/accounts/$currentContract/resource/0x1::shakti_evidence::Evidence/$evidenceHash")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val evidence = json.decodeFromString<BlockchainEvidence>(body)
                    return@withContext VerificationResult(
                        exists = true,
                        timestamp = evidence.timestamp,
                        blockHeight = evidence.blockHeight,
                        isValid = true
                    )
                }
            }
            
            return@withContext VerificationResult(exists = false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying evidence", e)
            return@withContext VerificationResult(
                exists = false,
                error = e.message
            )
        }
    }

    /**
     * Generate legal certificate with blockchain proof
     */
    suspend fun generateLegalCertificate(evidence: EvidencePackage): LegalCertificate = withContext(Dispatchers.IO) {
        val verification = verifyEvidence(evidence.evidenceHash)
        
        LegalCertificate(
            evidenceId = evidence.evidenceId,
            evidenceHash = evidence.evidenceHash,
            timestamp = evidence.timestamp,
            location = evidence.location,
            threatType = evidence.threatDetection.threatType.name,
            riskScore = evidence.threatDetection.calculateRiskScore(),
            blockchainVerified = verification.exists,
            blockchainTxHash = evidence.blockchainTxHash,
            blockHeight = verification.blockHeight,
            certificateHash = generateCertificateHash(evidence),
            generatedAt = System.currentTimeMillis(),
            isCourtAdmissible = verification.exists && verification.isValid
        )
    }

    /**
     * Generate certificate hash
     */
    private fun generateCertificateHash(evidence: EvidencePackage): String {
        val content = "${evidence.evidenceId}|${evidence.evidenceHash}|${evidence.timestamp}"
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Queue evidence for later anchoring
     */
    private fun queueEvidence(evidence: EvidencePackage, reason: String?) {
        val queued = QueuedEvidence(
            evidence = evidence,
            queuedAt = System.currentTimeMillis(),
            retryCount = 0,
            lastError = reason
        )
        evidenceQueue.add(queued)
        Log.d(TAG, "Evidence queued for retry: ${evidence.evidenceId}")
    }

    /**
     * Process queued evidence (call periodically when online)
     */
    suspend fun processQueue() = withContext(Dispatchers.IO) {
        if (evidenceQueue.isEmpty()) {
            return@withContext
        }
        
        Log.i(TAG, "Processing ${evidenceQueue.size} queued evidence items")
        
        val iterator = evidenceQueue.iterator()
        while (iterator.hasNext()) {
            val queued = iterator.next()
            
            if (queued.retryCount >= MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Max retries exceeded for ${queued.evidence.evidenceId}")
                iterator.remove()
                continue
            }
            
            // Try to anchor
            val result = anchorEvidence(queued.evidence)
            
            if (result.success) {
                Log.i(TAG, "Queued evidence anchored successfully: ${queued.evidence.evidenceId}")
                iterator.remove()
            } else {
                // Update retry count
                queued.retryCount++
                queued.lastError = result.error
                delay(RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Get queue status
     */
    fun getQueueStatus(): QueueStatus {
        return QueueStatus(
            totalQueued = evidenceQueue.size,
            oldestQueuedTime = evidenceQueue.minOfOrNull { it.queuedAt } ?: 0L,
            failedRetries = evidenceQueue.count { it.retryCount >= MAX_RETRY_ATTEMPTS }
        )
    }

    /**
     * Switch between testnet and mainnet
     */
    fun setMainnet(enabled: Boolean) {
        useMainnet = enabled
        Log.i(TAG, "Switched to ${if (enabled) "MAINNET" else "TESTNET"}")
    }

    /**
     * Check if online and can submit transactions
     */
    suspend fun isBlockchainAccessible(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$currentRPC/")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            return@withContext false
        }
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

data class AnchorResult(
    val success: Boolean,
    val txHash: String? = null,
    val blockHeight: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val error: String? = null
)

data class VerificationResult(
    val exists: Boolean,
    val timestamp: Long? = null,
    val blockHeight: Long? = null,
    val isValid: Boolean = false,
    val error: String? = null
)

data class LegalCertificate(
    val evidenceId: String,
    val evidenceHash: String,
    val timestamp: Long,
    val location: LocationEvidence?,
    val threatType: String,
    val riskScore: Float,
    val blockchainVerified: Boolean,
    val blockchainTxHash: String?,
    val blockHeight: Long?,
    val certificateHash: String,
    val generatedAt: Long,
    val isCourtAdmissible: Boolean
) {
    /**
     * Generate PDF certificate content
     */
    fun toPDFContent(): String {
        return """
        ═══════════════════════════════════════════════════════════
                    SHAKTI AI - LEGAL EVIDENCE CERTIFICATE
        ═══════════════════════════════════════════════════════════
        
        EVIDENCE DETAILS
        ────────────────────────────────────────────────────────────
        Evidence ID:        $evidenceId
        Evidence Hash:      $evidenceHash
        Timestamp:          ${formatTimestamp(timestamp)}
        Location:           ${formatLocation(location)}
        Threat Type:        $threatType
        Risk Score:         ${(riskScore * 100).toInt()}%
        
        BLOCKCHAIN VERIFICATION
        ────────────────────────────────────────────────────────────
        Blockchain:         Aptos ${if (blockchainVerified) "✓ VERIFIED" else "⚠ PENDING"}
        Transaction Hash:   ${blockchainTxHash ?: "N/A"}
        Block Height:       ${blockHeight ?: "N/A"}
        Immutable Proof:    ${if (blockchainVerified) "YES" else "NO"}
        
        LEGAL STATUS
        ────────────────────────────────────────────────────────────
        Court Admissible:   ${if (isCourtAdmissible) "YES ✓" else "PENDING"}
        Certificate Hash:   $certificateHash
        Generated:          ${formatTimestamp(generatedAt)}
        
        VERIFICATION INSTRUCTIONS
        ────────────────────────────────────────────────────────────
        1. Verify evidence hash on Aptos blockchain explorer
        2. Check transaction timestamp matches evidence timestamp
        3. Validate certificate hash: $certificateHash
        4. Cross-reference with original evidence package
        
        This certificate provides cryptographic proof that the evidence
        existed at the specified timestamp and has not been tampered with.
        The evidence hash is immutably stored on the Aptos blockchain.
        
        ═══════════════════════════════════════════════════════════
                    Generated by SHAKTI AI Evidence System
                    For legal inquiries: legal@shaktiai.org
        ═══════════════════════════════════════════════════════════
        """.trimIndent()
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.US).format(date)
    }
    
    private fun formatLocation(location: LocationEvidence?): String {
        return location?.let {
            "${it.latitude}, ${it.longitude} (±${it.accuracy}m)"
        } ?: "Unknown"
    }
}

data class QueuedEvidence(
    val evidence: EvidencePackage,
    val queuedAt: Long,
    var retryCount: Int,
    var lastError: String?
)

data class QueueStatus(
    val totalQueued: Int,
    val oldestQueuedTime: Long,
    val failedRetries: Int
)

// Aptos API response models
@Serializable
data class TransactionResponse(
    val hash: String,
    val sender: String? = null,
    val sequence_number: String? = null
)

@Serializable
data class TransactionStatus(
    val hash: String,
    val success: Boolean? = null,
    val vm_status: String? = null
)

@Serializable
data class LedgerInfo(
    val chain_id: Int,
    val epoch: String,
    val ledger_version: String,
    val oldest_ledger_version: String,
    val ledger_timestamp: String,
    val node_role: String,
    val oldest_block_height: String,
    val block_height: String
)

@Serializable
data class BlockchainEvidence(
    val timestamp: Long,
    val blockHeight: Long,
    val evidenceHash: String
)
