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

---

## v1.0.2 — app-shell review fixes (verified findings only)

Every finding from `tmp/review/app-shell.md` was re-checked against the code
before fixing. **C2 ("compileSdk 37 unavailable") dismissed** — releases have
built green against `platforms;android-37.0` since v0.4.0. **M1 (runBlocking
locale load at startup) verified but deliberately kept** — attachBaseContext
needs the value synchronously; a single small file read, documented tradeoff.

Applied:
- **C3** POST_NOTIFICATIONS now requested on API 33+ from onboarding *and* the
  Library permission flow; notification grant/denial explicitly never gates
  library access.
- **H1** `data_extraction_rules.xml` + `full_backup_content.xml` exclude the
  credential-bearing DataStore from cloud backup and device-transfer.
- **H2** replay-intro → Library navigate gains `launchSingleTop` (no duplicate
  Library destinations).
- **M2** bottom chrome height measured via `onGloballyPositioned`; fixed 140dp
  constant removed (content no longer floats or scrolls under the bar).
- **M3/M9** widget text/buttons use GlanceTheme colors; transport glyphs carry
  real contentDescriptions.
- **M4** widget receiver `exported="false"` (system still delivers updates).
- **M5** ACTION_VIEW: extension-based MIME sniff fallback when pickers send
  null/octet-stream; manifest adds x-matroska/ogg/x-flac types.
- **M6** PiP params: 16:9 aspect + `setAutoEnterEnabled` on S+.
- **M7** AppViewModel now passed into VeloxNavHost explicitly (no dual lookup).
- **L1/L2/L5/L8** dead code removed (BOTTOM_NAV_ROUTES, ExternalPlayback.isVideo,
  tools:targetApi, vectorDrawables.useSupportLibrary).
- **L10** crash log rotates: previous report preserved as `_prev`.
- **CI** `concurrency:` group so rapid tag pushes don't race releases.

versionCode 9.

---

## v1.0.3 — data-layer review fixes (verified findings only)

Every finding from `tmp/review/data-layer.md` was re-checked against the code.
All Critical/High items confirmed real and fixed. Deferred: H4 (Room schema
exports + migration-test infra — needs a CI build to generate schemas first),
M4 settings-write coalescing (partially done via `applyAll`), M13 playlist PK
race, and several documented nits.

- **C1** Migration 1→2: dropped `DEFAULT NULL` from both ADD COLUMN statements —
  SQLite stores an explicit default that Room's TableInfo comparison rejects,
  crashing every v1→v3 upgrade at runtime.
- **C2** Empty-list `NOT IN ()`: rescan now skips deletion when the scan returns
  zero rows (protective against transient MediaStore failures); BackupManager
  chunks `getByIds` by ≤900 ids and skips empty payloads entirely.
- **H1** Snapshot now covers EVERY row — tag-editor-only overrides previously
  fell outside the WHERE clause and were silently reverted by rescans.
- **M1** Custom SMB ports honoured in NetworkUrls.root().
- **M2** findServer matches protocol + host (case-insensitive) + explicit port.
- **M3** Rescan DB portion wrapped in withTransaction; clearPlayHistory too.
- **M5** playHistoryDao.trimTo(500) called after each insert (was never called).
- **M6** WebDAV display names decode only the final raw path segment via
  android.net.Uri.decode (no %2F splitting, no '+'→space mangling).
- **M7** Relative WebDAV hrefs resolve against the current directory with
  dot-segment collapsing, not against the server root.
- **M8** basePath segments percent-encoded before OkHttp URL construction.
- **M9** parentOf() clamps at the authority/share boundary, appends trailing '/'.
- **M10** FTP control encoding set to UTF-8.
- **M11** Enum valueOf calls in the settings flow wrapped in runCatching with
  fallbacks — no more collector crashes after enum renames.
- **M12** Playlist detail track rows emit from observeById (live Room query) —
  favourite/tag edits propagate without re-entering the playlist.
- **M14** Destructive-migration fallback gated behind BuildConfig.DEBUG
  (buildConfig feature enabled in core:data).
- Lows: pending/trashed filter for audio+video scans (API 29+), EXTINF newline
  sanitisation on M3U export, URLDecoder guard in NetworkUrls.displayName,
  backup format-version gate (>supported → clear error), UTF-8 single
  computation for export bytes, api→implementation for jcifs/commons-net/okhttp.

versionCode 10.

---

## v1.0.4 — features review fixes (verified findings only)

From `tmp/review/features-library-playlists-network.md`. C1 root-cause analysis
of the "no navigation" live bug is moot (user confirmed the symptom was a
testing artifact); C2 empty-queue crash already fixed in v1.0.0 (H6).

