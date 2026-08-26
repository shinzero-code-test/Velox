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

---

## v0.3.0 — second device-feedback pass (10 bugs)

Everything below came out of the first real device run of v0.2.1. All fixes are
in this tree; none have been re-verified on hardware yet — treat this section
the same way the original Phase 1 notes were written: designed-in until a
device proves otherwise.

| # | Report | Root cause | Fix |
|---|--------|-----------|-----|
| 1 | Equalizer completely broken | Effects only exist once an audio session attaches; the restore lived in the EQ ViewModel, so pre-playback edits were lost or clobbered by a stale one-shot restore, and detached `setBandLevel` calls were dropped on the floor | `AndroidAudioEffectsController` now caches every desired value (incl. band levels), applies them at attach, rehydrates from DataStore on attach when the process has no fresher edits — and flushes those edits instead when it does. VM's one-shot restore removed |
| 2 | Progress bar always 100% | `durationMs` was only synced on item *transition* events; first track after `play()` never fires one, so duration stayed 0 and the slider clamped position to its coerced max | State sync now also runs on `STATE_READY` and `onTimelineChanged` |
| 3 | Elapsed/total time always 0 | Same missing-sync root cause as #2 (position ticked but duration stayed 0) | Same fix |
| 4 | No Now Playing from album/artist/folder/playlists | Those screens started playback without any navigation callback (library rows had one; the newer screens didn't) | `onMediaItemClick` threaded through `CollectionDetailScreen` + `PlaylistDetailScreen` (rows *and* play-all/shuffle) into `openMediaItem` |
| 5 | Sort worked on Songs only | Sort pipeline existed only in `observeTracks`/`observeVideos`; grouping tabs ignored it entirely | Albums/artists/folders now sort case-insensitively by their title key for every option (date/size/duration degrade to title — groupings carry no such fields) |
| 6 | Video audio kept playing after exiting the player | Nothing stopped playback on leave; the session player is built for background audio | Fullscreen player pauses on dispose unless the exit is a configuration change (rotation/recreate); PiP hand-offs don't dispose, so auto-PiP keeps playing |
| 7 | AMOLED still not applied | Screens paint over the XML theme's static `windowBackground`; Compose-side token swaps never reached the visible surface | Composition root in `MainActivity` now paints `VeloxColors.currentBackground` under everything |
| 8 | Video speed bled into songs; no song speed control | One shared player, speed set globally, never reset per media type; Now Playing had no speed UI | `play()` resets speed to 1x for non-video queues; Now Playing gains a cycling speed chip (1x→1.25x→1.5x→2x) |
| 9 | Subtitles / track selection not working | Two compounding causes: commands issued before the async `MediaController` connect were silently dropped (`controller ?: return`), and the wrong-thread crashes from the v0.2.0 report killed follow-up commands | Commands now wait up to 2.5 s for connect via `awaitController()` before executing (threading itself was fixed in v0.2.1) |
| 10 | Remember-position broken | Feature flag existed in Settings but nothing read/wrote positions anywhere | New `PlaybackPositionStore` port (DataStore impl, Hilt-bound): saved every ~5 s while playing + on pause/stop; restored on play() when the setting is on, guarded against trivial/near-end positions |

Also in this release: `versionCode` starts moving with releases (was stuck at 1).

---

## v0.4.0 — Phase 1 completion + Phase 1.1 (scoped)

Implements the remaining Phase 1 milestones that could be built without new
external infrastructure, plus the Phase 1.1 items that survived scoping.
**Explicitly out of scope this pass (user decision): Play Store release,
Chromecast, online subtitle search.**

### Phase 1 leftovers now done

- **M2 Genres view** — real this time (was a placeholder mapping to tracks):
  `genre` + `fileName` columns on `media_items` (**Room migration 1→2**, non-
  destructive), scanner reads `MediaStore.Audio.Media.GENRE` (API 30+; older
  devices just show an empty tab), DAO GROUP BY projection, repository/domain/
  ViewModel plumbing, Genres tab chip + list rows, and a GenreDetail route into
  the shared collection screen. Sort applies to genres like the other groupings.
- **M3 Subtitle styling** — the Settings → Subtitles knobs (scale %, bottom vs
  raised) finally reach PlayerView's SubtitleView; previously they persisted and
  did nothing.
- **M3 Gapless** — covered by Media3's setMediaItems/prepare pipeline (no action
  between same-format items). True crossfade remains deferred: it needs either a
  dual-player mixer or an AudioProcessor chain, both device-test-heavy.
- **M4 Widgets** — Glance now-playing widget (title/artist, prev/play-pause/
  next, tap-to-open). Same-process PlayerController state drives it reactively;
  transport actions go through Hilt entry-point ActionCallbacks. Artwork in the
  widget is deferred (needs a bitmap fetch/cache pipeline).
- **M4 File association** — MainActivity handles ACTION_VIEW content/file audio
  + video intents (fresh start *and* onNewIntent via singleTop): starts playback
  with a synthetic negative-id MediaItem and routes to the Now Playing chrome.
  Known limitation: fullscreen video route resolves through the library DB, so
  external videos surface Now Playing rather than the video screen for now.
