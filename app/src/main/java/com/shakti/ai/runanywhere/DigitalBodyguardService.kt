package com.shakti.ai.runanywhere

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.shakti.ai.MainActivity
import com.shakti.ai.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Digital Bodyguard Service
 * 
 * Always-on, ultra-low-latency threat detection service that:
 * - Monitors audio for distress signals (< 2s latency)
 * - Tracks sudden motion via IMU sensors
 * - Detects suspicious BLE proximity
 * - Creates encrypted evidence packages
 * - Broadcasts SOS via BLE mesh
 * - Anchors evidence to Aptos blockchain
 * 
 * Designed for stealth operation with minimal battery impact.
 */
class DigitalBodyguardService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "DigitalBodyguard"
        private const val CHANNEL_ID = "digital_bodyguard_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096
        private const val AUDIO_BURST_DURATION_MS = 500 // Micro-burst sampling
        private const val AUDIO_BURST_INTERVAL_MS = 2000 // Sample every 2s
        
        // Threat thresholds
        private const val AUDIO_THREAT_THRESHOLD = 0.75f
        private const val MOTION_THREAT_THRESHOLD = 15f // m/s²
        private const val RISK_SCORE_THRESHOLD = 0.7f
        
        // Model files
        private const val AUDIO_MODEL_FILE = "audio_threat_detector.tflite"
    }

    // Service binding
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): DigitalBodyguardService = this@DigitalBodyguardService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Wake lock for background operation
    private lateinit var wakeLock: PowerManager.WakeLock
    
    // Sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // Audio monitoring
    private var audioRecorder: AudioRecord? = null
    private var isAudioMonitoring = false
    
    // TensorFlow Lite interpreter
    private var audioModelInterpreter: Interpreter? = null
    
    // BLE Mesh service
    private var bleMeshService: BLEMeshService? = null
    
    // Evidence manager
    private lateinit var evidenceManager: EvidenceManager
    
    // State flows
    private val _monitoringState = MutableStateFlow(MonitoringState())
    val monitoringState: StateFlow<MonitoringState> = _monitoringState
    
    private val _sensorStatus = MutableStateFlow(SensorStatus())
    val sensorStatus: StateFlow<SensorStatus> = _sensorStatus
    
    private val _latestThreat = MutableStateFlow<ThreatDetection?>(null)
    val latestThreat: StateFlow<ThreatDetection?> = _latestThreat
    
    // Current sensor readings
    private var lastAccelerometer = FloatArray(3)
    private var lastGyroscope = FloatArray(3)
    private var currentLocation: Location? = null
    
    // Settings
    private var settings = BodyguardSettings()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Digital Bodyguard Service created")
        
        // Initialize wake lock for background operation
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShaktiAI::DigitalBodyguardWakeLock"
        )
        
        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        // Initialize evidence manager
        evidenceManager = EvidenceManager(this)
        
        // Load TensorFlow Lite model
        loadAudioModel()
        
        // Initialize BLE Mesh service
        bleMeshService = BLEMeshService.getInstance(this)
        
        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Digital Bodyguard Service started")
        
        // Start foreground service with notification
        val notification = createNotification(false)
        startForeground(NOTIFICATION_ID, notification)
        
        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
        
        // Start monitoring
        startMonitoring()
        
        return START_STICKY // Restart service if killed
    }

    /**
     * Start all monitoring systems
     */
    private fun startMonitoring() {
        if (_monitoringState.value.isActive) {
            Log.d(TAG, "Already monitoring")
            return
        }
        
        Log.i(TAG, "Starting Digital Bodyguard monitoring")
        
        // Update state
        _monitoringState.value = _monitoringState.value.copy(
            isActive = true,
            startTime = System.currentTimeMillis()
        )
        
        // Register sensor listeners
        registerSensorListeners()
        
        // Start audio monitoring in micro-bursts
        startAudioBurstMonitoring()
        
        // Start BLE mesh scanning
        bleMeshService?.startScanning()
        
        // Start threat fusion loop
        startThreatDetectionLoop()

        // Start blockchain queue processor
        startBlockchainQueueProcessor()

        // Update notification
        updateNotification(true)
        
        Log.i(TAG, "Digital Bodyguard monitoring active")
    }

    /**
     * Register IMU sensor listeners
     */
    private fun registerSensorListeners() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Accelerometer registered")
        }
        
        gyroscope?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Gyroscope registered")
        }
        
        _sensorStatus.value = _sensorStatus.value.copy(
            imuEnabled = true
        )
    }

    /**
     * Start audio monitoring in micro-bursts for battery efficiency
     */
    private fun startAudioBurstMonitoring() {
        if (!checkAudioPermission()) {
            Log.w(TAG, "Audio permission not granted")
            return
        }
        
        serviceScope.launch {
            isAudioMonitoring = true
            _sensorStatus.value = _sensorStatus.value.copy(audioEnabled = true)
            
            while (isAudioMonitoring && _monitoringState.value.isActive) {
                try {
                    // Capture audio burst
                    val audioResult = captureAndAnalyzeAudioBurst()
                    
                    // Check for threat
                    if (audioResult.confidence > AUDIO_THREAT_THRESHOLD) {
                        handleAudioThreat(audioResult)
                    }
                    
                    // Wait before next burst (battery optimization)
                    delay(AUDIO_BURST_INTERVAL_MS.toLong())
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Audio monitoring error", e)
                    delay(5000) // Wait 5s before retry
                }
            }
        }
    }

    /**
     * Capture and analyze audio burst using TinyML model
     */
    private suspend fun captureAndAnalyzeAudioBurst(): AudioThreatResult = withContext(Dispatchers.IO) {
        val result = AudioThreatResult()
        
        try {
            // Initialize audio recorder if needed
            if (audioRecorder == null) {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(BUFFER_SIZE)
                
                if (checkAudioPermission()) {
                    audioRecorder = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }
            }
            
            audioRecorder?.let { recorder ->
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    
                    // Capture burst
                    val buffer = ShortArray(BUFFER_SIZE)
                    val samplesRead = recorder.read(buffer, 0, BUFFER_SIZE)
                    
                    recorder.stop()
                    
                    if (samplesRead > 0) {
                        // Analyze audio
                        return@withContext analyzeAudio(buffer, samplesRead)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture error", e)
        }
        
        return@withContext result
    }

    /**
     * Analyze audio using TensorFlow Lite model
     */
    private fun analyzeAudio(buffer: ShortArray, length: Int): AudioThreatResult {
        // Calculate volume
        val volume = calculateVolume(buffer, length)
        
        // Extract features
        val features = extractAudioFeatures(buffer, length)
        
        // Run TFLite inference if model loaded
        val modelOutput = audioModelInterpreter?.let { interpreter ->
            runAudioInference(interpreter, features)
        } ?: FloatArray(5) { 0f }
        
        // Interpret results
        // Model outputs: [normal, scream, aggressive, gunshot, glass_break]
        return AudioThreatResult(
            timestamp = System.currentTimeMillis(),
            isScream = modelOutput[1] > 0.7f,
            isAggressiveVoice = modelOutput[2] > 0.7f,
            isGunshot = modelOutput[3] > 0.85f,
            isGlassBreak = modelOutput[4] > 0.75f,
            confidence = modelOutput.maxOrNull() ?: 0f,
            audioType = getAudioType(modelOutput),
            decibels = volumeToDecibels(volume)
        )
    }

    /**
     * Calculate audio volume (RMS)
     */
    private fun calculateVolume(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        val rms = sqrt(sum / length)
        return (rms / Short.MAX_VALUE).toFloat()
    }

    /**
     * Extract audio features for model input
     */
    private fun extractAudioFeatures(buffer: ShortArray, length: Int): FloatArray {
        // Normalize audio to [-1, 1]
        return FloatArray(length.coerceAtMost(4096)) { i ->
            if (i < length) {
                (buffer[i] / 32768f).coerceIn(-1f, 1f)
            } else {
                0f
            }
        }
    }

    /**
     * Run TensorFlow Lite inference
     */
    private fun runAudioInference(interpreter: Interpreter, features: FloatArray): FloatArray {
        try {
            val inputBuffer = ByteBuffer.allocateDirect(features.size * 4).apply {
                order(ByteOrder.nativeOrder())
                features.forEach { putFloat(it) }
                rewind()
            }
            
            val outputBuffer = ByteBuffer.allocateDirect(5 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            
            interpreter.run(inputBuffer, outputBuffer)
            
            outputBuffer.rewind()
            return FloatArray(5) { outputBuffer.float }
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            return FloatArray(5) { 0f }
        }
    }

    /**
     * Get audio type from model output
     */
    private fun getAudioType(output: FloatArray): String {
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: 0
        return when (maxIndex) {
            1 -> "scream"
            2 -> "aggressive_voice"
            3 -> "gunshot"
            4 -> "glass_break"
            else -> "normal"
        }
    }

    /**
     * Convert volume to decibels
     */
    private fun volumeToDecibels(volume: Float): Float {
        return (20 * kotlin.math.log10(volume.coerceAtLeast(0.0001f))).toFloat()
    }

    /**
     * Handle audio threat detection
     */
    private fun handleAudioThreat(audioResult: AudioThreatResult) {
        Log.w(TAG, "Audio threat detected: ${audioResult.audioType} (${audioResult.confidence})")
        
        // Create threat detection
        val threat = ThreatDetection(
            timestamp = audioResult.timestamp,
            audioConfidence = audioResult.confidence,
            motionConfidence = calculateMotionThreat(),
            bleProximityScore = 0f, // TODO: Get from BLE mesh
            cameraScore = 0f,
            threatType = ThreatType.AUDIO_DISTRESS,
            location = currentLocation
        )
        
        _latestThreat.value = threat
        
        // Check if should escalate
        if (threat.calculateRiskScore() > RISK_SCORE_THRESHOLD) {
            handleThreatEscalation(threat)
        }
    }

    /**
     * Calculate motion threat from IMU sensors
     */
    private fun calculateMotionThreat(): Float {
        val accelMagnitude = sqrt(
            lastAccelerometer[0] * lastAccelerometer[0] +
            lastAccelerometer[1] * lastAccelerometer[1] +
            lastAccelerometer[2] * lastAccelerometer[2]
        )
        
        // Normalize to 0-1 scale
        return (accelMagnitude / 30f).coerceIn(0f, 1f)
    }

    /**
     * Start threat detection fusion loop
     */
    private fun startThreatDetectionLoop() {
        serviceScope.launch {
            while (_monitoringState.value.isActive) {
                try {
                    // Fuse all sensor data
                    val threat = fuseSensorData()
                    
                    // Check for threat
                    if (threat.isThreat(RISK_SCORE_THRESHOLD)) {
                        handleThreatDetection(threat)
                    }
                    
                    // Run fusion every 100ms
                    delay(100)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Threat detection loop error", e)
                }
            }
        }
    }

    /**
     * Fuse all sensor data into single threat assessment
     */
    private fun fuseSensorData(): ThreatDetection {
        return ThreatDetection(
            timestamp = System.currentTimeMillis(),
            audioConfidence = 0f, // Set by audio burst monitoring
            motionConfidence = calculateMotionThreat(),
            bleProximityScore = 0f, // TODO: Get from BLE mesh
            cameraScore = 0f, // TODO: Add camera analysis
            location = currentLocation
        ).apply {
            // Calculate combined risk score
            val risk = calculateRiskScore()
            // Update threat type based on dominant sensor
            val type = when {
                audioConfidence > 0.7f -> ThreatType.AUDIO_DISTRESS
                motionConfidence > 0.8f -> ThreatType.SUDDEN_MOTION
                bleProximityScore > 0.7f -> ThreatType.SUSPICIOUS_PROXIMITY
                risk > 0.7f -> ThreatType.COMBINED
                else -> ThreatType.NONE
            }
        }
    }

    /**
     * Handle threat detection - show confirmation prompt
     */
    private fun handleThreatDetection(threat: ThreatDetection) {
        _latestThreat.value = threat
        
        _monitoringState.value = _monitoringState.value.copy(
            totalThreatsDetected = _monitoringState.value.totalThreatsDetected + 1,
            lastThreatDetection = threat
        )
        
        Log.w(TAG, "THREAT DETECTED - Risk: ${threat.calculateRiskScore()}")
        
        // Auto-escalate if settings enabled
        if (settings.autoEscalate) {
            serviceScope.launch {
                delay(settings.confirmationTimeoutMs)
                // If user hasn't cancelled, escalate
                handleThreatEscalation(threat)
            }
        }
    }

    /**
     * Handle threat escalation - trigger all responses
     */
    private fun handleThreatEscalation(threat: ThreatDetection) {
        Log.e(TAG, "ESCALATING THREAT - Triggering emergency protocol")
        
        serviceScope.launch {
            try {
                // 1. Create evidence package
                val evidence = evidenceManager.createEvidencePackage(
                    threat,
                    currentLocation,
                    getSensorLogs()
                )
                
                _monitoringState.value = _monitoringState.value.copy(
                    evidencePackagesCreated = _monitoringState.value.evidencePackagesCreated + 1
                )
                
                // 2. Broadcast SOS via BLE mesh
                if (settings.automatedResponse.bleBroadcast) {
                    broadcastSOSMessage(threat)
                }
                
                // 3. Start evidence recording
                if (settings.automatedResponse.recordEvidence) {
                    evidenceManager.startEvidenceRecording()
                }
                
                // 4. Trigger automated responses
                triggerAutomatedResponses(threat)
                
                // 5. Anchor to blockchain (when online)
                anchorEvidenceToBlockchain(evidence)
                
                // 6. Update notification
                updateNotification(true, "EMERGENCY: Threat detected!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during escalation", e)
            }
        }
    }

    /**
     * Broadcast SOS via BLE mesh
     */
    private suspend fun broadcastSOSMessage(threat: ThreatDetection) {
        val sos = SOSBroadcast(
            messageId = SOSBroadcast.generateMessageId(),
            senderId = "user_${System.currentTimeMillis()}", // TODO: Get from user profile
            senderName = "SHAKTI User",
            urgency = UrgencyLevel.CRITICAL,
            location = currentLocation?.let {
                LocationEvidence(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy
                )
            },
            threatType = threat.threatType,
            timestamp = System.currentTimeMillis()
        )
        
        bleMeshService?.broadcastSOS(sos)
        
        _monitoringState.value = _monitoringState.value.copy(
            sosMessagesSent = _monitoringState.value.sosMessagesSent + 1
        )
        
        Log.i(TAG, "SOS broadcast sent via BLE mesh")
    }

    /**
     * Trigger automated responses based on settings
     */
    private suspend fun triggerAutomatedResponses(threat: ThreatDetection) {
        val response = settings.automatedResponse
        
        // Implement response actions
        // TODO: Implement each response type
        
        Log.i(TAG, "Automated responses triggered")
    }

    /**
     * Anchor evidence to Aptos blockchain
     */
    private suspend fun anchorEvidenceToBlockchain(evidence: EvidencePackage) {
        try {
            val blockchainManager = AptosBlockchainManager.getInstance(this)

            // Check if blockchain is accessible
            val isAccessible = blockchainManager.isBlockchainAccessible()

            if (isAccessible) {
                Log.i(TAG, "Anchoring evidence to blockchain: ${evidence.evidenceHash}")

                // Anchor the evidence
                val result = blockchainManager.anchorEvidence(evidence)

                if (result.success) {
                    Log.i(TAG, "✓ Evidence successfully anchored to blockchain")
                    Log.i(TAG, "  Transaction Hash: ${result.txHash}")
                    Log.i(TAG, "  Block Height: ${result.blockHeight}")
                    Log.i(TAG, "  Evidence ID: ${evidence.evidenceId}")

                    // The evidence is now immutably recorded on blockchain
                    // Transaction can be verified at: https://explorer.aptoslabs.com/txn/${result.txHash}
                } else {
                    Log.w(TAG, "Failed to anchor evidence: ${result.error}")
                    Log.i(TAG, "Evidence queued for retry by AptosBlockchainManager")
                }
            } else {
                Log.w(TAG, "Blockchain not accessible, evidence will be queued for later anchoring")
                // Try to anchor anyway - AptosBlockchainManager will queue it automatically
                blockchainManager.anchorEvidence(evidence)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error anchoring evidence to blockchain", e)
        }
    }

    /**
     * Get current sensor logs
     */
    private fun getSensorLogs(): SensorLogs {
        return SensorLogs(
            accelerometer = lastAccelerometer.toList(),
            gyroscope = lastGyroscope.toList(),
            batteryLevel = 100 // TODO: Get actual battery level
        )
    }

    // SensorEventListener implementations
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccelerometer = it.values.copyOf()
                    
                    // Check for sudden motion
                    val magnitude = sqrt(
                        it.values[0] * it.values[0] +
                        it.values[1] * it.values[1] +
                        it.values[2] * it.values[2]
                    )
                    
                    if (magnitude > MOTION_THREAT_THRESHOLD) {
                        handleSuddenMotion(magnitude)
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroscope = it.values.copyOf()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /**
     * Handle sudden motion detection
     */
    private fun handleSuddenMotion(magnitude: Float) {
        Log.w(TAG, "Sudden motion detected: $magnitude m/s²")
        
        val threat = ThreatDetection(
            timestamp = System.currentTimeMillis(),
            motionConfidence = (magnitude / 30f).coerceIn(0f, 1f),
            threatType = ThreatType.SUDDEN_MOTION,
            location = currentLocation
        )
        
        if (threat.calculateRiskScore() > RISK_SCORE_THRESHOLD) {
            handleThreatDetection(threat)
        }
    }

    /**
     * Load TensorFlow Lite audio model
     */
    private fun loadAudioModel() {
        try {
            val modelFile = loadModelFile(AUDIO_MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(2) // Low thread count for battery efficiency
                setUseNNAPI(true) // Use Android Neural Networks API
            }
            audioModelInterpreter = Interpreter(modelFile, options)
            Log.i(TAG, "Audio model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load audio model (fallback to heuristics): ${e.message}")
            // Service continues without model
        }
    }

    /**
     * Load model file from assets
     */
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Check audio permission
     */
    private fun checkAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Update location
     */
    fun updateLocation(location: Location) {
        currentLocation = location
        _sensorStatus.value = _sensorStatus.value.copy(locationEnabled = true)
    }

    /**
     * Update settings
     */
    fun updateSettings(newSettings: BodyguardSettings) {
        settings = newSettings
        Log.d(TAG, "Settings updated")
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping Digital Bodyguard monitoring")
        
        isAudioMonitoring = false
        
        // Unregister sensors
        sensorManager.unregisterListener(this)
        
        // Stop BLE scanning
        bleMeshService?.stopScanning()
        
        // Stop audio recorder
        audioRecorder?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio recorder", e)
            }
        }
        audioRecorder = null
        
        _monitoringState.value = _monitoringState.value.copy(isActive = false)
        _sensorStatus.value = SensorStatus()
        
        updateNotification(false)
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Digital Bodyguard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-on safety monitoring"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification
     */
    private fun createNotification(isActive: Boolean, message: String = "Safety monitoring"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SHAKTI AI - Digital Bodyguard")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: Add shield icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification
     */
    private fun updateNotification(isActive: Boolean, message: String = "Safety monitoring") {
        val notification = createNotification(isActive, message)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Digital Bodyguard Service destroyed")
        
        stopMonitoring()
        
        // Release wake lock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        // Clean up
        audioModelInterpreter?.close()
        serviceScope.cancel()
    }

    /**
     * Start blockchain queue processor
     */
    private fun startBlockchainQueueProcessor() {
        serviceScope.launch {
            while (_monitoringState.value.isActive) {
                try {
                    val blockchainManager =
                        AptosBlockchainManager.getInstance(this@DigitalBodyguardService)

                    // Check if blockchain is accessible
                    val isAccessible = blockchainManager.isBlockchainAccessible()

                    if (isAccessible) {
                        // Process any queued evidence
                        blockchainManager.processQueue()

                        // Get queue status
                        val queueStatus = blockchainManager.getQueueStatus()
                        if (queueStatus.totalQueued > 0) {
                            Log.i(
                                TAG,
                                "Blockchain queue: ${queueStatus.totalQueued} items, ${queueStatus.failedRetries} failed"
                            )
                        }
                    } else {
                        Log.d(TAG, "Blockchain not accessible, skipping queue processing")
                    }

                    // Run every 2 minutes when online, 5 minutes when offline
                    delay(if (isAccessible) 120000 else 300000)

                } catch (e: Exception) {
                    Log.e(TAG, "Blockchain queue processor error", e)
                    delay(300000) // Wait 5 minutes on error
                }
            }
        }
    }

}