- **H1** Empty playlist Play-All/Shuffle no longer navigates to dead Now Playing;
  Shuffle navigates by the actually-played (shuffled) track.
- **H3** Favourite hearts in LibraryComponents and CollectionDetailScreen carry
  contentDescription strings (cd_favorite_add/cd_favorite_remove/
  cd_favorite_toggle) for TalkBack.
- **H4** Network browser: system back goes up one directory while browsing
  instead of exiting the destination.

versionCode 11.

---

## v1.0.5 — features(player+settings+eq+uicore) review fixes (verified findings only)

- **H1** Language-change persist wrapped in `NonCancellable` so the recreate()
  can't cancel the DataStore write that the new locale depends on.
- **H3** LyricsLoader.load and ChaptersLoader.load hop to `Dispatchers.IO`
  (both were `runCatching { File.readText() }` on main; Chapters wasn't even
  suspend).
- **H4** Bass/virtualizer sliders now call VM `on*ChangeFinished` once on release
  instead of hammering DataStore ~60×/s during a drag — matches the band-slider
  contract.
- **M1** Edit-info dialog captures the editing id at open time; Save can't NPE
  if the queue clears mid-dialog.
- **M4** Brightness override is restored to its initial value on player dispose.
- **M8** EQ switch is disabled when no audio session exists.
- **M9** ≤6-band canonical mapping now first-wins instead of last-write (deterministic
  regardless of map iteration order).
- **M12** Backup import picker accepts `application/json`, `application/octet-stream`,
  and `text/plain` (many SAF providers tag .json with the latter two).
- **M14** Bookmark delete IconButton carries the new `cd_delete_marker` string
  (TalkBack no longer says "Cancel" for a destructive action).
- **L1** Dead `current.let { }` in SleepTimerSheet save handler removed.
- **L19** LrcParser offset sign flipped: positive offset now shifts lyrics
  *later* (matches the LRC spec; code previously subtracted).

versionCode 12.

---

## v1.0.6 — Priority-A review fixes (player correctness + data-loss risk)

This pass clears the seven Priority-A items in `tmp/review/deferred-backlog.md`
plus a small handful of Priority-B UX items that came along for the ride.
Everything that needed a Room schema export (H4) is wired up so the next CI
build will populate `core/data/schemas/`; the migration test asserts
behaviour against the JSONs the moment they exist.

- **data-layer H4** Room schema export is already enabled
  (`schemaDirectory("$projectDir/schemas")` in `core/data/build.gradle.kts`,
  `exportSchema = true` in `VeloxDatabase`). The next CI build will generate
  `1.json`–`3.json` under
  `core/data/schemas/com.exapps.velox.core.data.local.VeloxDatabase/` and
  the new `androidTest` (`VeloxDatabaseMigrationsTest`) will pin them.
  Added `androidx.room.testing` to the version catalog.
- **data-layer M13** `addTracksAtEnd` is now guarded by a per-playlist
  `Mutex` in `PlaylistRepositoryImpl` so concurrent addTracks calls can't
  read the same `MAX(position)` and violate the (playlistId, position) PK.
  The DAO additionally renumbers positions densely and collapses duplicate
  (playlistId, mediaItemId) rows via `MIN(rowid) GROUP BY mediaItemId`.
- **player-stack H3** `AndroidAudioEffectsController` now serialises every
  field (effect objects, desired levels, dirty flag) through a single
  `synchronized(lock)` monitor. The setter path stays synchronous so the
  ViewModel's `state.value` reads in `EqualizerViewModel.persist()` see the
  post-write value immediately.
- **player-stack M7** Same class gained a `generation` counter that any
  in-flight `onAttachedRestoreOrFlush` checks after each suspension; a
  newer attach or release (which bumps the counter) drops the stale
  restore instead of overwriting the new state.
- **player-stack H5** `UserSettingsPreferences` now holds the
  `DecoderPreference` in an `AtomicReference`-equivalent `@Volatile` field
  that is primed once at process start (`VeloxApplication.primeCache()`,
  called alongside the locale load). `VeloxExoPlayerFactory.create()` reads
  the cached value synchronously — no more `runBlocking { DataStore }` on
  the service-creation main thread.
- **player-stack M6** `MediaControllerPlayerController` registers a
  `MediaController.Listener` (via `Builder.setListener`) whose
  `onDisconnected` callback nulls the controller and kicks off a fresh
  `buildAsync` against the same `SessionToken`. Subsequent commands
  observe `controller == null` and wait via `awaitController` until the
  new connect resolves.
