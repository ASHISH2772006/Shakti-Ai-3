# Aptos Blockchain Integration - Shakti AI

## Overview

Shakti AI integrates with the **Aptos blockchain** to provide immutable, cryptographic proof of
evidence captured during threat detection. This ensures legal admissibility and tamper-proof
evidence that can be verified by law enforcement and legal authorities.

## Architecture

### Components

1. **AptosBlockchainManager** - Core blockchain interaction manager
    - Handles evidence hash anchoring to Aptos blockchain
    - Manages transaction submission and confirmation
    - Implements retry queue for offline/failed transactions
    - Supports both testnet and mainnet

2. **DigitalBodyguardService** - Main monitoring service
    - Detects threats using multi-sensor fusion
    - Creates evidence packages
    - Automatically anchors evidence to blockchain
    - Processes blockchain queue periodically

3. **EvidenceManager** - Evidence handling
    - Creates encrypted evidence packages
    - Generates SHA-256 hashes
    - Manages local storage

## How It Works

### 1. Threat Detection

When a threat is detected (scream, sudden motion, suspicious proximity):

```
Audio/Motion Sensors → ThreatDetection → EvidencePackage
```

### 2. Evidence Creation

```kotlin
EvidencePackage(
    evidenceId: "EVIDENCE_1234567890_5678",
    timestamp: 1234567890,
    threatDetection: ThreatDetection(...),
    evidenceHash: "abc123...", // SHA-256 hash
    location: LocationEvidence(...),
    audioRecordingPath: "/path/to/recording.enc",
    isEncrypted: true
)
```

### 3. Blockchain Anchoring

```
EvidencePackage → AptosBlockchainManager.anchorEvidence()
                → Submit transaction to Aptos
                → Wait for confirmation
                → Return AnchorResult(txHash, blockHeight)
```

### 4. Verification

Evidence can be verified on-chain:

```kotlin
val result = blockchainManager.verifyEvidence(evidenceHash)
// Returns: exists, timestamp, blockHeight, isValid
```

## Blockchain Features

### Immutable Timestamps

- Evidence hash is permanently recorded with blockchain timestamp
- Cannot be backdated or modified
- Provides cryptographic proof of existence at specific time

### Privacy-Preserving

- Only SHA-256 hash is stored on-chain (not actual evidence)
- No personal identifiable information (PII) on blockchain
- User ID is anonymized ("anonymous")
- Location coordinates are optional

### Low Transaction Costs

- Aptos offers low gas fees (~$0.0001 per transaction)
- Optimized for frequent evidence anchoring
- Fast finality (~1 second)

### Court Admissible

- Blockchain proof meets legal standards for evidence authentication
- Generates legal certificates with blockchain verification
- Includes transaction hash and block height for verification

## Queue Management

### Offline Support

When blockchain is not accessible:

1. Evidence is automatically queued
2. Retry mechanism attempts anchoring periodically
3. Max 3 retry attempts with exponential backoff
4. Queue status can be monitored

### Queue Processing

Background task runs every 2-5 minutes:

```kotlin
startBlockchainQueueProcessor()
    → blockchainManager.processQueue()
    → Retry failed/queued transactions
    → Log queue status
```

## API Endpoints

### Testnet (Default)

```
RPC: https://fullnode.testnet.aptoslabs.com/v1
Explorer: https://explorer.aptoslabs.com/?network=testnet
```

### Mainnet

```
RPC: https://fullnode.mainnet.aptoslabs.com/v1
Explorer: https://explorer.aptoslabs.com/
```

Switch to mainnet:

```kotlin
blockchainManager.setMainnet(true)
```

## Smart Contract

Evidence is anchored via Move function:

```move
anchor_evidence(
    evidence_hash: String,
    timestamp: u64,
    latitude: f64,
    longitude: f64,
    threat_type: String
)
```

Contract address:

- Testnet: `0x1::shakti_evidence::EvidenceRegistry`
- Mainnet: `0x1::shakti_evidence::EvidenceRegistry`

## Legal Certificate Generation

Generate court-admissible certificate:

```kotlin
val certificate = blockchainManager.generateLegalCertificate(evidence)
val pdfContent = certificate.toPDFContent()
```

