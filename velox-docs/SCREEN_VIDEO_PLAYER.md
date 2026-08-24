# Screen: Video Player

**Route:** `video_player/{mediaId}`  
**Priority:** P0  

---

## 1. Purpose

Immersive video playback with minimal chrome, powerful gestures, and quick access to tracks, subtitles, and options.

---

## 2. Layout Modes

### 2.1 Immersive (controls hidden)
```
┌──────────────────────────────────────┐
│                                      │
│                                      │
│              VIDEO                   │
│                                      │
│                                      │
└──────────────────────────────────────┘
```
- True edge-to-edge
- System bars hidden or immersive sticky
- Tap anywhere → show controls

### 2.2 Controls Visible
```
┌──────────────────────────────────────┐
│ ← Title                    ⋮ / Cast  │  ← Top bar (glass gradient)
│                                      │
│                                      │
│              VIDEO                   │
│                                      │
│                                      │
│  ━━━━━●━━━━━━━━━━  12:34 / 45:00    │
│  ⇄  ◁◁  ▶❚❚  ▷▷  ↻   ⚙  CC  ⋯     │  ← Bottom control cluster
└──────────────────────────────────────┘
```

---

## 3. Top Bar (when visible)

- Back / Close
- Video title (ellipsize)
- Optional: Cast, PiP, More (aspect ratio, playback speed, tracks…)

Background: vertical gradient from black/scrim → transparent.

---

## 4. Bottom Control Cluster

Glass or dark translucent panel with:

1. Progress bar + time labels
2. Transport row:
   - Shuffle (if in playlist context)
   - Previous
   - Play / Pause (prominent)
   - Next
   - Repeat
3. Utility row or overflow:
   - Aspect ratio
   - Playback speed
   - Audio tracks
   - Subtitles (CC)
   - Lock controls
   - More

---

## 5. Gesture Map

| Gesture                     | Zone          | Action                          |
|-----------------------------|---------------|---------------------------------|
| Single tap                  | Anywhere      | Toggle controls                 |
| Double tap                  | Left / Right  | Seek −10s / +10s (configurable) |
| Horizontal drag             | Center        | Seek                            |
| Vertical drag               | Left half     | Brightness                      |
| Vertical drag               | Right half    | Volume                          |
| Long press                  | Center        | Speed scrub (1.5x–2x etc.)      |
| Pinch                       | Anywhere      | Zoom / crop                     |
| Swipe down (from top or center) | —        | Exit / minimize to PiP (config) |

Visual feedback:
- Brightness / Volume: vertical indicator with icon + percentage
- Seek: time delta preview + thumbnail if available
- Speed: temporary label “1.5x”

---

## 6. Lock Mode

- Long press lock button or dedicated control
- Hides all controls and disables most gestures (except unlock)
- Unlock: tap lock icon or long press screen
- Prevents accidental touches during long sessions

---

## 7. Subtitles

- Rendered by player (Media3)
- Styling controlled in Settings + quick panel:
  - Size
  - Text color
  - Background / window color
  - Position (bottom / top)
- Quick access: CC button → list of tracks + “Open external” + “Search online” (Phase 1.1)
- Delay adjustment: −/+ buttons or slider in subtitle panel

---

## 8. Aspect Ratio Options

- Fit (default)
- Fill
- Stretch
- Original / 100%
- 16:9 / 4:3 crops
- Cycle via button or menu

---

## 9. Picture-in-Picture

- System PiP on home or back (configurable)
- Continues playback
- Tap PiP window → return to full player

---

## 10. States

| State             | Treatment                                      |
|-------------------|------------------------------------------------|
| Buffering         | Centered thin progress + optional %            |
| Error             | Message overlay + retry / open with…           |
| End of video      | Replay / Next / Close options                  |
| No audio track    | Silent indicator                               |
| Subtitle loading  | Brief toast or indicator                       |

---

## 11. Transition from Audio Now Playing

When user opens a video:
- Fullscreen landscape preference possible (auto-rotate aware)
- Same player engine; UI switches to video chrome
- Mini player still available when user leaves the screen (audio continues if background audio enabled)

---

## 12. RTL Notes

- Back arrow mirrors
- Seek double-tap zones stay left = back / right = forward in screen coordinates (physical), not logical — common video convention
- Text and menus fully RTL
- Brightness (left) / Volume (right) zones stay physical for muscle memory

---

## 13. Motion

- Controls fade in/out: 200–250ms
- Seek / volume / brightness overlays: fast fade
- Aspect ratio change: smooth content scale
- Enter PiP: system animation