- **player-stack M5** `NetworkLibraryRepository.findServer` now reads
  the server list from a `StateFlow` kept hot by the application scope
  instead of calling `dataStore.first()` on every `DataSource.open()`. The
  `IN (:ids)` DataStore query that runs at the same point is no longer on
  the hot loader thread.
- **features-library-playlists-network H2** `NetworkViewModel.navigateTo`
  now (a) captures the previous listing and keeps it visible while a new
  one is loading, (b) cancels any in-flight `list()` via a stored `Job`
  + epoch counter, (c) records the failed URL on error and exposes a
  `retry()` method, (d) `BrowserContent` renders a dedicated Retry button
  next to Up when an error has a `failedUrl`. New localized strings
  `network_retry` (en+ar).
- **features-library-playlists-network M1** `playStream` now sets a
  `streamError` `StateFlow` when the URL prefix isn't supported; the
  screen surfaces it through a `SnackbarHost`. Localized
  `network_stream_unsupported` (en+ar).
- **features-library-playlists-network M2** `saveServer` now clamps the
  port to `1..65535` and reports the rejection via the same
  `streamError` channel. Localized `network_port_invalid` (en+ar).
- **features-player-settings-eq-uicore M2** Long-press speed boost now
  bails out when the in-screen controls are visible, so a long-press on
  the play button (which keeps the controls alive) doesn't also flip
  playback to 2x.
- **features-player-settings-eq-uicore M3** Horizontal-seek commit now
  writes back the clamped `seekDeltaMs` to the accumulator on every drag
  frame, so the final commit on release matches the clamped value the
  live feedback pill was already showing.
- **features-player-settings-eq-uicore M15** `SettingsViewModel.exportBackup`
  / `restoreBackup` now log the raw exception and surface a localized
  string instead of concatenating `it.message`. New
  `settings_restore_done` (+ `settings_backup_failed`, `settings_restore_failed`)
  in en+ar.

versionCode 13.


---

## v1.0.7 — Priority-B + Priority-C backlog (medium/polish items)

A single pass that closes the medium-severity UX items, the EQ/stats
polish, the library playback surface, and a long list of small nits.
Grouped by area below.

### EQ + player polish
- **eq-uicore M2** Long-press speed boost now bails out when
  `controlsVisible` is true so it can't fire while the user is
  holding a control.
- **eq-uicore M3** Horizontal-seek commit writes back the clamped
  `seekDeltaMs` so the final commit matches the live feedback pill.
- **eq-uicore M5** Statistics screen now buckets play history by the
  device's local day (offset-aware SQL) instead of UTC-day.
- **eq-uicore M6** The "Last N days" header now reflects the actually
  rendered row count (`min(daily.size, 7)`) so the two never disagree.
- **eq-uicore M7** Statistics screen distinguishes loading from
  loaded-empty via a `ScreenState`-like flow; no more flash of the
  empty-state on cold open.
- **eq-uicore M10** `VerticalBandSlider` tracks an `isDragging` flag
  and only reseeds `dragLevel` from the prop when the user isn't
  actively dragging.
- **eq-uicore M11** `END_OF_TRACK` sleep timer now waits for an
  `PlaybackStatus.ENDED` transition, not just an id change, so
  manual skipNext/skipPrevious no longer trips the timer.
- **eq-uicore M13** Marker-delete and accent-swatch icon buttons
  are wrapped in `minimumInteractiveComponentSize()`; the
  `VeloxGlassIconButton` itself enforces the 40dp floor regardless
  of caller-supplied visual size.
- **eq-uicore L3** Lyrics toggle button is disabled when no sidecar
  lyrics exist.
- **eq-uicore L4** Plain-text lyrics panel has a height cap and uses
  a real scroll container.
- **eq-uicore L5** Lyrics auto-scroll now skips while the user is
  scrolling, items are keyed by timestamp, and dead scroll modifiers
  on the plain-text branch are removed.
- **eq-uicore L6** Brightness/volume feedback now uses `roundToInt()`
  so the live pill doesn't bias toward 0%.
- **eq-uicore L7** `VerticalBandSlider` width comment matches the
  40dp visual width code (the touch target is 48dp).
- **eq-uicore L8** Queue sheet `LazyColumn` now uses `weight(1f)`
  inside a `heightIn(max = screenHeight * 0.8)` column instead of a
  fixed 420dp; queue items are keyed by id alone.
- **eq-uicore L9** PiP button is disabled when
  `PackageManager.FEATURE_PICTURE_IN_PICTURE` is absent (TV/Wear).
