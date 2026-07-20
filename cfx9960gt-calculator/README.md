# Casio CFX-9960GTe

A functional recreation of the **Casio CFX-9960GT** graphing calculator for the **Ratta Supernote Nomad** (and any Android 5.0+ device).

---

## Features

### COMP Mode (scientific calculator)
| Feature | Details |
|---|---|
| Arithmetic | `+` `−` `×` `÷` `^` `%` |
| Trig | `sin` `cos` `tan` + inverses (DEG / RAD / GRA) |
| Hyperbolic | `sinh` `cosh` `tanh` + inverses (HYP key) |
| Logarithms | `log` `ln` `log₂` `10ˣ` `eˣ` |
| Roots | `√` `∛` arbitrary `^` |
| Other | `abs` `ceil` `floor` `!` `x⁻¹` `x²` `nPr` `nCr` `gcd` `lcm` `Ran#` |
| Constants | `π` `e` `Ans` |
| Variables | `A`–`Z` (store via ALPHA + key, recall via VARS or ALPHA) |
| Memory | `M+` `M-` `RCL` |
| EXP input | `×10ˣ` key (e.g. `1.5×10^3` → 1500) |
| Display modes | Normal · Fix n · Sci |

### GRAPH Mode
- Up to **3 simultaneous equations** (Y1, Y2, Y3)
- Configurable **View Window** (Xmin, Xmax, Xscl, Ymin, Ymax, Yscl)
- **TRACE** – touch/drag to read X/Y coordinates
- **TABLE** – tabulate Y1 values over the view range
- Axes with tick marks and labels
- Grid lines

### STAT Mode (1-Variable Statistics)
- Enter data as a list → calculates n, Σx, Σx², x̄, σx, sx, Min, Max

---

## Keyboard Layout (6 × 9)

```
┌─────┬─────┬─────┬─────┬─────┬─────┐
│ F1  │ F2  │ F3  │ F4  │ F5  │ F6  │  ← context-sensitive soft keys
├─────┼─────┼─────┼─────┼─────┼─────┤
│SHIFT│ALPHA│x,θ,T│MENU │  ▲  │ AC  │
├─────┼─────┼─────┼─────┼─────┼─────┤
│OPTN │VARS │ DEL │  ◄  │  ▼  │  ►  │
├─────┼─────┼─────┼─────┼─────┼─────┤
│ x²  │ x⁻¹ │ log │  ln │ (-) │EXIT │
├─────┼─────┼─────┼─────┼─────┼─────┤
│ sin │ cos │ tan │×10ˣ │ HYP │ ∛x  │
├─────┼─────┼─────┼─────┼─────┼─────┤
│  7  │  8  │  9  │  (  │  )  │  ÷  │
├─────┼─────┼─────┼─────┼─────┼─────┤
│  4  │  5  │  6  │  ×  │  √x │  π  │
├─────┼─────┼─────┼─────┼─────┼─────┤
│  1  │  2  │  3  │  +  │  -  │ Ans │
├─────┼─────┼─────┼─────┼─────┼─────┤
│  0  │  .  │  ,  │ M+  │ EXE │     │
└─────┴─────┴─────┴─────┴─────┴─────┘
```

**F-key row** (COMP mode default):
`F1=COMP  F2=GRAPH  F3=STAT  F4=DEG  F5=RAD  F6=GRA`

---

## Build

```bash
cd cfx9960gt-calculator
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** Android SDK, Java 8+, Gradle 8.x (wrapper included).

---

## Sideloading to Supernote Nomad

1. Build the debug APK (`./gradlew assembleDebug`) or download a release APK.
2. On the Nomad: **Settings → Security & Privacy → Allow Sideloading** → enable.
3. Connect via USB and use the [Supernote Android Sideloader](https://github.com/zwpaper/supernote-sideloader) or ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. The app appears as **"Casio CFX-9960GTe"** in the launcher.

---

## Version History

| Version | Notes |
|---|---|
| 1.0 | Initial release – COMP mode (full scientific), GRAPH mode (Y1/Y2/Y3, trace, table), STAT 1-var |

---

## Disclaimer

This is an independent functional recreation for educational and personal use.
Casio® and CFX-9960GT® are registered trademarks of Casio Computer Co., Ltd.
This project is not affiliated with or endorsed by Casio.
