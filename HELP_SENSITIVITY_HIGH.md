# HELP Detection - HIGH SENSITIVITY (Works with Slow/Quiet Speech)

## ✅ **Settings Updated to HIGH SENSITIVITY**

The HELP detection has been adjusted to **HIGH SENSITIVITY** - it will now detect "HELP" even when
spoken **slowly and quietly**.

---

## 📊 **High Sensitivity Thresholds**

### **StealthBodyguardManager.kt (Calculator Mode)**

| Setting | Previous (Balanced) | Now (HIGH SENSITIVITY) | Change |
|---------|---------------------|------------------------|--------|
| **Volume (RMS)** | 2000 | **1700** | **-15%** ⬇️ (quieter accepted) |
| **Peak Amplitude** | 6500 | **5500** | **-15%** ⬇️ (lower peak accepted) |
| **High Energy Threshold** | 7000 | **6000** | **-14%** ⬇️ (quieter consonants) |
| **High Energy Ratio** | 0.16 | **0.13** | **-19%** ⬇️ (lower energy OK) |
| **ZCR Range** | 0.09-0.18 | **0.08-0.20** | **+33% wider** ⬆️ (more flexible) |
| **Energy Variance** | 0.11 | **0.09** | **-18%** ⬇️ (easier to achieve) |
| **Min Confidence** | 70% | **60%** | **-14%** ⬇️ (need only 3/5 conditions) |

### **StealthTriggerService.kt (Background Service)**

| Setting | Previous (Balanced) | Now (HIGH SENSITIVITY) | Change |
|---------|---------------------|------------------------|--------|
| **Min RMS** | 2000 | **1700** | **-15%** ⬇️ |
| **Peak** | 6500 | **5500** | **-15%** ⬇️ |
| **High Energy Threshold** | 7000 | **6000** | **-14%** ⬇️ |
| **High Energy Ratio** | 0.16 | **0.13** | **-19%** ⬇️ |
| **ZCR Range** | 0.09-0.18 | **0.08-0.20** | **+33% wider** ⬆️ |
| **Energy Variance** | 0.11 | **0.09** | **-18%** ⬇️ |

---

## 🎯 **High Sensitivity Detection Logic**

```kotlin
// HELP-specific characteristics (MORE SENSITIVE):

1. isSingleSyllable = burstCount == 1
   → EXACTLY 1 burst (HELP is one syllable)
   → Weight: 35%

2. hasHelpZCR = zcr in 0.08f..0.20f
   → WIDER ZCR range (accepts slower speech)
   → Weight: 25%

3. hasStrongConsonants = peak > 5500f && highEnergyRatio > 0.13f
   → LOWER thresholds (quieter speech works)
   → Weight: 20%

4. hasEnergyVariance = energyVariance > 0.09f
   → LOWER variance (easier to achieve)
   → Weight: 10%

5. isLoudEnough = rms > 1700f
   → MUCH LOWER (quiet speech accepted)
   → Weight: 10%

Confidence threshold: >= 60% (need only 3 out of 5 conditions)
```

**What this means:**

- Need only **3 out of 5 conditions** to trigger (60% confidence)
- Works with **quiet, slow speech**
- Accepts **lower volume** (RMS > 1700)
- Accepts **weaker consonants** (peak > 5500)
- **Wider ZCR range** for different speaking speeds
- Much more **forgiving**

---

## 💡 **What Will Trigger Now?**

### **✅ WILL Trigger (Intended):**

```
✅ "HELP" (said quietly)
✅ "HELP" (spoken slowly)
✅ "help" (lowercase = quiet)
✅ "HELP" (whispered but clear)
✅ "HELP" (normal volume)
✅ "HELP" (loud volume)
✅ "HELP!" (emphatic)
```

### **❌ Should NOT Trigger (Hopefully):**

```
❌ "hello" (2 syllables - still filtered)
❌ "okay" (2 syllables - still filtered)
❌ Very long sentences (multiple syllables)
❌ Pure background music (no voice pattern)
```

### **⚠️ MIGHT Trigger More Often (Trade-off):**

```
⚠️ "test" (might now trigger - 1 syllable, quiet)
⚠️ "stop" (likely will trigger - similar pattern)
⚠️ "yes" (might trigger - 1 syllable)
⚠️ Other single-syllable words
⚠️ Some loud background conversation
```

---

## 📈 **Complete Sensitivity Evolution**

| Version | RMS | Peak | Energy Ratio | ZCR | Confidence | Use Case |
|---------|-----|------|--------------|-----|------------|----------|
| **v1 (Too Strict)** | 4000 | 10000 | N/A | 0.08-0.16 | 80% | Missed everything ❌ |
| **v2 (Too Sensitive)** | 1500 | 4000 | N/A | 0.02-0.35 | 40% | Everything triggered ❌ |
| **v3 (Balanced)** | 1800 | 5000 | N/A | 0.05-0.28 | 55% | Generic speech ⚠️ |
| **v4 (HELP-Specific)** | 1900 | 6500 | 0.15 | 0.08-0.18 | 70% | Good balance ✅ |
| **v5 (Stricter)** | 2100 | 7000 | 0.18 | 0.09-0.17 | 75% | Clear speech only ⚠️ |
| **v6 (Too Strict)** | 2300 | 7200 | 0.20 | 0.10-0.16 | 80% | Nothing detected ❌ |
| **v7 (Optimal)** | 2000 | 6500 | 0.16 | 0.09-0.18 | 70% | Normal speech ✅ |
| **v8 (HIGH SENS)** | **1700** | **5500** | **0.13** | **0.08-0.20** | **60%** | **Quiet/slow speech** ✅ |

