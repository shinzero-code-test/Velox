# Screen: Equalizer

**Route:** `equalizer`  
**Priority:** P0

---

## 1. Purpose

Visual multi-band equalizer with presets and additional audio effects. Feels precise and modern.

---

## 2. Layout

```
┌──────────────────────────────────────┐
│ ←  معادل الصوت              Reset    │
├──────────────────────────────────────┤
│  [Preset chips: عادي | بوب | روك | ...] │
├──────────────────────────────────────┤
│                                      │
│     │  │  │  │  │  │  │  │  │  │     │
│     │  │  │  │  │  │  │  │  │  │     │  ← 10 vertical sliders
│     │  │  │  │  │  │  │  │  │  │     │
│    31 62 125 250 500 1k 2k 4k 8k 16k │
│                                      │
├──────────────────────────────────────┤
│  Bass Boost          [====●====]     │
│  Virtualizer         [====●====]     │
│  Loudness            [  On | Off ]   │
├──────────────────────────────────────┤
│  Apply to: Current / All media       │
└──────────────────────────────────────┘
```

---

## 3. Bands

- 10-band recommended (classic)
- Frequency labels under each slider
- Values in dB (−15 to +15 typical)
- Smooth animated response when changing presets

---

## 4. Presets

Horizontal scrollable chips or dropdown:

- عادي (Normal / Flat)
- بوب (Pop)
- روك (Rock)
- جاز (Jazz)
- كلاسيك (Classical)
- صوت الجهير (Bass Boost)
- صوت بشري (Vocal / Voice)
- إلكتروني (Electronic)
- مستخدم (User / Custom) — saved automatically when user moves sliders

Active preset highlighted with accent.

---

## 5. Additional Effects

| Effect        | Control type      | Notes                          |
|---------------|-------------------|--------------------------------|
| Bass Boost    | Slider 0–100%     |                                |
| Virtualizer   | Slider 0–100%     |                                |
| Loudness      | Switch or slider  |                                |
| Reverb        | Optional later    | Presets or amount              |

---

## 6. Scope

- Toggle or selector: apply to **current media only** vs **global**
- Visual indication of which mode is active

---

## 7. Interactions

- Drag slider → live audio update (throttled if needed)
- Tap preset → animate bands to new values
- Reset → flat + effects off
- Back → persist current settings

---

## 8. Visual Style

- Dark glass surface
- Sliders use accent color for active portion
- Soft glow or highlight on active band (subtle)
- Avoid skeuomorphic “hardware” look; keep clean and modern

---

## 9. RTL

- Frequency order remains low → high left-to-right (technical convention) or fully mirrored — prefer keep LTR band order for familiarity with EQ UIs
- All labels and chips RTL
- Back button mirrors