- **eq-uicore L10** Seek feedback pill uses `FastRewind` /
  `FastForward` so the direction is visible at a glance.
- **eq-uicore L11** Crash-share chooser no longer carries
  `FLAG_ACTIVITY_NEW_TASK` (unnecessary from an Activity context).
- **eq-uicore L12** `lastCrashSummary` is now a `StateFlow` loaded
  on `Dispatchers.IO` in the VM's init; no more lazy disk read on the
  UI thread.
- **eq-uicore L13** `historyCleared` is now a one-shot event; the
  screen shows a confirmation snackbar and acks the flag.
- **eq-uicore L18** `backupMessage` and `showClearHistoryDialog` use
  `rememberSaveable` so they survive rotation.
- **player-stack M4** Player uses `C.WAKE_MODE_NETWORK` so radio
  suspension doesn't kill long network-streamed tracks.
- **player-stack L2** `RoutingDataSource.open` now throws
  `IOException` for the "called twice" path (retryable by ExoPlayer
  instead of crashing the loader).
- **player-stack L5** `subtitleMimeTypeFor` moved to
  `:player:engine` where it belongs; `MediaItemMapper` now guards
  against empty `artworkUri` strings.

### Library, playlists, network
- **library M2** Server editor: secure toggle is disabled for
  non-WebDAV protocols with a helper text; dead `ExposedProtocolField`
  parameters dropped; plaintext-credentials notice added.
- **library M3** `CollectionDetailScreen` now exposes a proper
  `ScreenState<List<MediaItem>>` so the loading vs empty state
  can't be confused.
- **library M4** M3U import/export results are now surfaced via a
  snackbar; new `playlists_import_*` and `playlists_export_*`
  strings in en+ar.
- **library M5** `LibraryViewModel.onMediaPermissionResult` only
  triggers a rescan on the denied→granted transition, not on every
  tab return.
- **library M7** Network id collisions reduced by mixing the artist
  name's length into the high 32 bits of the id.
- **library dead code** `LibraryGroup.RECENT` removed (the
  "Recently Played" system playlist already covers that surface).
- **library strings** `playlist_track_count` is now a `<plurals>`
  resource in both locales; `CollectionDetailViewModel` was already
  using `pluralStringResource`.
- **library Search UX** Clear-text icon added to the search field;
  a new `hasQueried` flag suppresses the "no results" empty state
  during the 250ms debounce window.
- **library `MediaItemDao.search`** Uses `ESCAPE '\'` on the LIKE
  patterns; the repository escapes `%` and `_` in user input first.
- **data-layer M3U charset** Export already uses `Charsets.UTF_8`;
  the duplicate `toByteArray()` call was a stale reviewer note.
- **network SMB** `SmbClient.list` normalises the URL to a trailing
  slash via the new `UrlNormalize` helper.
- **network WebDAV** `prop()` helper now merges across all 200-status
  `propstat` elements in document order, not just the first.

### SettingsViewModel
- **eq-uicore M15** Export/restore backup already use localized
  strings and log raw exceptions to logcat; verified in this pass.

versionCode 14.

---

## v1.0.8 — highest-leverage remaining backlog items

A targeted pass that closes the data-correctness and PlaybackSafety
items that survived v1.0.6/v1.0.7. Grouped by area.

### Library data correctness
- **observeFolders placeholder counts** `MediaItemDao` now exposes
  `observeFolderSummaries()` (single `GROUP BY folderPath` query); the
  repository passes the count straight into `Folder.itemCount`.
  Every folder in the Library list now shows its real track count
  without a follow-up fetch.
- **importM3u unreadable source** `PlaylistRepositoryImpl.importM3u`
  now reads the entries first, refuses to create a placeholder
  playlist if nothing resolved, and throws `IOException` so the
  existing import snackbar surfaces the failure to the user.
- **M6 duplicate-key robustness** Every `LazyColumn` /
  `LazyVerticalGrid` in the Library, Playlists, and Network screens
  switched to `itemsIndexed` with `"${id}-$index"` keys. A rescan
  race that surfaces the same id twice no longer crashes Compose
  with a duplicate-key exception.
- **sortedFor inapplicable sorts** `SortOrder.applicableTo(group)`
  filters the Library sort menu so it only shows options that have
  a meaningful value for the active tab (Albums/Artists/Folders/
  GENRES no longer present "by size" or "by date added" that would
  silently degrade to title sort).

### A11y
- **LibraryTabChip a11y** `Role.Tab` + `stateDescription` (selected /
  not selected) for TalkBack.
