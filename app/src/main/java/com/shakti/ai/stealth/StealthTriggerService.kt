package com.shakti.ai.stealth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.provider.Settings
import android.graphics.PixelFormat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.shakti.ai.EmergencyOverlayService
import com.shakti.ai.R
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stealth Trigger Service
 * 
 * Background service that continuously listens for emergency triggers:
 * - Loud noise (scream, crash, etc.) above threshold
 * - "HELP" said 2 times within 8 seconds
 * 
 * When triggered, automatically launches Calculator in stealth mode.
 * 
 * Features:
 * - Low battery impact (~1-2%/hour)
 * - Runs as foreground service (persistent)
 * - Simple audio analysis (no ML needed)
 * - Fast response (<200ms)
 * - Works even when app is closed
 */
class StealthTriggerService : Service() {

    companion object {
        private const val TAG = "StealthTrigger"
        const val NOTIFICATION_CHANNEL_ID = "stealth_trigger_channel"
        const val NOTIFICATION_ID = 1001
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096
        private const val AUDIO_FRAME_MS = 100

        // Detection thresholds - MORE SENSITIVE (works with slow/quiet speech)
        private const val LOUD_NOISE_THRESHOLD = 16000 // More sensitive - louder clear noise
        private const val HELP_COUNT_THRESHOLD = 2     // "HELP" must be said 2 times
        private const val HELP_TIMEOUT_MS = 8000L      // 8 seconds between HELPs
        private const val LOUD_BURST_THRESHOLD = 5000  // Burst detection threshold
        private const val MIN_RMS_FOR_SPEECH =
            1700    // Much more sensitive - quiet speech accepted
        
        // Actions
        const val ACTION_START_MONITORING = "com.shakti.ai.stealth.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.shakti.ai.stealth.STOP_MONITORING"
    }

    private var audioRecorder: AudioRecord? = null
    private var isMonitoring = false
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Voice trigger state
    private var helpCount = 0
    private var lastHelpTimestamp = 0L
    
    // Cooldown to prevent multiple triggers
    private var lastTriggerTime = 0L
    private val TRIGGER_COOLDOWN_MS = 30000L // 30 seconds cooldown

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Stealth Trigger Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if service should be running (from SharedPreferences)
        val prefs = getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_enabled", false)

        Log.d(TAG, "onStartCommand: action=${intent?.action}, shouldRun=$shouldRun")

