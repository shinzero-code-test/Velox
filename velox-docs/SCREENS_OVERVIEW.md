# Velox Screens Overview

**App ID:** `com.exapps.velox`  
**Design language:** Dark glass • Large artwork • Subtle gradients • Rounded cards • Micro-animations  
**Direction:** Arabic-first (RTL)

---

## 1. Screen Map

```
┌─────────────────┐
│   Onboarding    │ (first launch only)
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────────┐
│  Home / Library │────▶│  Search          │
└────────┬────────┘     └──────────────────┘
         │
         ├──────────────▶ Artist / Album / Folder Detail
         │
         ├──────────────▶ Playlist List ──▶ Playlist Detail
         │
         ├──────────────▶ Favorites / Recent / Most Played
         │
         ▼
┌─────────────────┐
│  Now Playing    │◀── Mini Player (persistent)
│  (Full Screen)  │
└────────┬────────┘
         │
         ├──────────────▶ Queue Sheet
         ├──────────────▶ Equalizer
         ├──────────────▶ Subtitles
         ├──────────────▶ Sleep Timer
         └──────────────▶ Audio / Video Tracks

┌─────────────────┐
│  Settings Hub   │──▶ Sub-pages (Playback, Library, Appearance, About…)
└─────────────────┘
```

---

## 2. Primary Screens (v1.0)

| Screen                    | File                                      | Priority |
|---------------------------|-------------------------------------------|----------|
| Onboarding                | [SCREEN_ONBOARDING.md](./SCREEN_ONBOARDING.md) | P1      |
| Home / Library            | [SCREEN_HOME_LIBRARY.md](./SCREEN_HOME_LIBRARY.md) | P0   |
| Now Playing (Audio)       | [SCREEN_NOW_PLAYING.md](./SCREEN_NOW_PLAYING.md) | P0    |
| Video Player Overlay      | [SCREEN_VIDEO_PLAYER.md](./SCREEN_VIDEO_PLAYER.md) | P0  |
| Playlists                 | [SCREEN_PLAYLISTS.md](./SCREEN_PLAYLISTS.md) | P0      |
| Search                    | [SCREEN_SEARCH.md](./SCREEN_SEARCH.md)    | P0       |
| Equalizer                 | [SCREEN_EQUALIZER.md](./SCREEN_EQUALIZER.md) | P0     |
| Settings                  | [SCREEN_SETTINGS.md](./SCREEN_SETTINGS.md) | P0      |
| Shared Patterns           | [SCREEN_PATTERNS.md](./SCREEN_PATTERNS.md) | —       |

---

## 3. Global UI Chrome

### 3.1 Mini Player
- Persistent bottom bar when media is loaded
- Appears above bottom navigation (if any) or at screen bottom
- Height: 68–72dp
- Glass background + thin top border
- Content: Artwork (48dp rounded) • Title + Artist • Play/Pause
- Gestures: Tap → expand Now Playing • Swipe up → expand • Swipe away → stop (optional confirm)

### 3.2 Bottom Navigation (optional for v1)
Recommended destinations:
1. المكتبة (Library)
2. قوائم التشغيل (Playlists)
3. المفضلة (Favorites) or البحث (Search)
4. الإعدادات (Settings)

If using bottom nav, Mini Player sits directly above it.

Alternative (cleaner): No bottom nav — use top tabs / drawer / FAB patterns and rely on gestures + mini player. Decision can be finalized in implementation; docs support both.

### 3.3 System Bars
- Edge-to-edge
- Dark icons / light icons adapted to background
- Status bar transparent or subtle scrim
- Navigation bar transparent or matching surface

---

## 4. Navigation Model

- Single Activity + Navigation Compose
- Shared element transitions for artwork (Library → Now Playing, Album → Now Playing)
- Predictive back supported
- Bottom sheets for Queue, Sleep Timer, Track selection, Sort/Filter
- Full screens for Equalizer, Settings sub-pages, Onboarding

---

## 5. Common States (All Screens)

Every major list/detail screen must define:

| State          | Treatment                                      |
|----------------|------------------------------------------------|
| Loading        | Subtle shimmer or centered glass progress      |
| Empty          | Illustration / icon + Arabic message + CTA     |
| Error          | Clear message + retry action                   |
| Content        | Normal                                         |
| Offline / No permission | Permission explanation + action button   |

---

## 6. RTL Rules (Global)

- All horizontal layouts reverse in RTL
- Icons that imply direction (back, next, skip) mirror
- Progress bars and scrubbers remain LTR for time (universal convention) unless decided otherwise
- Artwork stays visually centered; text alignment follows locale
- Test every screen in Arabic RTL before sign-off

---

## 7. Motion Across Screens

- Artwork shared-element: Library card ↔ Now Playing hero
- Mini Player expand/collapse: spring, 380–450ms
- Sheet present/dismiss: medium spring
- List item press: scale 0.97 + opacity
- Page transitions: fade + slight slide (respect reduced motion)

---

## 8. Related Documents

- [DESIGN_SYSTEM.md](./DESIGN_SYSTEM.md) — tokens, components, motion
- [FEATURES.md](./FEATURES.md) — what each screen must support
- [LOCALIZATION.md](./LOCALIZATION.md) — Arabic strings & terminology