Certificate includes:

- Evidence hash and timestamp
- Blockchain transaction hash
- Block height
- Location and threat details
- Verification instructions
- Certificate hash for validation

## Integration in DigitalBodyguardService

### Automatic Anchoring

```kotlin
private suspend fun handleThreatEscalation(threat: ThreatDetection) {
    // 1. Create evidence package
    val evidence = evidenceManager.createEvidencePackage(threat, location, sensorLogs)
    
    // 2. Anchor to blockchain
    anchorEvidenceToBlockchain(evidence)
    
    // Evidence is now immutably recorded
}
```

### Blockchain Status Logging

```
✓ Evidence successfully anchored to blockchain
  Transaction Hash: 0xabc123...
  Block Height: 1234567
  Evidence ID: EVIDENCE_1234567890_5678
```

### Queue Status

```
Blockchain queue: 3 items, 0 failed
```

## Error Handling

### Network Errors

- Automatically queued for retry
- Logged with error details
- Max 3 retry attempts

### Transaction Failures

- Evidence remains in queue
- Can be manually retried
- Error message logged

### Offline Operation

- Full functionality without blockchain
- Evidence queued automatically
- Anchored when online

## Security Considerations

### Encryption

- Evidence files encrypted at rest (AES-256-GCM)
- Only hash sent to blockchain
- Master key stored in Android Keystore

### Privacy

- No PII on blockchain
- User ID anonymized
- Location optional (user consent required)

### Hash Integrity

- SHA-256 ensures evidence hasn't been tampered
- Any modification changes hash
- Mismatch detected during verification

## Testing

### Check Blockchain Accessibility

```kotlin
val isAccessible = blockchainManager.isBlockchainAccessible()
```

### Manual Queue Processing

```kotlin
blockchainManager.processQueue()
```

### Get Queue Status

```kotlin
val status = blockchainManager.getQueueStatus()
Log.d("Queue", "Total: ${status.totalQueued}, Failed: ${status.failedRetries}")
```

### Verify Evidence

```kotlin
val result = blockchainManager.verifyEvidence(evidenceHash)
if (result.exists) {
    Log.d("Verified", "Block: ${result.blockHeight}, Time: ${result.timestamp}")
}
```

## Performance Optimization

### Micro-Burst Anchoring

- Only hash sent (32 bytes)
- Fast transaction (~1 second)
- Minimal battery impact

### Background Processing

- Queue processed in background
- Low-priority thread
- Sleep during offline periods

### Network Efficiency

- OkHttp with connection pooling
- 30-second timeouts
- Automatic retry with backoff

## Future Enhancements

1. **Batch Anchoring** - Merkle tree of multiple evidence hashes
2. **Cross-Chain** - Multi-blockchain support (Ethereum, Polygon)
3. **Zero-Knowledge Proofs** - Privacy-enhanced verification
4. **IPFS Integration** - Decentralized evidence storage
5. **NFT Evidence** - Mint evidence as NFTs for legal cases

## Troubleshooting

### Evidence Not Anchoring

1. Check internet connection
2. Verify blockchain accessibility: `isBlockchainAccessible()`
3. Check queue status: `getQueueStatus()`
4. Review logs for errors

### High Queue Count

1. Check network connectivity
2. Verify RPC endpoint is accessible
3. Consider switching networks (testnet/mainnet)
4. Manual retry: `processQueue()`

### Transaction Hash Not Found

1. Wait for confirmation (1-2 seconds)
2. Check correct network (testnet vs mainnet)
3. Verify transaction on explorer
4. Check RPC endpoint status

## Links

- [Aptos Blockchain](https://aptoslabs.com)
- [Aptos Explorer](https://explorer.aptoslabs.com)
- [Move Language](https://move-language.github.io/move/)
- [Aptos Developer Docs](https://aptos.dev)

## Support

For blockchain integration issues:

- Check logs: `adb logcat | grep AptosBlockchain`
- Review queue status
- Contact: blockchain@shaktiai.org

---

**Note:** Current implementation uses testnet by default. For production deployment, switch to
mainnet and ensure proper smart contract deployment.
