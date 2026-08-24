# Velox — Build Progress

Status: **Phase 1 (Core Player) implemented** on top of the Phase 0 foundation.
Everything in ROADMAP.md's M1–M4 is now in the codebase except the items listed
under "Deferred past this pass" below.

This file is the source of truth for "what's real vs. what's a placeholder" —
read it before assuming a screen is finished.

## Phase 0 checklist (master prompt)

| # | Item | Status |
|---|------|--------|
| 1 | Modular Gradle project, package `com.exapps.velox` | ✅ 13 modules, matches ARCHITECTURE.md's module graph exactly |
| 2 | `core:ui`: VeloxTheme, tokens, GlassCard, buttons, icon buttons | ✅ Color/Spacing/Shape/Type/Motion tokens transcribed from DESIGN_SYSTEM.md; GlassCard, 4 button variants, icon buttons, Empty/Loading/Error state components; runtime accent via `accentColor()` CompositionLocal |
| 3 | Force RTL + Arabic + English strings; verify layout direction | ✅ `supportsRtl="true"`, `locales_config.xml` (ar first), all strings duplicated in `values/` + `values-ar/`, verified key-parity. In-app language switch (Settings → Language) applies via attachBaseContext + recreate |
| 4 | Single-activity host + navigation skeleton | ✅ `MainActivity` + type-safe `VeloxNavHost` covering every screen in SCREENS_OVERVIEW.md |
| 5 | Integrate Media3; play a local file | ✅ Real ExoPlayer + MediaSession + MediaController wiring, not a stub |
| 6 | Room schema for the media library | ✅ MediaItem/Album/Artist/Playlist/PlaylistItem/PlayHistory entities + DAOs (Folder is a derived query, not its own table — see Deviations) |
| 7 | Basic media scanner (MediaStore) | ✅ `MediaStoreScanner` queries `MediaStore.Audio.Media` + `MediaStore.Video.Media`. Folder-walk for un-indexed paths: not done (see Deviations) |

## Phase 1 status by milestone (ROADMAP.md)

### M1 — Playback Core
- ✅ Video surface: `VeloxVideoSurface` (`:player:engine`) renders the session
  player via `PlayerView` with its own controller off; Compose chrome on top.
  Feature modules still never import media3 (engine-owned `VeloxResizeMode` enum).
- ✅ Gesture map (SCREEN_VIDEO_PLAYER.md §5): tap toggles controls, double-tap
  seek ±N s (configurable 5/10/15/30 in Settings; physical left = back per §12),
  horizontal drag = seek with live time-delta pill, vertical drag = brightness
  (left half) / volume (right half), long-press = temporary 2x speed, pinch =
  zoom 1–3x. Lock mode (§6) removes the gesture layer except the unlock button.
- ✅ Speed control: 0.25x–3x picker sheet; long-press boost; live chip label.
- ✅ Aspect ratio: cycles Fit / Fill / Zoom-crop (§8's full crop ladder —
  16:9 / 4:3 crops — collapsed into three modes for v1).
- ✅ Track selection: audio + text tracks exposed through
  `PlayerController.tracks` / `selectTrack` (domain-level `PlayerTrack` model).
- ✅ Background audio + notification: MediaSessionService-provided (Phase 0).
- ✅ PiP: manifest flag + top-bar button + auto-PiP on leave
  (MainActivity.onUserLeaveHint, gated by Settings → Playback → Auto PiP and
  "current item is video and playing").

### M2 — Library & Playlists
- ✅ Sort menu on Library (title / date added / duration / size / path) wired to
  `observeTracks/observeVideos(sortOrder)`.
- ✅ Videos tapped anywhere (Library tab, Search) open the Video Player route,
  not Now Playing.
- ✅ Playlists: create (FAB + dialog), play-all, shuffle, remove track,
  add-tracks picker sheet, per-playlist M3U export (SAF CreateDocument) and
  M3U import (SAF OpenDocument; content-URI entries resolve by id, plain paths
  resolve via MediaStore DATA lookup).
- ✅ System playlist details: Favorites / Recently Played / Most Played detail
  screens now show their live query contents (previously always empty).
- ✅ Favorites & Recently Played (Phase 0, unchanged).

### M3 — Player Experience
- ✅ Now Playing: transport, seek-on-release slider (single seek per gesture —
  no IPC flood while dragging), EQ button navigates to the Equalizer route.
- ✅ Queue sheet: current item highlighted, tap-to-play index, per-row remove,
  clear-all.
- ✅ Sleep timer sheet: off / end of track / 15 / 30 / 60 min.
- ✅ Equalizer: real `android.media.audiofx` chain (Equalizer + BassBoost +
  Virtualizer) attached to the ExoPlayer audio session through
  `AndroidAudioEffectsController` (`:player:engine`), exposed to features via the
  domain `AudioEffectsController` seam. Device band count is discovered at
  runtime; presets are defined on the canonical 10 frequencies and mapped by
  nearest frequency. Presets: Normal/Pop/Rock/Jazz/Classical/Bass/Vocal/
  Electronic + implicit User. Persistence: DataStore (canonical curve), applied
  on session attach, live updates while dragging.
