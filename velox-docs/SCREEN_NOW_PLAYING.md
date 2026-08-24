# Screen: Now Playing (Audio Focus)

**Route:** `now_playing`  
**Priority:** P0  
**Entry:** Mini player tap / play action / notification

---

## 1. Purpose

Immersive full-screen experience for the currently playing audio (and as the expanded state for video audio). Celebrates artwork and provides primary controls.

---

## 2. Layout Structure

```
┌──────────────────────────────────────┐
│ ←  (optional)              ⋮         │  ← Top controls (auto-hide possible)
│                                      │
│                                      │
│          ┌──────────────┐            │
│          │              │            │
│          │   Artwork    │            │  ← Large, centered, radius-xl
│          │   (hero)     │            │
│          │              │            │
│          └──────────────┘            │
│                                      │
│         Title (display)              │
│         Artist / Album               │
│                                      │
│    ━━━━━━━━━●━━━━━━━━━━  2:34 / 5:12 │  ← Progress + times
│                                      │
│   ⇄    ◁◁    ▶ / ❚❚    ▷▷    ↻     │  ← Main transport
│                                      │
│   ♡     ☰ Queue    EQ     ⏱ Sleep   │  ← Secondary row
│                                      │
└──────────────────────────────────────┘
```

Background: Blurred/cropped artwork + dark gradient scrim (see Design System).

---

## 3. Hero Artwork

- Size: ~280–320dp on phone (responsive)
- Shape: `radius-xl` (24–32dp)
- Soft ambient shadow or glow
- Optional subtle rotation or parallax on device tilt (future, disabled by default)
- Shared element source/target from Library cards and Mini Player

---

## 4. Typography Hierarchy

| Element       | Style             | Notes                          |
|---------------|-------------------|--------------------------------|
| Title         | display-medium / large | 1–2 lines, marquee if needed |
| Artist        | title-medium      | Accent or secondary color      |
| Album / Info  | body-medium       | Optional                       |
| Time labels   | label-medium      | Elapsed • Remaining or Total   |

---

## 5. Progress Bar

- Thick enough for easy touch (track ~4–6dp, thumb 12–16dp)
- Accent color fill
- Buffered range subtle
- Seek on drag; optional preview time tooltip
- Time labels: start (elapsed) and end (total or remaining — user preference)

---

## 6. Main Transport Controls

| Control     | Icon suggestion     | Behavior                          |
|-------------|---------------------|-----------------------------------|
| Shuffle     | ⇄                   | Toggle, accent when on            |
| Previous    | ◁◁                  | Prev track / restart if >3s       |
| Play/Pause  | ▶ / ❚❚              | Large, accent or filled glass     |
| Next        | ▷▷                  | Next track                        |
| Repeat      | ↻ / ↻¹              | Off → All → One                   |

Play/Pause is the visual anchor (larger, possibly contained in glass circle).

---

## 7. Secondary Actions Row

- Favorite / Like (♡ → filled)
- Queue (opens Queue sheet)
- Equalizer (navigates to EQ screen)
- Sleep Timer (opens timer sheet)
- Optional: Lyrics, Share, Cast

Use glass icon buttons or simple tonal icons.

---

## 8. Gestures

| Gesture              | Action                              |
|----------------------|-------------------------------------|
| Swipe down           | Collapse to Mini Player             |
| Horizontal swipe     | Next / Previous track (optional)    |
| Tap artwork          | (optional) toggle lyrics or info    |
| Long press progress  | Fine seek                           |

---

## 9. Queue Sheet

- Half-to-full expandable bottom sheet
- Glass surface
- Current item highlighted
- Drag to reorder
- Swipe to remove
- Clear queue action
- “Play next” / “Add to queue” sources feed here

---

## 10. States

| State            | Treatment                                      |
|------------------|------------------------------------------------|
| Playing          | Animated play/pause, progress moving           |
| Paused           | Static, play icon                              |
| Buffering        | Subtle progress indicator near controls        |
| No artwork       | Gradient placeholder + music note icon         |
| Error            | Message + retry / skip                         |

---

## 11. RTL Notes

- Back arrow mirrors
- Previous/Next icons mirror
- Title and artist right-aligned in Arabic
- Progress bar direction: keep LTR (time progresses left→right universally) or follow locale — document decision and stay consistent
- Secondary actions order mirrors

---

## 12. Motion Specs

- Enter from Mini Player: shared art + spring expand (400ms)
- Exit to Mini Player: reverse shared element
- Play ↔ Pause icon morph
- Favorite: small scale + color burst
- Sheet: standard medium spring

---

## 13. Accessibility

- All controls have content descriptions in Arabic & English
- Progress bar announced as seek control
- Large touch targets (min 48dp)
- Support for media session / headset controls (already system level)