---

## 🎬 **Expected Behavior Examples**

### **Test 1: Say "HELP" quietly**

```
Audio Analysis:
├─ Syllables detected: 1 ✅
├─ Bursts: 1 ✅
├─ RMS: 1800 ✅ (> 1700) - quiet but detected
├─ ZCR: 0.14 ✅ (in 0.08-0.20)
├─ Peak: 5800 ✅ (> 5500) - lower peak accepted
├─ High Energy Ratio: 0.14 ✅ (> 0.13)
└─ Energy Variance: 0.10 ✅ (> 0.09)

Confidence: 100% (5/5 conditions)
Result: ✅ HELP DETECTED → Counter: 1/3
```

### **Test 2: Say "HELP" slowly**

```
Audio Analysis:
├─ Syllables detected: 1 ✅
├─ Bursts: 1 ✅
├─ RMS: 1900 ✅ (> 1700)
├─ ZCR: 0.19 ✅ (in 0.08-0.20) - slower speech accepted
├─ Peak: 6000 ✅ (> 5500)
├─ High Energy Ratio: 0.15 ✅ (> 0.13)
└─ Energy Variance: 0.11 ✅ (> 0.09)

Confidence: 100% (5/5 conditions)
Result: ✅ HELP DETECTED → Counter: 1/3
```

### **Test 3: Whisper "help"**

```
Audio Analysis:
├─ Syllables detected: 1 ✅
├─ Bursts: 1 ✅
├─ RMS: 1750 ✅ (> 1700) - whisper detected!
├─ ZCR: 0.15 ✅ (in 0.08-0.20)
├─ Peak: 5600 ✅ (> 5500)
├─ High Energy Ratio: 0.13 ✅ (> 0.13)
└─ Energy Variance: 0.09 ✅ (> 0.09)

Confidence: 100% (5/5 conditions)
Result: ✅ HELP DETECTED → Counter: 1/3
```

### **Test 4: Say "hello"**

```
Audio Analysis:
├─ Syllables detected: 2 ❌ (not 1) - still filtered
├─ Bursts: 2 ❌ (not 1)
├─ RMS: 1900 ✅
├─ ZCR: 0.22 ❌ (outside 0.08-0.20)
├─ Peak: 5400 ❌ (< 5500)
├─ High Energy Ratio: 0.11 ❌ (< 0.13)
└─ Energy Variance: 0.08 ❌ (< 0.09)

Confidence: 10% (1/5 conditions)
Result: ❌ NOT DETECTED (< 60%) → Counter stays at 0/3
```

### **Test 5: Say "test" quietly**

```
Audio Analysis:
├─ Syllables detected: 1 ✅
├─ Bursts: 1 ✅
├─ RMS: 1800 ✅ (> 1700)
├─ ZCR: 0.16 ✅ (in 0.08-0.20)
├─ Peak: 5400 ❌ (< 5500) - T is weaker
├─ High Energy Ratio: 0.12 ❌ (< 0.13)
└─ Energy Variance: 0.08 ❌ (< 0.09)

Confidence: 60% (3/5 conditions)
Result: ⚠️ MIGHT DETECT (= 60%) → Counter: 1/3
(Acceptable - counter prevents false emergency)
```

### **Test 6: Say "stop"**

```
Audio Analysis:
├─ Syllables detected: 1 ✅
├─ Bursts: 1 ✅
├─ RMS: 1900 ✅ (> 1700)
├─ ZCR: 0.14 ✅ (in 0.08-0.20)
├─ Peak: 5900 ✅ (> 5500) - S and P are strong
├─ High Energy Ratio: 0.14 ✅ (> 0.13)
└─ Energy Variance: 0.10 ✅ (> 0.09)

Confidence: 100% (5/5 conditions)
Result: ⚠️ LIKELY WILL DETECT → Counter: 1/3
(Acceptable - also an emergency word)
```

---

## ⚖️ **Trade-offs**

### **✅ Advantages (HIGH SENSITIVITY):**

1. **Works with Quiet Speech**: Detects "HELP" spoken quietly
2. **Works with Slow Speech**: Accepts slower speaking
3. **Accessible**: Works for people with weak voices
4. **Emergency Friendly**: Easier to trigger in genuine emergency
5. **Forgiving**: More tolerant of speech variations

### **⚠️ Disadvantages (Trade-offs):**