- ✅ Subtitles (local): text-track selection + external subtitle side-loading
  (SRT/VTT/TTML/SSA via SAF picker → `PlayerController.addExternalSubtitle`,
  rendered by PlayerView's SubtitleView).

### M4 — Polish & Settings
- ✅ Full Settings screen: Appearance (Dark Glass / AMOLED + 6 accent swatches,
  applied live through `VeloxTheme(amoled=…, accent=…)`), Playback (resume
  position, seek interval, auto-PiP), Subtitles (size, position, auto-load —
  persisted; see Deferred for styling application), Language (System / العربية /
  English with recreate), Storage (clear playback history with confirm — wipes
  play_history + resets Recently/Most Played), About (version, replay intro).
- ✅ Onboarding: full 3-page pager (welcome, feature highlights, permission
  priming with real permission request + skip path).
- ✅ All new strings in both en and ar (key parity verified).

## Deferred past this pass (known, deliberate)

1. **Gapless + crossfade** (M3): needs a dual-player architecture decision;
   current single ExoPlayer with larger buffers is the Phase 0 stance.
2. **Subtitle styling application**: size/position/auto-load are persisted in
   Settings, but PlayerView's SubtitleView styling isn't fed from them yet —
   needs a custom subtitle view or PlayerView text output wiring.
3. **Genres tab**: not in SCREEN_HOME_LIBRARY.md's 5-tab spec; `LibraryGroup.GENRES`
   still maps to tracks (TODO(Phase 2) in LibraryViewModel).
4. **EQ scope selector** (§6 "apply to current media vs global"): global only.
5. **Playlist reordering UI**: repository/DAO support exists (`reorderTrack`),
   no drag-reorder interaction yet.
6. **Loudness enhancer** (SCREEN_EQUALIZER.md §5 "Loudness"): switch row not
   built; BassBoost + Virtualizer are.
7. **Shared-element artwork transition**, artwork collage on playlist cards,
   video thumbnails in lists (scanner still returns `artworkUri = null` for
   videos — swap in `loadThumbnail()` when picked up).
8. **Widgets, file association, baseline profiles** (M4 tail).
9. **Online subtitle search, lyrics, Chromecast** — Phase 1.1 by roadmap.

## Deviations from the docs (and why)

1. **Accent color: Teal, not the Stitch mockups' violet.** Same call as Phase 0
   (DESIGN_SYSTEM.md §2 + BRANDING.md §4 agree on `#2EE6A6`; mockups' `#6366F1`
   is Material-You-generated). Still worth confirming with the product owner.
   The Settings accent picker now includes the mockup-era Violet as a swatch,
   so the debate is user-resolvable in-app.

2. **No standalone `FolderEntity` table.** Folders derived via `GROUP BY
   folderPath` (Phase 0 decision, unchanged).

3. **No dedicated `feature:search` module.** Search lives in `feature:library`
   (TECHNICAL_PLAN.md's module list has no search module).

4. **Album art**: legacy `content://media/external/audio/albumart/{id}` URIs
   (Phase 0 decision, unchanged).

5. **Sorting**: only title is SQL-ordered; other sort orders sort in Kotlin over
   the query result (Phase 0 decision, unchanged; matters at multi-thousand-track
   scale).

6. **Bottom nav chrome height** is a fixed 140.dp (Phase 0 decision, unchanged).

7. **M3U import of plain paths** resolves through a live MediaStore DATA query
   rather than a stored path column — the schema deliberately doesn't persist raw
   filesystem paths. Unmatchable entries are skipped, not fatal.

8. **PlaylistDao.removeItem** still removes every occurrence of a track (Phase 0
   note, unchanged).

9. **Language switching** uses `attachBaseContext` + `recreate()` rather than
   AppCompat's `setApplicationLocales` — the app is ComponentActivity-based and
   TECHNICAL_PLAN.md doesn't mandate AppCompat. On API 33+ the system
   per-app-language page also works via `locales_config.xml`.

10. **EQ presets live in the domain** (`EqualizerPreset`) as curves on the
    canonical 10 frequencies rather than using the device's own preset slots —
    device preset lists vary wildly and don't survive band-count differences.

## Build configuration corrections (2026-08-23)

Verified against Google Maven / Maven Central metadata (web metadata only — no
local build was run, per instruction):

- `compileSdk`/`targetSdk` 37 → **36**: `platforms;android-37` is not published
  (build-tools 37 exists, the platform does not). Applied to all 13 modules.
- AGP `9.3.0` → `9.3.1` (latest stable patch of the same line).
- KSP `2.3.21-2.0.4` → **`2.3.11`**: the `<kotlin>-<ksp>` version scheme no
  longer exists; KSP publishes standalone 2.3.x versions that pair with Kotlin
  2.3.x. If sync rejects it, pick the newest 2.3.x from
  https://central.sonatype.com/artifact/com.google.devtools.ksp.gradle.plugin.
- Kotlin 2.3.21, Compose BOM 2026.08.00, Media3 1.11.0 confirmed published.
- Added `androidx.lifecycle:lifecycle-runtime-compose` to `:app`
  (`collectAsStateWithLifecycle` was used without the dependency — a Phase 0 bug).

## What hasn't been verified

**No local compilation was run (user instruction: never build locally).** Beyond
the Phase 0 caveats that still stand:

- The code was hand-checked against current stable API surfaces, and a full
  multi-file review pass was run (imports, cross-module contracts, Hilt graph,
  DAO signatures, string references, en/ar parity — several caught defects were
  fixed), but the first real Gradle sync in Android Studio remains the actual
  gate. Expect at most a handful of small resolution issues, not structural ones.
- The KSP 2.3.11 pairing is metadata-verified but untested by a build.
- No device/emulator run: gesture feel, audiofx behavior on real hardware,
  PiP aspect ratio, and RTL layouts are designed-in but unobserved.

## Recommended next step

Open in Android Studio, sync, and fix whatever the first build surfaces (likely
zero-to-few items). Then the highest-value follow-ups are: subtitle styling
application (Deferred #2), video thumbnails in lists (#7), playlist drag-reorder
(#5), and the gapless/crossfade architecture decision (#1).
