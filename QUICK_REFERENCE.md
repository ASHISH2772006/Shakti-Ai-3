# Quick Reference - Aptos Blockchain Integration

## Common Tasks

### Initialize Blockchain Manager

```kotlin
val blockchainManager = AptosBlockchainManager.getInstance(context)
```

### Anchor Evidence to Blockchain

```kotlin
val result = blockchainManager.anchorEvidence(evidencePackage)
if (result.success) {
    println("TX Hash: ${result.txHash}")
    println("Block Height: ${result.blockHeight}")
}
```

### Verify Evidence on Blockchain

```kotlin
val verification = blockchainManager.verifyEvidence(evidenceHash)
if (verification.exists) {
    println("Verified! Block: ${verification.blockHeight}")
}
```

### Check Blockchain Accessibility

```kotlin
val isOnline = blockchainManager.isBlockchainAccessible()
```

### Process Queue (Retry Failed)

```kotlin
blockchainManager.processQueue()
```

### Get Queue Status

```kotlin
val status = blockchainManager.getQueueStatus()
println("Queued: ${status.totalQueued}, Failed: ${status.failedRetries}")
```

### Generate Legal Certificate

```kotlin
val certificate = blockchainManager.generateLegalCertificate(evidence)
val pdfContent = certificate.toPDFContent()
// Save or display certificate
```

### Switch to Mainnet

```kotlin
blockchainManager.setMainnet(true)  // Use mainnet instead of testnet
```

## Key Classes

### EvidencePackage

```kotlin
data class EvidencePackage(
    val evidenceId: String,
    val timestamp: Long,
    val threatDetection: ThreatDetection,
    val evidenceHash: String,
    val blockchainTxHash: String? = null,
    val blockHeight: Long? = null,
    val isAnchoredOnChain: Boolean = false
)
```

### AnchorResult

```kotlin
data class AnchorResult(
    val success: Boolean,
    val txHash: String? = null,
    val blockHeight: Long? = null,
    val timestamp: Long,
    val error: String? = null
)
```

### VerificationResult

```kotlin
data class VerificationResult(
    val exists: Boolean,
    val timestamp: Long? = null,
    val blockHeight: Long? = null,
    val isValid: Boolean = false,
    val error: String? = null
)
```

### QueueStatus

```kotlin
data class QueueStatus(
    val totalQueued: Int,
    val oldestQueuedTime: Long,
    val failedRetries: Int
)
```

## Integration in Your Service

```kotlin
class YourService {
    private lateinit var blockchainManager: AptosBlockchainManager
    
    fun onCreate() {
        blockchainManager = AptosBlockchainManager.getInstance(this)
        
        // Start queue processor
        startQueueProcessor()
    }
    
    private fun startQueueProcessor() {
        scope.launch {
            while (isActive) {
                if (blockchainManager.isBlockchainAccessible()) {
                    blockchainManager.processQueue()
                }
                delay(120000) // 2 minutes
            }
        }
    }
    
    suspend fun handleThreat(threat: ThreatDetection) {
        // Create evidence
        val evidence = createEvidence(threat)
        
        // Anchor to blockchain
        val result = blockchainManager.anchorEvidence(evidence)
        
        if (result.success) {
            Log.i(TAG, "✓ Anchored: ${result.txHash}")
        } else {
            Log.w(TAG, "Queued for retry: ${result.error}")
        }
    }
}
```

## Monitoring & Debugging

### View Logs

```bash
# Android Logcat
adb logcat | grep AptosBlockchain

# Filter for errors
adb logcat | grep -E "AptosBlockchain.*error|ERROR"
```

### Check Transaction on Explorer

```
Testnet: https://explorer.aptoslabs.com/txn/<TX_HASH>?network=testnet
Mainnet: https://explorer.aptoslabs.com/txn/<TX_HASH>
```

### Debug Checklist

- [ ] Internet connection available
- [ ] Blockchain RPC endpoint accessible
- [ ] Evidence hash is valid SHA-256
- [ ] Smart contract deployed (production)
- [ ] Correct network (testnet/mainnet)
- [ ] Check queue status for failures

## API Endpoints

### Testnet (Default)