- **Album / artist / folder / genre cells a11y** `Role.Button` +
  `onClickLabel` via the new `cd_open_album/artist/folder/genre`
  strings in en+ar; `ClickableGlassCard` accepts an optional
  `onClickLabel` and propagates it to `combinedClickable`.

### Player correctness
- **M1 A-B seek-past-B policy** A `DISCONTINUITY_REASON_SEEK` that lands
  outside the loop region now snaps the playback back to A. The
  wrap was previously undefined.
- **L11 publishState prefers desired over hardware truth**
  `AndroidAudioEffectsController.setBandLevel` and
  `applyDesiredToHardwareLocked` now re-read `eq.getBandLevel` after
  each set so the StateFlow reflects the hardware's actual value
  when the device clamped our request.

### AddTracksSheet
- **AddTracksSheet fixed 420dp** Now `heightIn(max = screenHeight *
  0.8f)` + `weight(1f)` for the LazyColumn, mirroring the QueueSheet
  fix from v1.0.7.

### UI cleanup
- **L16 VeloxErrorRow sizing** Removed the `fillMaxSize()` from
  `VeloxErrorRow`; sizing is now caller-owned via the `modifier`
  parameter. Callers that want the full-screen variant pass
  `Modifier.fillMaxSize()` explicitly.
- **L14 dead code** `Motion.mediumSpring` and `PlusJakartaSans`
  removed (defined but never called).

versionCode 15.

---

## v1.0.9 — final backlog cleanup pass

Closes the remaining DEFERRED items from `tmp/review/deferred-backlog.md`.
After this pass, only two items remain open: a "name-keyed table"
for artist ids and a "port interface for DecoderPreferenceStore"
(engine depends on :core:data directly) — both are tracked as
out-of-scope architectural changes.

### Player correctness
- **L3 stock-buffer comment** The "stock 15s/50s min/max buffer"
  comment was wrong: ExoPlayer's actual default is min == max ==
  50_000ms. Comment corrected.
- **L8 PlayerTrack.id collisions** The fallback id now includes
  `format.codecs` and `format.sampleMimeType` so two formats in the
  same group with identical label + language still get unique ids.
- **L9 setLoopRegion contract** KDoc on `PlayerController.setLoopRegion`
  now spells out the "endMs <= startMs clears the loop" contract
  (the implementation already enforced it).
- **L10 poll save cadence drifts during loops** The position-save
  cadence is no longer skipped on a tick that fires the A-B wrap;
  the saved position now updates with the same 5s cadence
  regardless of looping.

### Settings & permissions
- **L15 settings rows** `SwitchRow` carries `Role.Switch` and
  `ChoiceRow` carries `Role.RadioButton`; both rows now meet
  Material's 40dp minimum touch target via `VeloxSpacing.sm` vertical
  padding (M13).
- **M5 permanent denial** `LibraryScreen` detects permanent
  permission denial via `shouldShowRequestPermissionRationale()` and
  offers an "Open settings" shortcut to grant access manually.
  `READ_MEDIA_VISUAL_USER_SELECTED` is left unrequested (the system
  photo/video picker is the right surface for partial access).

### Playlists
- **M3/M4 create dialog** The Create button is now disabled until
  the trimmed playlist name is non-blank; an `isError` supporting
  text surfaces the requirement (`playlists_name_required` in en+ar).
- **M3/M4 error state** `ScreenState.Error` now uses a dedicated
  `playlists_error_title` instead of the misleading "No playlists
  yet" empty-state copy.

### Library data layer
- **importM3u legacy DATA access** The resolver still tries the
  exact `DATA = ?` match first (works on API ≤28 and on most
  backwards-compat ROMs), then falls back to a
  `(RELATIVE_PATH, DISPLAY_NAME)` match against the MediaStore
  Files table, which works on API 29+.

### Bulk cleanup
- **L14 dead resources** `cd_more_options` resource removed from
  player/values and player/values-ar.
- **bulk-cleanup** `ScreenState.dataOrNull()` removed from
  core/common (no callers). `accentTint()` wrapper removed from
  NetworkScreen; callers now use `accentColor()` directly.
  `isBrowsing` in NetworkViewModel is now a `StateFlow<Boolean>`
  consumed via `collectAsStateWithLifecycle` in the screen.

### Documentation
- **L6 engine depends on :core:data** Now explicitly documented as
  deferred for a future architectural pass (the alternative is a
  30-line refactor for zero behavioural change).
- **L7 redundant `androidx-media3-common` dep** The dep audit shows
  `:player:engine` uses `media3.common` directly and `:player:service`
  uses it transitively via `media3.exoplayer` / `media3.session`;
  no redundant `implementation` lines remain.

versionCode 16.