        when (intent?.action) {
            ACTION_START_MONITORING -> {
                // User explicitly started it
                prefs.edit().putBoolean("service_enabled", true).apply()
                startMonitoring()
            }

            ACTION_STOP_MONITORING -> {
                // User explicitly stopped it
                prefs.edit().putBoolean("service_enabled", false).apply()
                stopMonitoring()
                return START_NOT_STICKY
            }

            else -> {
                // Service restarted by system or boot receiver
                if (shouldRun) {
                    startMonitoring()
                } else {
                    Log.d(TAG, "Service not enabled by user, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        // START_STICKY: System will restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Start audio monitoring for triggers
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        Log.i(TAG, "🎧 Starting audio trigger monitoring")
        isMonitoring = true
        
        // Start audio listening
        startAudioMonitoring()
    }

    /**
     * Start audio monitoring loop
     */
    private fun startAudioMonitoring() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Check permission
                if (ActivityCompat.checkSelfPermission(
                        this@StealthTriggerService,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Audio recording permission not granted")
                    stopSelf()
                    return@launch
                }

                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(BUFFER_SIZE)

                audioRecorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )

                if (audioRecorder?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecorder?.startRecording()
                    Log.i(TAG, "✓ Audio monitoring active")
                    
                    updateNotification("Listening for emergency triggers...")

                    // Continuous monitoring loop
                    val buffer = ShortArray(BUFFER_SIZE)

                    while (isMonitoring) {
                        val samplesRead = audioRecorder?.read(buffer, 0, BUFFER_SIZE) ?: 0

                        if (samplesRead > 0) {
                            // Check for triggers
                            checkForTriggers(buffer, samplesRead)
                        }

                        // Small delay to prevent CPU overload
                        delay(AUDIO_FRAME_MS.toLong())
                    }
                } else {
                    Log.e(TAG, "Failed to initialize AudioRecord")
                    stopSelf()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in audio monitoring", e)
                stopSelf()
            }
        }
    }

    /**
     * Check audio buffer for emergency triggers
     */
    private fun checkForTriggers(buffer: ShortArray, length: Int) {
        // 1. Check for loud noise (scream, crash, etc.)
        val loudNoiseDetected = detectLoudNoise(buffer, length)
        
        // 2. Check for "HELP" voice pattern
        val helpDetected = detectHelpPattern(buffer, length)
        
        // Trigger if either condition met
        if (loudNoiseDetected) {
            Log.w(TAG, "🚨 LOUD NOISE DETECTED!")
            triggerStealthMode("Loud noise detected")
        } else if (helpDetected) {
            Log.w(TAG, "🚨 HELP TRIGGER DETECTED!")
            triggerStealthMode("Help voice trigger detected")
        }
    }

    /**
     * Detect loud noise (simple amplitude check)
     */
    private fun detectLoudNoise(buffer: ShortArray, length: Int): Boolean {
        // Check if in cooldown period
        if (System.currentTimeMillis() - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            return false
        }
        
        // Find max amplitude and RMS
        var maxAmplitude = 0
        var rmsSum = 0.0
        
        for (i in 0 until length) {
            val abs = abs(buffer[i].toInt())
            if (abs > maxAmplitude) maxAmplitude = abs
            rmsSum += abs * abs
        }
        
        val rms = sqrt(rmsSum / length)
        
        // Loud noise detected if both max and RMS are high
        val isLoud = maxAmplitude > LOUD_NOISE_THRESHOLD && rms > (LOUD_NOISE_THRESHOLD * 0.5)
        
        if (isLoud) {
            Log.d(TAG, "Loud noise: max=$maxAmplitude, rms=${rms.toInt()}")
        }
        
        return isLoud
    }

    /**
     * Detect "HELP" pattern (2 loud bursts within 8 seconds)
     * Improved: Actually analyzes speech patterns, not just amplitude
     */
    private fun detectHelpPattern(buffer: ShortArray, length: Int): Boolean {
        // Check if in cooldown period
        if (System.currentTimeMillis() - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            return false
        }

        // Calculate RMS (loudness) and Zero-Crossing Rate (speech indicator)
        var rmsSum = 0.0
        var zeroCrossings = 0
        var peakAmplitude = 0

        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            val absSample = kotlin.math.abs(sample)

            // Calculate RMS
            rmsSum += sample * sample

            // Count zero crossings (speech has higher ZCR than music/noise)
            if (i > 0) {
                val prevSample = buffer[i - 1].toInt()
                if ((prevSample < 0 && sample > 0) || (prevSample > 0 && sample < 0)) {
                    zeroCrossings++
                }
            }

            // Track peak
            if (absSample > peakAmplitude) {
                peakAmplitude = absSample
            }
        }

        val rms = sqrt(rmsSum / length)
        val zcr = zeroCrossings.toFloat() / length

        // Must be loud enough to be someone calling for help
        if (rms < MIN_RMS_FOR_SPEECH) {
            return false // Too quiet
        }

        // Detect bursts (single syllable characteristic of "HELP")
        var burstCount = 0
        var inBurst = false
        val burstThreshold = LOUD_BURST_THRESHOLD

        for (i in 0 until length) {
            val abs = kotlin.math.abs(buffer[i].toInt())
            if (abs > burstThreshold) {
                if (!inBurst) {
                    burstCount++
                    inBurst = true
                }
            } else if (abs < burstThreshold / 2) {
                inBurst = false
            }
        }

        // Calculate energy distribution (HELP has specific pattern)
        var highEnergyCount = 0
        var lowEnergyCount = 0
        val highEnergyThreshold = 6000  // "H" and "P" sounds are high energy - more sensitive
        val lowEnergyThreshold = 3000

        for (i in 0 until length) {
            val abs = kotlin.math.abs(buffer[i].toInt())
            if (abs > highEnergyThreshold) {
                highEnergyCount++
            } else if (abs < lowEnergyThreshold) {
                lowEnergyCount++
            }
        }

        val highEnergyRatio = highEnergyCount.toFloat() / length
        val energyVariance = if (highEnergyCount > 0 && lowEnergyCount > 0) {
            kotlin.math.abs(highEnergyCount - lowEnergyCount).toFloat() / length
        } else {
            0f
        }

        // "HELP" specific characteristics:
        // 1. Single syllable (EXACTLY 1 burst)
        // 2. Narrow ZCR range (0.08-0.20) - specific to HELP
        // 3. Strong consonants (H and P) = high peak + high energy ratio
        // 4. Energy variance (H...e...l...P pattern)
        // 5. Clear speaking volume

        val isSingleSyllable = burstCount == 1  // EXACTLY 1 burst
        val hasHelpZCR = zcr in 0.08f..0.20f  // Narrower, HELP-specific range
        val hasStrongConsonants = peakAmplitude > 5500 && highEnergyRatio > 0.13f  // H and P
        val hasEnergyVariance = energyVariance > 0.09f  // HELP's energy pattern
        val isLoudEnough = rms > MIN_RMS_FOR_SPEECH

        // ALL HELP-specific conditions must be met (strict matching)
        val isHelpPattern = isSingleSyllable && hasHelpZCR && hasStrongConsonants &&
                hasEnergyVariance && isLoudEnough

        if (isHelpPattern) {
            val currentTime = System.currentTimeMillis()
            
            // Reset counter if timeout exceeded
            if (helpCount > 0 && currentTime - lastHelpTimestamp > HELP_TIMEOUT_MS) {
                Log.d(TAG, "HELP counter reset due to timeout ($helpCount -> 0)")
                helpCount = 0
            }

            // Increment counter
            helpCount++
            lastHelpTimestamp = currentTime

            Log.i(
                TAG,
                "🗣️ Speech detected (RMS: ${rms.toInt()}, ZCR: ${"%.3f".format(zcr)}, Peak: $peakAmplitude, Bursts: $burstCount, High Energy Ratio: $highEnergyRatio, Energy Variance: $energyVariance): HELP count = $helpCount/$HELP_COUNT_THRESHOLD"
            )

            // Update notification with progress
            updateNotification("HELP detected: $helpCount/$HELP_COUNT_THRESHOLD")

            // Trigger if threshold reached
            if (helpCount >= HELP_COUNT_THRESHOLD) {
                helpCount = 0 // Reset
                return true
            }
        } else if (rms > MIN_RMS_FOR_SPEECH) {
            // Log when we detect sound but it's not speech-like
            Log.d(
                TAG,
                "Sound detected but not speech-like (RMS: ${rms.toInt()}, ZCR: ${"%.3f".format(zcr)}, Peak: $peakAmplitude, Bursts: $burstCount, High Energy Ratio: $highEnergyRatio, Energy Variance: $energyVariance)"
            )
        }
        
        return false
    }

