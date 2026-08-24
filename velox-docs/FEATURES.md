# Velox Features

**App ID:** `com.exapps.velox`

Features are organized by release phase and priority.

---

## 1. Core Playback (v1.0 – Must Have)

### Video Playback
- Local video files (MP4, MKV, AVI, MOV, WebM, TS, FLV, 3GP, etc.)
- Hardware & software decoding
- Multi-audio track selection
- Multi-subtitle track support (embedded + external)
- Aspect ratio controls (Fit, Fill, Stretch, Original, 16:9, 4:3, Crop)
- Playback speed (0.25x – 3.0x) with pitch correction option
- Frame-by-frame step (forward / backward)
- Precise scrubbing with preview thumbnails (where available)
- Brightness & volume gesture controls
- Lock orientation / lock controls
- Background audio (continue audio when screen off)
- Picture-in-Picture (PiP)

### Audio Playback
- Local audio (MP3, FLAC, AAC, OGG, WAV, Opus, M4A, ALAC, etc.)
- Gapless playback
- Crossfade (configurable duration)
- Sleep timer with fade-out
- Playback speed control
- ReplayGain support (where metadata exists)

### Common Controls
- Play / Pause / Next / Previous
- Seek bar with time display (elapsed / remaining / total)
- Shuffle & Repeat (Off / One / All)
- Queue management (add, remove, reorder, clear)
- Last played position resume

---

## 2. Media Library (v1.0)

- Automatic & manual library scanning
- Folder browser with breadcrumb navigation
- Grouping: Folders, Artists, Albums, Genres, Years, Playlists
- Smart filters & search (title, artist, album, filename)
- Sort options (name, date added, duration, size, path)
- Favorites / Liked tracks & videos
- Recently played & Most played
- Hidden folders / exclude list
- Artwork display (embedded + folder art)
- Grid & List views with density options

---

## 3. Playlists (v1.0)

- Create / rename / delete playlists
- Add to playlist from anywhere
- Reorder tracks
- Smart playlists (Recently Added, Most Played, Not Played in X days)
- Import / Export M3U / M3U8
- Playlist artwork

---

## 4. Now Playing & UI Experience (v1.0)

- Full-screen Now Playing with large artwork
- Mini player (persistent bottom bar)
- Swipe gestures on mini player
- Queue sheet
- Lyrics display (embedded + future online)
- Dark glass surfaces + blurred artwork backgrounds
- Smooth shared-element transitions
- Micro-animations on controls and state changes

---

## 5. Subtitles (v1.0 + enhancements)

- Embedded subtitles
- External subtitle files (SRT, ASS/SSA, VTT, SUB)
- Subtitle delay adjustment
- Font size, color, background, shadow, alignment
- Online subtitle search (OpenSubtitles or similar – Phase 1.1)
- Auto-load matching subtitle by filename

---

## 6. Audio Effects (v1.0)

- 10-band Equalizer
- Pre-built presets (Normal, Pop, Rock, Jazz, Classical, Bass Boost, Vocal, etc.)
- Custom user presets
- Bass Boost
- Virtualizer
- Loudness Enhancer
- Reverb (optional)
- Per-media or global EQ

---

## 7. Gestures & Controls (v1.0)

| Gesture                     | Action                          |
|----------------------------|---------------------------------|
| Vertical swipe (left side) | Brightness                      |
| Vertical swipe (right side)| Volume                          |
| Horizontal swipe           | Seek                            |
| Double tap left/right      | ±10s (configurable)             |
| Long press                 | Playback speed scrub            |
| Pinch                      | Zoom (video)                    |
| Single tap                 | Show / hide controls            |

---

## 8. Settings & Customization (v1.0)

- Theme: Dark Glass (default), pure black AMOLED option
- Accent color selection
- Default playback speed
- Resume behavior
- Scanner preferences
- Subtitle defaults
- Notification style
- Language (Arabic primary + English)
- Gesture sensitivity
- Hardware decoder preferences
- Clear cache / library rescan

---

## 9. System Integration (v1.0)

- Rich media notification with artwork
- Lock screen controls
- Bluetooth headset buttons
- Android Auto (basic)
- Home screen widgets (Play / Pause + Now Playing)
- File association (open with Velox)
- Share sheet support

---

## 10. Phase 1.1 – Early Enhancements

- Online subtitle search
- Basic lyrics fetch
- Chromecast / external display basic support
- Improved thumbnail generation
- Folder-based playlists (auto)
- Tag editor (basic)

---

## 11. Phase 2 – Power Features

- Network streams (HTTP, RTSP, HLS, DASH)
- SMB / FTP / WebDAV browsing
- Advanced video filters (sharpen, denoise, deinterlace)
- Custom gesture mapping
- Multi-window / freeform support
- Advanced sleep timer (end of track / playlist)
- Bookmark / chapter support
- A-B repeat
- Video snapshots
- Playback history with statistics
- Backup & restore settings + playlists

---

## 12. Phase 3 – Expansion

- iOS version
- Cloud playlist sync (optional account)
- Theme marketplace / user themes
- Plugin system for sources
- Android TV leanback interface
- Wear OS companion controls
- Advanced audio (spatial, head tracking later)
- AI-assisted features (auto chapters, smart silence skip – experimental)

---

## 13. Explicit Non-Goals (at least initially)

- Social features / sharing listening activity
- Forced user accounts
- Ads in the core player experience
- Cryptocurrency or web3 integrations
- Heavy social media integrations

---

## Feature Priority Matrix (v1.0)

| Feature                    | Priority | Complexity |
|---------------------------|----------|------------|
| Core local video/audio    | P0       | High       |
| Library + scanner         | P0       | High       |
| Now Playing + Mini player | P0       | Medium     |
| Playlists                 | P0       | Medium     |
| Equalizer                 | P0       | Medium     |
| Subtitles (local)         | P0       | Medium     |
| Gestures                  | P0       | Medium     |
| PiP                       | P1       | Low        |
| Widgets                   | P1       | Medium     |
| Online subtitles          | P1       | Medium     |
| Android Auto              | P2       | Medium     |
