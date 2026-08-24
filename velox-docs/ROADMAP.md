# Velox Roadmap

**App ID:** `com.exapps.velox`  
**Last updated:** 2026-08-19

---

## Overview

Velox will be delivered in clear phases. Each phase produces a usable, polished product rather than half-finished features.

```
Phase 0          Phase 1 (v1.0)       Phase 1.1        Phase 2          Phase 3
Foundation  →    Core Player     →   Polish + Online → Power Features → Multi-platform
(4–6 weeks)      (10–14 weeks)       (4–6 weeks)       (8–12 weeks)     (ongoing)
```

---

## Phase 0 – Foundation (Weeks 1–6)

**Goal:** Solid technical base + design system + Arabic-first foundation.

### Deliverables
- [ ] Project setup (`com.exapps.velox`), modular structure
- [ ] Design system tokens & core Compose components
- [ ] Dark Glass theme implementation
- [ ] Arabic + English localization framework + RTL
- [ ] Basic navigation shell
- [ ] Media3 player integration (play local file)
- [ ] Room database schema for media library
- [ ] Basic media scanner (folders + MediaStore)
- [ ] CI basics + code style

**Exit Criteria:** Can open a local video/audio file, see basic Now Playing UI in Arabic RTL, and scan a folder into a list.

---

## Phase 1 – Core Player (v1.0) (Weeks 7–20)

**Goal:** Feature-complete local media player that feels premium.

### Milestones

#### M1 – Playback Core
- Full video & audio format support via Media3
- Audio/video track selection
- Speed control, seek, gestures
- Background audio + notification + media session
- PiP support

#### M2 – Library & Playlists
- Complete scanner + metadata extraction
- Artists / Albums / Folders / Genres views
- Search & sort
- Playlists CRUD + M3U import/export
- Favorites & Recently Played

#### M3 – Player Experience
- Full Now Playing screen with large artwork
- Mini player + queue
- Equalizer + presets
- Subtitle loading & styling (local)
- Sleep timer
- Gapless + crossfade

#### M4 – Polish & Settings
- All settings screens
- Widgets
- File association
- Performance tuning
- Arabic typography & layout refinement
- Accessibility basics

**Exit Criteria:** Stable v1.0 ready for closed beta / soft launch on Google Play (Arabic + English).

---

## Phase 1.1 – Early Enhancements (Weeks 21–26)

**Goal:** Address top user requests and polish.

- Online subtitle search
- Lyrics display (basic)
- Improved artwork & thumbnail pipeline
- Chromecast support (cast video/audio)
- Tag editor (basic metadata editing)
- More widget variants
- Crash & ANR hardening
- First public Play Store release

---

## Phase 2 – Power User Features (Weeks 27–38)

**Goal:** Reach parity with advanced features of MX Player / VLC for power users.

- Network streams (HTTP, HLS, DASH, RTSP)
- Network browsing (SMB, FTP, WebDAV)
- Advanced video processing options
- A-B repeat, bookmarks, chapters
- Advanced sleep timer options
- Playback statistics & history
- Backup / restore
- Custom gesture configuration
- Android Auto improved experience
- Foldable / large screen optimizations

---

## Phase 3 – Expansion (Ongoing)

### 3.1 Multi-platform
- iOS version (SwiftUI + AVPlayer, shared design language)
- Android TV interface
- Better tablet layouts

### 3.2 Ecosystem
- Optional cloud sync for playlists & settings
- Theme engine / community themes
- Plugin architecture for media sources
- Wear OS remote

### 3.3 Intelligence (Experimental)
- Smart silence / intro detection
- Auto chapter generation
- Personalized recommendations (on-device)

---

## Release Cadence (Post v1.0)

| Type            | Frequency          | Content                          |
|-----------------|--------------------|----------------------------------|
| Major           | Every 3–4 months   | Significant features             |
| Minor           | Monthly            | Improvements + smaller features  |
| Patch           | As needed          | Bugs, stability, Play policy     |

---

## Success Metrics (v1.0 → v1.5)

| Metric                        | Target                     |
|-------------------------------|----------------------------|
| Play Store rating             | ≥ 4.5                      |
| Crash-free sessions           | ≥ 99.5%                    |
| Day-1 retention               | ≥ 45%                      |
| Day-7 retention               | ≥ 25%                      |
| Average session length        | ≥ 18 min                   |
| Arabic language usage share   | Track & optimize           |

---

## Risk Register (High Level)

| Risk                              | Mitigation                                      |
|-----------------------------------|-------------------------------------------------|
| Media3 codec edge cases           | Extensive device matrix testing + fallbacks     |
| Scanner performance on large libs | Incremental scan + background WorkManager       |
| Arabic typography / RTL bugs      | Continuous testing on real devices + font tuning|
| Play Store policy changes         | Stay minimal on permissions & background work   |
| Scope creep                       | Strict phase gates + feature freeze windows     |

---

## Team Assumptions (Solo / Small Team)

This roadmap is realistic for a focused solo developer or small team (2–3 people) working full-time. Adjust timelines proportionally for part-time effort.