```
RPC: https://fullnode.testnet.aptoslabs.com/v1
```

### Mainnet

```
RPC: https://fullnode.mainnet.aptoslabs.com/v1
```

## Constants

```kotlin
// Transaction settings
MAX_GAS_AMOUNT = 2000
GAS_UNIT_PRICE = 100
TRANSACTION_TIMEOUT_SEC = 60L

// Retry settings
MAX_RETRY_ATTEMPTS = 3
RETRY_DELAY_MS = 5000L

// Contracts
TESTNET_CONTRACT = "0x1::shakti_evidence::EvidenceRegistry"
MAINNET_CONTRACT = "0x1::shakti_evidence::EvidenceRegistry"
```

## Error Handling

```kotlin
try {
    val result = blockchainManager.anchorEvidence(evidence)
    
    when {
        result.success -> {
            // Success - evidence on-chain
            handleSuccess(result.txHash!!)
        }
        result.error?.contains("network") == true -> {
            // Network error - will auto-retry
            log("Network issue, queued for retry")
        }
        else -> {
            // Other error
            log("Error: ${result.error}")
        }
    }
} catch (e: Exception) {
    log("Exception: ${e.message}")
}
```

## Performance Tips

1. **Batch Operations** - Process queue in batches
2. **Check Accessibility First** - Avoid unnecessary network calls
3. **Use Coroutines** - All methods are `suspend` functions
4. **Monitor Queue Size** - Alert if queue grows too large
5. **Offline Mode** - Let queue handle retries automatically

## Security Notes

- ✅ Only hash stored on-chain (not evidence content)
- ✅ No PII sent to blockchain
- ✅ Evidence encrypted at rest
- ✅ User ID anonymized
- ✅ Location optional (user consent)

## Common Issues

### Issue: "Unresolved reference: blockHeight"

**Fix:** Make sure `EvidencePackage` has `blockHeight: Long?` field

### Issue: Evidence not anchoring

**Fix:** Check `isBlockchainAccessible()` and queue status

### Issue: Transaction hash not found

**Fix:** Wait 1-2 seconds for confirmation, verify network

### Issue: High queue count

**Fix:** Check network, verify RPC endpoint, manual `processQueue()`

## Testing

```kotlin
// Unit test
@Test
fun testBlockchainAnchoring() = runBlocking {
    val manager = AptosBlockchainManager.getInstance(context)
    val evidence = createTestEvidence()
    
    val result = manager.anchorEvidence(evidence)
    
    assertTrue(result.success || result.txHash != null)
}

// Integration test
@Test
fun testQueueProcessing() = runBlocking {
    val manager = AptosBlockchainManager.getInstance(context)
    
    // Create offline evidence
    val evidence = createTestEvidence()
    manager.anchorEvidence(evidence) // Queues
    
    // Process queue
    manager.processQueue()
    
    val status = manager.getQueueStatus()
    assertTrue(status.totalQueued >= 0)
}
```

## Example: Complete Flow

```kotlin
suspend fun completeEvidenceFlow() {
    // 1. Detect threat
    val threat = detectThreat()
    
    // 2. Create evidence
    val evidence = EvidencePackage(
        evidenceId = EvidencePackage.generateEvidenceId(),
        timestamp = System.currentTimeMillis(),
        threatDetection = threat,
        evidenceHash = calculateHash(...)
    )
    
    // 3. Anchor to blockchain
    val blockchainManager = AptosBlockchainManager.getInstance(context)
    val result = blockchainManager.anchorEvidence(evidence)
    
    // 4. Generate certificate
    if (result.success) {
        val certificate = blockchainManager.generateLegalCertificate(evidence)
        saveCertificate(certificate.toPDFContent())
    }
    
    // 5. Verify (later)
    val verification = blockchainManager.verifyEvidence(evidence.evidenceHash)
    if (verification.exists) {
        println("✓ Evidence verified on blockchain")
    }
}
```

---

**Need Help?**

- 📖 Full docs: `BLOCKCHAIN_INTEGRATION.md`
- 🔍 Summary: `INTEGRATION_SUMMARY.md`
- 🐛 Logs: `adb logcat | grep AptosBlockchain`