- **M4 Accessibility basics** — playback slider carries a contentDescription;
  rows/buttons already met 40dp+ targets and labelled their states.

### Phase 1.1 items implemented

- **Lyrics display (basic)** — sidecar lookup next to each track: `Name.lrc`
  (synced, parsed incl. multiple timestamps per line + `[offset:]`) or `Name.txt`
  (plain). Now Playing grows a Lyrics toggle + panel with active-line highlight
  and auto-scroll. Embedded-tag lyrics are not read yet.
- **Improved artwork & thumbnail pipeline** — video rows in Tracks/Videos lists
  decode a frame from the media file itself (coil-video request-level decoder);
  audio keeps album-art URIs.
- **Tag editor (basic)** — "Edit info" on Now Playing edits title/artist
  (album shown read-only) as a library-level override. File tags are NOT
  rewritten (API 29+ ownership walls); overrides survive rescans via the new
  consolidated user-metadata snapshot/restore (which also guards favourites and
  play statistics).
- **Crash & ANR hardening** — uncaught exceptions persist to
  `filesDir/last_crash.txt` before handing off to the system handler; Settings →
  About shows a "Last crash" row (time) and shares the full trace.

### Still deferred (documented, deliberate)

- Chromecast, online subtitle search, Play Store release — excluded by decision.
- Crossfade DSP, embedded lyrics/tags writing, widget artwork, folder-walk
  scanner for un-indexed files.

---

## v1.0.0 — Phase 2 complete (minus Android Auto)

ROADMAP Phase 2 implemented end-to-end, except Android Auto (optional per
product decision) and the Play Store release step. Every feature below is a
full implementation wired into the app — no stubs.

### Network streams (HTTP / HLS / DASH / RTSP)
Media3's protocol modules (exoplayer-hls/dash/rtsp) are on the classpath so
DefaultMediaSourceFactory resolves them by URL; "Network" (Library toolbar) has
a URL field with recents (DataStore-persisted, capped at 10).

### Network browsing (SMB / FTP / WebDAV)
New `:core:network` module: `NetworkServer` records (DataStore JSON), blocking
protocol clients (jcifs-ng SMB incl. share listing + auth; commons-net FTP in
passive/binary mode with RETR restart offsets; minimal WebDAV over OkHttp with
namespace-agnostic PROPFIND parsing and ranged GETs), and canonical URL builders
(`smb:// ftp:// dav(s)://`). Playback rides a scheme-routing DataSource installed
in VeloxExoPlayerFactory — browsed files stream through the same session player,
queued exactly like local tracks. `:feature:network` provides server CRUD +
test-connect, directory browsing with back-stack, and tap-to-play.

### Advanced video processing
Decoder priority (auto vs software-first MediaCodec selector with fallback) from
Settings → Video processing; network-sized load buffers; resize modes and speed
already existed.

### A-B repeat · Bookmarks · Chapters
- A-B repeat: controller-enforced loop region (poll wraps to A past B), cycled
  OFF → A → A↔B from Now Playing; cleared when a new queue starts.
- Bookmarks: Room v3 migration adds a `bookmarks` table; add/jump/delete via the
  new Markers sheet.
- Chapters: sidecar `<track>.chapters.txt` (YouTube-style timestamp lines) shown
  read-only above your bookmarks in the same sheet (embedded-chapter extraction
  isn't exposed by this Media3 version — documented).

### Advanced sleep timer
Custom minutes, end-of-track, **end-of-queue**, and a 10-second volume fade-out
before stop (session volume ramps down then restores).

### Playback statistics & history
StatsDao aggregates over play_history: totals (plays / distinct tracks /
listening time), last-N-days activity, top-tracks leaderboard; StatisticsScreen
reachable from Settings → Data.

### Backup / restore
Single JSON document via SAF (CreateDocument/OpenDocument — no storage
permissions): settings, playlists merged by name, favourites, play history,
bookmarks, servers, recent streams. Restore re-applies only entries whose media
still exists in the library.

### Custom gesture configuration
Settings → Gestures: long-press 2× boost toggle, horizontal-seek toggle, and
vertical-drag mapping swap (brightness/volume sides) — honoured live by the
video surface's gesture detectors.

### Foldable / large screens
Now Playing caps its width (~720dp) and centres on expanded widths; album grid
switches from fixed 2 columns to adaptive 160dp cells (up to 4 across).

versionCode 7. Android Auto: deferred by product decision.

---

## v1.0.1 — launch crash fix (device-reported)

**Symptom:** instant `NetworkOnMainThreadException` crash on every app start —
`VeloxPlaybackService.onCreate` → Dagger builds `SmbClient` → jcifs-ng's
`BaseContext` constructor initialises the NetBIOS name-service cache, which
performs hostname lookups. StrictMode (correctly) kills it.

**Fix:** `baseContext` in `SmbClient` is now `by lazy` — jCIFS boots on first
actual SMB use, which runs on ExoPlayer's loader thread instead of main. FTP
and WebDAV clients were audited and confirmed offline at construction.

versionCode 8.
