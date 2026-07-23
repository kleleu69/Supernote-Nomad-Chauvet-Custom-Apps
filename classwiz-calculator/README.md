# ClassWiz Calculator  
**Casio fx-991CW (ClassWiz) inspired scientific calculator for Supernote Nomad (Chauvet OS)**

## Features

| Category | Functions |
|---|---|
| Arithmetic | `+  −  ×  ÷  ^  %` |
| Trig | `sin  cos  tan  sin⁻¹  cos⁻¹  tan⁻¹` with DEG / RAD / GRD mode |
| Hyperbolic | `sinh  cosh  tanh  asinh  acosh  atanh` |
| Logarithms | `ln  log₁₀  log₂  eˣ  10ˣ` |
| Roots / Powers | `√x  ∛x  x²  x³  x^y  x⁻¹` |
| Other | `x!  abs(x)  rand  ceil  floor  round  sign` |
| Constants | `π  e  Ans` |
| Variables | `A  B  C  D  E  F  M` (store / recall) |
| Memory | `M+  M-  RCL  STO` |
| Display | S⇔D fraction / decimal toggle, engineering notation shift |

E-ink optimised:
- Hardware acceleration disabled (software rendering only)
- Pure black/white colour palette — no gradients, no transparency
- All window animations disabled
- Static button layout, no ripple effects

---

## Button layout (5 × 8 grid)

```
┌────────┬────────┬────────┬────────┬────────┐
│ SHIFT  │ ALPHA  │  MODE  │ ◄DEL   │   AC   │
│        │        │ [SETUP]│ [INS]  │ [CLR]  │
├────────┼────────┼────────┼────────┼────────┤
│  sin   │  cos   │  tan   │   ln   │  log   │
│[sin⁻¹]│[cos⁻¹]│[tan⁻¹]│  [eˣ]  │ [10ˣ] │
│  {A}   │  {B}   │  {C}   │  {D}   │  {E}   │
├────────┼────────┼────────┼────────┼────────┤
│  x⁻¹  │   x²   │   √x   │   ^    │   (    │
│  [x!]  │  [x³]  │  [∛x]  │[log_a] │ [|x|]  │
│  {F}   │        │        │        │        │
├────────┼────────┼────────┼────────┼────────┤
│  S⇔D  │   M+   │  RCL   │  ENG   │   )    │
│  [HYP] │  [M-]  │  [STO] │ [←ENG] │ [Ran#] │
│        │  {M}   │        │        │        │
├────────┼────────┼────────┼────────┼────────┤
│   7    │   8    │   9    │   ÷    │   ×    │
├────────┼────────┼────────┼────────┼────────┤
│   4    │   5    │   6    │   -    │   +    │
├────────┼────────┼────────┼────────┼────────┤
│   1    │   2    │   3    │   π    │   e    │
│        │        │        │ [Ran#] │        │
├────────┼────────┼────────┼────────┼────────┤
│   0    │   .    │  ×10ˣ  │  Ans   │   =    │
│        │        │        │  [%]   │        │
└────────┴────────┴────────┴────────┴────────┘
  [x]  = SHIFT+key    {x} = ALPHA+key
```

---

## Building

### Prerequisites
- **Android Studio** Giraffe (2022.3) or newer  
  *or* command-line: JDK 17 + Android SDK (build-tools 34)

### Steps

```bash
# 1. Clone / open the project
cd classwiz-calculator

# 2. Generate the Gradle wrapper JAR (one-time, if not already present)
gradle wrapper --gradle-version 8.4

# 3. Build debug APK
./gradlew assembleDebug

# 4. Find the APK
#    app/build/outputs/apk/debug/app-debug.apk
```

### Signing for release (optional but recommended)

```bash
# Generate a key store
keytool -genkey -v -keystore classwiz.jks \
        -alias classwiz -keyalg RSA -keysize 2048 -validity 10000

# Build release APK (add signing config to app/build.gradle first)
./gradlew assembleRelease
```

---

## Sideloading onto Supernote Nomad (Chauvet OS)

1. **Enable unknown sources** on the Nomad:  
   *Settings → App & notification → Special app access → Install unknown apps*  
   or use the Supernote launcher's side-load option if available.

2. **Copy the APK** to the device via USB or the Files app:  
   ```
   adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
   ```

3. **Install** from the device's file manager, tap the APK.

4. **Launch** "ClassWiz Calculator" from the app drawer.

> **Tip:** The Nomad's e-ink display refreshes better in "Speed" or "A2" refresh mode.  
> If available in your firmware, enable fast-refresh for the calculator app in  
> *Settings → Display → Refresh mode*.

---

## Architecture

```
classwiz-calculator/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/supernote/classwiz/
│       │   ├── CalculatorEngine.kt   ← pure-Kotlin tokeniser + recursive-descent parser
│       │   └── MainActivity.kt       ← UI, 5×8 button grid, state machine
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/{colors,strings,themes}.xml
└── README.md  (this file)
```

## Licence

Apache 2.0 — see [LICENSE](../LICENSE).  
"Casio" and "ClassWiz" are trademarks of Casio Computer Co., Ltd.  
This app is an independent implementation and is not affiliated with or endorsed by Casio.