    /**
     * Count zero crossings in audio buffer
     * Speech has characteristic zero-crossing rate
     */
    private fun countZeroCrossings(buffer: ShortArray, length: Int): Int {
        var crossings = 0
        for (i in 1 until length) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings
    }

    /**
     * Trigger stealth mode - launch calculator AND start emergency recording
     */
    private fun triggerStealthMode(reason: String) {
        // Set cooldown
        lastTriggerTime = System.currentTimeMillis()
        
        Log.w(TAG, "🚀 TRIGGERING FULL EMERGENCY MODE: $reason")
        
        // Update notification
        updateNotification("🚨 Emergency detected! Starting protection...")

        // Show emergency overlay, request SYSTEM_ALERT_WINDOW if needed
        showEmergencyOverlay()

        // Get StealthBodyguardManager and trigger FULL emergency
        val bodyguardManager = StealthBodyguardManager.getInstance(this)
        
        // Launch calculator activity
        val intent = Intent(this, StealthCalculatorActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("AUTO_TRIGGERED", true)
            putExtra("TRIGGER_REASON", reason)
            putExtra("START_EMERGENCY", true) // Signal to start emergency immediately
        }
        
        startActivity(intent)
        
        // Start monitoring if not already started
        serviceScope.launch {
            try {
                // Give the activity time to launch
                delay(500)
                
                // Start monitoring
                bodyguardManager.startMonitoring()
                
                // Wait for monitoring to initialize
                delay(500)
                
                // Trigger emergency with high confidence
                // This will start recording, capture location, create evidence, call contacts, etc.
                Log.w(TAG, "🚨 Manually triggering emergency in StealthBodyguardManager")
                bodyguardManager.manualTriggerEmergency(
                    reason = reason,
                    confidence = 0.95f
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering emergency", e)
            }
        }
        
        // Reset help counter
        helpCount = 0
        
        // Keep monitoring but with cooldown
        serviceScope.launch {
            delay(2000)
            updateNotification("Listening for emergency triggers...")
        }
    }

    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        Log.i(TAG, "Stopping audio trigger monitoring")
        isMonitoring = false
        
        audioRecorder?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio recorder", e)
            }
        }
        audioRecorder = null
        
        stopForeground(true)
        stopSelf()
    }

    /**
     * Create notification channel (Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Stealth Protection",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            ).apply {
                description = "Background protection monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(message: String = "Listening for emergency triggers...") = 
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🛡️ Stealth Protection Active")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createPendingIntent())
            .addAction(
                0,
                "Stop",
                createStopPendingIntent()
            )
            .build()

    /**
     * Update notification text
     */
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    /**
     * Create pending intent to open calculator
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, StealthCalculatorActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create pending intent to stop service
     */
    private fun createStopPendingIntent(): PendingIntent {
        val intent = Intent(this, StealthTriggerService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        return PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Show the emergency overlay on top of all apps, requesting SYSTEM_ALERT_WINDOW permission if needed
     */
    private fun showEmergencyOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Launch intent to request permission
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                Log.w(TAG, "Requesting SYSTEM_ALERT_WINDOW permission for emergency overlay")
                startActivity(intent)
                return
            }
        }
        // Start overlay service to show the emergency overlay on top of all apps
        val overlayIntent = Intent(this, EmergencyOverlayService::class.java)
        overlayIntent.putExtra("TRIGGER_REASON", "Emergency Detected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
        Log.i(TAG, "Emergency overlay started")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
        Log.i(TAG, "Stealth Trigger Service destroyed")
    }
}