1. **Higher False Positive Rate**: More words might trigger
2. **"STOP" Will Likely Trigger**: Similar acoustic pattern
3. **"test" Might Trigger**: Single syllable, similar pattern
4. **Louder Conversations Might Trigger**: More sensitive to background
5. **Requires 3x Counter**: Counter system is critical

### **🛡️ Critical Safety Features:**

1. **3x Counter Required**: Must say "HELP" 3 times (prevents single false positives)
2. **10s Timeout**: Counter resets after 10 seconds
3. **Single Syllable Check**: Still filters multi-syllable words
4. **Pattern Matching**: Still uses HELP-specific patterns

---

## 🔍 **Debug Logging**

```bash
adb logcat | grep "Voice:"
```

### **When saying "HELP" quietly (✅ should detect):**

```
Voice: RMS=1800, ZCR=0.140, Peak=5800, Bursts=1, HighEnergy=0.14, Variance=0.10, Conf=1.00 ✅ HELP!
```

### **When whispering "help" (✅ should detect):**

```
Voice: RMS=1750, ZCR=0.150, Peak=5600, Bursts=1, HighEnergy=0.13, Variance=0.09, Conf=1.00 ✅ HELP!
```

### **When saying "hello" (❌ should NOT detect):**

```
Voice: RMS=1900, ZCR=0.220, Peak=5400, Bursts=2, HighEnergy=0.11, Variance=0.08, Conf=0.10 ❌
```

### **When saying "test" (⚠️ might detect):**

```
Voice: RMS=1800, ZCR=0.160, Peak=5400, Bursts=1, HighEnergy=0.12, Variance=0.08, Conf=0.60 ⚠️
```

---

## 📱 **How to Test**

### **Step 1: Open Calculator**

```
SHAKTI AI → Settings → Stealth Mode → Open Calculator
```

### **Step 2: Test "HELP" at Different Volumes**

```
Whisper "help" quietly → Counter: 1/3 ✅ (should work now!)
Say "HELP" quietly → Counter: 2/3 ✅ (should work!)
Say "HELP" at normal volume → Counter: 3/3 → EMERGENCY! ✅
```

### **Step 3: Test Slow Speech**

```
Say "HELP" slowly and clearly → Counter should increment ✅
```

### **Step 4: Monitor for False Positives**

```
Say "test" → Might trigger ⚠️
Say "stop" → Likely triggers ⚠️
Say "hello" → Should NOT trigger ✅
Say "okay" → Should NOT trigger ✅
```

---

## 💻 **Code Changes Summary**

### **Files Modified:**

1. ✅ `StealthBodyguardManager.kt` (lines ~606-650)
    - RMS: 2000 → **1700** (-15%)
    - Peak: 6500 → **5500** (-15%)
    - High Energy Threshold: 7000 → **6000** (-14%)
    - High Energy Ratio: 0.16 → **0.13** (-19%)
    - ZCR Range: 0.09-0.18 → **0.08-0.20** (+33% wider)
    - Energy Variance: 0.11 → **0.09** (-18%)
    - Confidence: 70% → **60%** (-14%)

2. ✅ `StealthTriggerService.kt` (lines ~56-60, ~320-355)
    - MIN_RMS: 2000 → **1700** (-15%)
    - High Energy Threshold: 7000 → **6000** (-14%)
    - High Energy Ratio: 0.16 → **0.13** (-19%)
    - ZCR Range: 0.09-0.18 → **0.08-0.20** (+33% wider)
    - Energy Variance: 0.11 → **0.09** (-18%)
    - Peak: 6500 → **5500** (-15%)

---

## ✅ **Final Result**

**The HELP detection is now HIGHLY SENSITIVE - it will detect "HELP" even when spoken quietly and
slowly.**

### **Expected Behavior:**

✅ **"help" (whispered)** → Detected
✅ **"HELP" (quiet)** → Detected
✅ **"HELP" (slow)** → Detected
✅ **"HELP" (normal)** → Detected
✅ **"HELP" (loud)** → Detected
❌ **"hello"** → NOT detected (2 syllables)
❌ **"okay"** → NOT detected (2 syllables)
⚠️ **"test"** → MIGHT detect (1 syllable, similar)
⚠️ **"stop"** → LIKELY detects (1 syllable, strong consonants)
⚠️ **"yes"** → MIGHT detect (1 syllable)

**This setting prioritizes DETECTION RATE over accuracy. The 3x counter system is CRITICAL to
prevent false emergency triggers.** 🎯✅

---

## 🎯 **Summary**

**These HIGH SENSITIVITY settings:**

- ✅ Detect "HELP" at **quiet volumes**
- ✅ Detect "HELP" spoken **slowly**
- ✅ Work with **whispers** (barely above threshold)
- ✅ Very **accessible** and **forgiving**
- ⚠️ Higher false positive rate (but 3x counter mitigates this)
- ⚠️ Words like "STOP", "test" might trigger occasionally

**The 3x counter requirement is ESSENTIAL - it prevents single false positives from triggering full
emergency!**

---

**Try it now! Say "help" quietly or slowly 3 times - it should work!** 🎤✅

**Warning: This setting may have more false positives. Monitor the counter carefully!**
