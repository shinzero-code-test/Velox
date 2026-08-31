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

---

## v1.1.0 — Phase 3 / Wave 1 (engine ↔ data decoupling)

The first deliverable from the Phase 3 plan: close the single remaining
architectural debt in the player stack (`tmp/review/deferred-backlog.md`
L6). The `:player:engine` module no longer imports `:core:data` directly;
EQ and decoder preferences now flow through two domain ports.

### What landed

- **New ports in `:core:domain`** (pure-Kotlin, no Android deps):
  - `DecoderPreferenceStore` — exposes the user's decoder preference
    as a boolean (`preferSoftware`) + a hot `StateFlow<Boolean>`. Has
    a synchronous `preferSoftwareCached()` accessor that backs the
    H5 fast-path the playback service uses on its main thread.
  - `EqualizerPreferencesStore` — exposes the canonical
    `EqualizerSettings` shape as a hot `StateFlow`, with a suspending
    `current()` one-shot read and a suspending `save()`.
- **`EqualizerSettings` moved** from `core.data.preferences` to
  `core.domain.player`. The storage class is unchanged; only the
  package moved. The `:core:data` `EqualizerPreferences` now imports
  it from the new location. The equalizer feature's ViewModel got its
  import line updated in the same pass.
- **Adapters in `:core:data`** that implement the ports over the
  existing DataStore-backed classes (no storage shape changes):
  - `UserSettingsPreferencesStoreAdapter` — translates the internal
    `DecoderPreference` enum to the boolean the engine wants, and
    re-implements the `primeCache` fast-path. Eagerly
    `stateIn`s the preference on the application scope so the first
    reader sees the primed value.
  - `EqualizerPreferencesStoreAdapter` — wraps
    `EqualizerPreferences.settings` flow in a hot `StateFlow` so the
    audio effects controller can read the most recent value at any
    time (not just while a flow collector is alive).
- **Hilt `@Binds`** in `DataModule.kt` (the existing
  `RepositoryModule` abstract class) wire the adapters behind the
  port interfaces. Both bindings are `@Singleton`.
- **`:player:engine` depends on `:core:domain` only.** The
  `implementation(project(":core:data"))` line is gone from
  `player/engine/build.gradle.kts`. A grep across the engine's
  Kotlin sources confirms zero `com.exapps.velox.core.data.*` imports
  remain.
- **New unit test** at
  `player/engine/src/test/.../AndroidAudioEffectsControllerPortTest.kt`
  exercises the port round-trip without standing up a real DataStore
  — the third trigger condition from the original L6 entry
  ("A test suite needs to mock the preference store without standing
  up the full DataStore dependency graph").

### What did *not* change

- No behaviour change for users. The same settings, the same
  persistence, the same audio-session attach timing.
- `:player:service` still depends on `:core:data` directly. Its
  `MediaControllerPlayerController` reads `userSettings.resumePlayback`
  inline, which is a *service-layer* concern (deciding whether to
  resume the last position) and was not what L6 was about. The
  service is allowed to know about app storage; the engine is not.
  This is documented in `tmp/review/deferred-backlog.md` under the
  new "Priority Z — Architectural purity" section.
- No new dependencies. The port interfaces use only `kotlinx.coroutines`
  (already on the classpath) and `:core:domain` was already
  pure-Kotlin.

### Why this is the right next move

The Phase 3 plan's milestones 4 (plugin architecture) and 2 (theme
engine) both touch the engine seam. Doing this refactor first means
those milestones can land without re-deriving the engine's storage
contract each time. It's also the smallest change in the plan (~200
lines of new code + a single build.gradle edit) and ships
behaviour-identical.

### Updated docs

- `tmp/review/deferred-backlog.md` — L6 entry now reads
  **FIXED in v1.1.0** with the trigger condition that materialised
  (test mocking).
- `velox-docs/` — no spec change. This was an internal
  re-architecture; the public surface (player behaviour, settings UI)
  is unchanged.

versionCode 17.

---

## v1.3.0 — Phase 3 / Wave 2 (theme engine + tablet layouts)

Two milestones from the Phase 3 plan landed together because they
share the design-system surface (`:core:ui`). The dependency on
`material3.adaptive` added here is reused by future Milestone 4 work
(plugins) when it touches the chrome.

### Milestone 2 — Theme engine / community themes

The design tokens are no longer compile-time constants. Themes are
data — a `ThemeDefinition` (JSON) — and a `ThemeRegistry` (domain
port) holds the active selection.

- **New in `:core:domain`** (pure-Kotlin):
  - `ThemeDefinition`, `LocalizedText`, `ThemeTokens` — the serialisable
    theme manifest. `SCHEMA_VERSION = 1`; mismatched versions fall
    back to the bundled default (no crash, no in-place migration yet).
  - `ThemeRegistry` — the active-theme port (`active: StateFlow`,
    `available()`, `setActive(id)`, `primeCache()`).
  - `kotlinx-serialization` added as a domain dependency (the schema
    is owned by the domain layer; data and UI both serialise through
    the same definition).
- **New in `:core:data`**:
  - `assets/themes/dark-glass.json` and `assets/themes/amoled-dark.json`
    — the two bundled themes, with ar + en names.
  - `ThemePreferences` — DataStore-backed active-theme persistence,
    bundled-asset enumeration, and `importFromUri()` for SAF-imported
    `.veloxtheme.json` files. User imports land in
    `filesDir/themes/{themeId}.json`; malformed files throw with a
    clear message.
  - `ThemeRegistryAdapter` — the default `ThemeRegistry` impl, with
    a hard-coded `DefaultDarkGlass` constant for the
    empty-assets-install edge case.
  - New Hilt `@Binds` for `ThemeRegistry` in `DataModule.kt`.
- **New in `:core:ui`**:
  - `ThemeSpec.kt` — `VeloxThemeSpec` (`@Immutable`) + `resolveThemeSpec()`
    (the function that combines a `ThemeDefinition` with the accent
    override and the AMOLED toggle). Malformed `#RRGGBB` strings fall
    back to the bundled default per token — a broken theme never
    crashes the app.
  - `Theme.kt` — the new `VeloxTheme(spec = ...)` overload is the
    preferred entry point; the legacy two-arg overload stays for
    non-Compose surfaces (the Glance widget uses its own
    `GlanceTheme`).
  - `glassSurfaceColor` / `glassOutlineColor` are now `@Composable`
    helpers that read the active spec (themes can override
    `glassAlpha`, etc.). Direct-alpha forms (`glassSurfaceColorAt`,
    `glassOutlineColorAt`) exist for callers that already have a
    `Float`.
  - `kotlinx-serialization` + `:core:domain` added as deps.
- **`AppViewModel` and `MainActivity`** now read a combined
  `themeSpec: StateFlow<VeloxThemeSpec>` (theme + accent + amoled)
  and pass it to `VeloxTheme(spec = themeSpec)`. The legacy
  two-property call site is gone.
- **`SettingsViewModel`** exposes `availableThemes` and `activeTheme`
  flows and gains `selectTheme(id)`, `refreshAvailableThemes()`, and
  `importTheme(uri)`. `SettingsScreen` renders a new theme picker
  section above the existing AMOLED/Accent rows, with a SAF "Import
  theme…" button and a localised error snackbar.
- **Strings parity:** new IDs `settings_theme_picker`,
  `settings_theme_picker_hint`, `settings_theme_import`,
  `settings_theme_import_failed` added in en + ar.
- **Tests:** `core/ui/src/test/.../ThemeSpecTest.kt` covers the
  resolver, the AMOLED override, malformed colors, `LocalizedText`
  locale fallbacks, and the `parseColorOr` hex parser.

### Milestone 3 — Better tablet layouts (partial — chrome only)

The plan called for two-/three-pane list-detail screens at
medium/expanded widths. This pass ships the chrome switch
(bottom-bar → side-rail) and the `WindowSizeClass` plumbing; the
per-screen list-detail refactor is the next pass and is intentionally
out of scope here.

- **`WindowSizeClassExt.kt`** in `:core:ui` — extension properties
  (`isCompact`, `isMedium`, `isExpanded`, `shouldUseNavRail`) that
  match the Material 3 defaults (600 / 840 dp cutoffs).
- **`MainScaffold`** is now width-aware. At Compact width it keeps
  the existing bottom bar; at Medium / Expanded it switches to a
  side rail with the mini player docked to the bottom. The rail is
  80 dp wide, glass-tinted, and renders the same four destinations
  as the bottom bar.
- **`MainActivity`** calls `calculateWindowSizeClass(this)` once and
  forwards the result to `VeloxNavHost` → `MainScaffold`.
- **`material3.adaptive` added** to the version catalog and the
  `:core:ui` build file.
- **No data-layer / service-layer changes** — the player stack
  doesn't care about the chrome.

### What did *not* change

- **Phone behaviour is byte-identical** to v1.1.0. The bottom bar
  and chrome measurement logic are untouched on the Compact path.
- **No list-detail refactor** for Library / Playlists / Search.
  That's the next pass; shipping a partial two-pane per screen is
  more work than the chrome switch and would touch every detail
  route. The rail switch alone gets the most obvious win (the
  chrome reads correctly on tablets).
- **Now Playing still uses the 720dp cap** from v1.0.0 (single-screen
  cap, not a global breakpoint). Switching it to a true two-pane
  Now-Playing-on-the-right layout is a Milestone 4 / 5 follow-up.

### Why this is the right next move

The plan's Milestone 4 (plugins) and 5 (intelligence) both depend
on a stable `:core:ui` contract. Doing the theme engine first means
plugins can carry their own bundled themes; doing the rail switch
first means the plugin-injected media-source pickers can also be
rail-friendly when they land.

versionCode 19.

---

## v1.4.0 — Phase 3 / Milestone 3 completion (per-screen list-detail)

The v1.3.0 chrome switch shipped only the bottom-bar → side-rail
transition. This release adds the per-screen two-pane list-detail
that Milestone 3 always called for.

### What landed

- **`CollectionKey` and its Saver** (`:feature:library`): a sealed
  type carrying `(kind, id, title)` for the four collection flavours
  the Library supports (Album / Artist / Folder / Genre). A
  `CollectionKeySaver` round-trips it through `rememberSaveable` so
  the two-pane selection survives a foldable hinge or rotation.
- **`CollectionDetailContent`** (`:feature:library`): the body of
  the existing `CollectionDetailScreen` extracted into a stateless
  composable that takes a `StateFlow<ScreenState<List<MediaItem>>>`
  and two callbacks. Both the route (`CollectionDetailScreen`) and
  the new in-place pane call this; the route keeps its own VM, the
  pane re-uses the parent's.
- **`LibraryViewModel.tracksFor(key)`** + **`onCollectionTrackClick(key, track)`**:
  the parent screen now resolves a `CollectionKey` to a cold
  `Flow<List<MediaItem>>` via the existing repository
  (`observeAlbumTracks` / `observeArtistTracks` /
  `observeFolderContents` / `observeGenreTracks`) without
  constructing a per-pane Hilt VM. Track clicks in the pane
  resolve the queue from the same flow.
- **`LibraryScreen` is now width-aware**:
  - Compact (phones): unchanged single-pane. Album/Artist/Folder/Genre
    clicks translate back to the existing `onAlbumClick` etc. nav
    callbacks, so the route-driven `CollectionDetailScreen` still
    works.
  - Medium / Expanded (≥ 600 dp): a new `LibraryTwoPane` lays out the
    grouping list on the leading half and the selected collection's
    tracks on the trailing half, separated by a 1-px glass
    outline divider. The selection is `rememberSaveable`d; switching
    tabs hides the detail pane if the previous selection's type no
    longer matches (e.g. an AlbumKey in the Artists tab).
- **`DefaultWindowSizeClass` moved to `:core:ui.layout`**: was
  duplicated between `MainScaffold` and the feature screens; now a
  single internal `val` lives next to the `isCompact` / `isMedium`
  helpers.
- **`material3-adaptive` added to `:feature:library`** so consumers
  of the public `LibraryScreen(windowSizeClass: ...)` parameter can
  see the type.
- **Strings parity:** new ID `library_two_pane_hint` added in en + ar.
- **Tests:** `CollectionKeyTest` covers the four `from(...)` factory
  methods and the `CollectionKeySaver` round-trip (including a
  folder path that contains slashes — the saver splits on `|`, not
  on the path separator).

### What did *not* change

- **Phone behaviour is byte-identical** to v1.3.0. The Compact path
  uses the same code path as the v1.3.0 single-pane (literally the
  same composable extracted to `LibrarySinglePane`).
- **The Now Playing 720dp cap** is still in effect. Splitting
  Now Playing into a two-pane layout (artwork on one side, controls
  on the other at medium/expanded widths) is a follow-up — the
  existing 720dp cap is a single-screen cap, not a global
  breakpoint, so splitting it is its own design decision.
- **Playlists two-pane** is deferred. The PlaylistDetailScreen
  has its own dialogs, FAB, and a tracks-bottom-sheet; refactoring
  it to a stateless content composable is more work than Library
  and belongs in its own pass.
- **Network browser two-pane** is deferred. The plan said "the
  existing back-stack becomes the 'directory' pane", which is a
  larger refactor (the current back-stack is the nav graph, not a
  pane).

### Why this is the right next move

The Library is the only screen where the user spends most of their
time (the Library tab is the home tab; Playlists/Search/Network are
auxiliary). Two-pane there is the highest-value tablet win. The
Playlists and Network two-panes can land later in the same shape
once their detail screens are extracted into a stateless content
composable.

### Updated docs

- `tmp/review/deferred-backlog.md` — no new entries; the L6 fix
  (Wave 1) and the chrome switch (Wave 2) are unchanged.
- `velox-docs/` — no spec change; the two-pane is a layout
  detail, not a behavioural one.

versionCode 20.

---

## v1.5.0 — Phase 3 / Wave 3 / Round 1 (Playlists two-pane + Plugins)

Three independent slices from the Wave 3 plan landed in this
release.

### Milestone 3 follow-up — Playlists two-pane

- **`PlaylistDetailContent`** (`:feature:playlists`): the body of
  the existing `PlaylistDetailScreen` extracted as a stateless
  composable. Takes a `PlaylistDetail?` (so the loading state is
  visible) and a small set of callbacks. Both the route screen
  and the new in-place pane re-use it.
- **`PlaylistsViewModel`** grew a small set of methods for the
  in-place pane (`playlistDetailFor`, `onPlaylistPlayAll`,
  `onPlaylistTrackClick`, `onPlaylistRemoveTrack`,
  `onPlaylistAddTracks`, `isSystemPlaylist`). These are thin
  wrappers around the existing repository; the route's own
  `PlaylistDetailViewModel` is unchanged.
- **`PlaylistsScreen`** is now width-aware. Compact uses the
  existing single-pane (taps navigate to `PlaylistDetail`).
  Medium/Expanded uses a `Row` with the playlist list on the
  leading pane and the selected playlist's tracks on the
  trailing pane, separated by a 1-px glass outline divider
  (mirroring the Library two-pane from v1.4.0). Selection is
  `rememberSaveable`d.
- **Strings parity:** `playlists_two_pane_hint` (en + ar).
- **What did *not* change:** the playlist "add tracks" bottom
  sheet and the M3U export sheet remain route-only flows. The
  pane has the same export-less / add-tracks-less surface as the
  route's "compact" variant; round 1.5 can add those if needed.

### Milestone 3 follow-up — Network browser two-pane

**Deferred.** The current `NetworkScreen` uses the navigation
back-stack for sub-directory navigation, which is structural. A
two-pane rewrite would re-architect the directory drill-down as
local state (a stack of `NetworkEntry` keys) inside the screen
and put the server list + the current directory side-by-side.
That refactor is its own PR — calling it out here so it's tracked
and not silently lost. Round 1.5 will pick it up.

### Milestone 4 — Plugin architecture

The new `MediaSourceProvider` SPI lands end-to-end. Built-in
SMB / FTP / WebDAV continue to use the existing
`NetworkClientRegistry` path; first-party and future APK-form
plugins use the new SPI.

- **New in `:core:domain` (`plugin` package):**
  - `MediaSourceProvider` — `id`, `displayName`,
    `supportedProtocols`, `listDirectory(url)`, `openStream(url, offset)`.
  - `MediaEntry` — directory vs file, MIME, size,
    last-modified.
  - `MediaStream` — `offset`, `totalSize`, `read(): InputStream`,
    `close()`.
  - `LocalizedPluginName` — `defaultName` + `ar`/`en` (mirrors the
    theme engine's `LocalizedText`).
  - `PluginRegistry` — `providerForScheme(scheme)` (the hot
    router path) and `available()` (the Settings surface).
- **New in `:core:data` (`plugin` package + `di`):**
  - `PluginRegistryAdapter` — the default `PluginRegistry`
    implementation; takes a `Set<MediaSourceProvider>` via Hilt
    multibinding and builds a `scheme → provider` map lazily.
  - `HttpUrlProvider` — the first-party plugin. Exists primarily
    to exercise the SPI in the MVP; it wraps OkHttp so any
    `http(s)://` URL goes through the plugin path. The existing
    default `DefaultDataSource.Factory` chain also handles those
    URLs, so this is a no-op for playback but a real provider
    for the registry.
  - `HttpUrlProviderModule` (Hilt) — `@Binds @IntoSet` so the
    provider lands in the multibound set.
  - `PluginModule` (Hilt) — binds `PluginRegistry` →
    `PluginRegistryAdapter`.
  - `okhttp` added to `:core:data` deps.
- **Engine routing extended:** `VeloxDataSourceFactory` injects
  the `PluginRegistry`. The `RoutingDataSource` now picks a
  `PluginStreamDataSource` for any scheme a plugin claims,
  alongside the existing SMB/FTP/WebDAV branch and the
  default-chain fallback. The plugin data source hands the
  `openStream` call through `runBlocking` on the loader thread
  (ExoPlayer's loader is purpose-built for blocking IO).
- **Settings → About → Plugins** — new `PluginsScreen` reachable
  from a new row in the About section. Lists every registered
  provider with id, localised display name, and supported
  protocols. Read-only in v1.5.0 (no enable/disable toggle).
- **New route:** `VeloxRoute.Plugins` + a `composable<>` block
  in `VeloxNavHost`.
- **Strings parity:** `settings_plugins_title`,
  `settings_plugins_protocols` (en + ar).
- **Tests:** `PluginRegistryAdapterTest` covers scheme lookup
  (case-insensitive, unknown → null), first-match-wins,
  `available()` ordering, and the `HttpUrlProvider` contract
  (advertises http/https; refuses `listDirectory`).

### What did *not* change

- **Built-in SMB/FTP/WebDAV** still goes through the existing
  `NetworkClientRegistry` and the credential-aware
  `NetworkStreamDataSource`. Wrapping those clients in
  `MediaSourceProvider` adapters is a Round 1.5 task.
- **APK-form plugin discovery** (Phase 3b in the plan) is
  deferred. The Hilt multibinding accepts a new provider via
  one `@Provides` line; an APK-intent-filter + signature-
  permission model lands in a later pass once a real third-party
  consumer is on the horizon.
- **The plugin data source uses `runBlocking`** to bridge the
  `suspend openStream` to ExoPlayer's blocking `open`. This is
  acceptable on the loader thread, but a future round can move
  the bridge into a coroutine that returns a `ListenableFuture`
  for true non-blocking IO.

### Why this is the right next move

The plugin SPI is the only Wave 3 milestone that creates a
new module surface (`MediaSourceProvider`) that the rest of Wave
3 will reach for. Milestones 5 and 6's audio-analysis module
will use the same Hilt wiring pattern; Milestone 7's recommender
will publish through a similar port. Land the SPI first, then
the rest of the wave can build on it without re-deriving the
shape.

### Updated docs

- `tmp/review/deferred-backlog.md` — no new entries; the
  plugin architecture is in addition to, not instead of, the
  existing deferred items.
- `velox-docs/adr/` — `0001-plugin-architecture.md` is planned
  for Round 1.5 alongside the credential-aware plugin adapters.

versionCode 21.

---

## v1.6.0 — Phase 3 / Wave 3 / Round 2 (silence / intro + auto chapters)

Milestones 5 and 6 from the Phase 3 plan landed together. They
share the new `:core:audio-analysis` module and the
silence/chapter Room tables; the silence detector drives the
auto-skip on play, the chapter detector writes rows that the
existing Markers sheet surfaces.

### New module — `:core:audio-analysis`

A pure-Kotlin (with one Android-`MediaCodec` decoder) module
that the player stack consumes through a Hilt-bound port. The
module has no Compose, no Media3 — it ships:
- `SilenceDetector` — RMS-based silence detection. 100 ms
  windows, -50 dBFS threshold, 2 s minimum run. Pure Kotlin,
  fully unit-tested.
- `ChapterDetector` — same pipeline at 50 ms resolution;
  adjacent-window RMS deltas ≥ 6 dB become chapter boundaries.
  Pure Kotlin, no tests yet (the chapter-detection quality
  bar is "user-visible noise" rather than contract-correctness).
- `AndroidPcmDecoder` — wraps `MediaExtractor` + `MediaCodec`
  to read a file's audio track to 16-bit signed little-endian
  PCM at 22 050 Hz mono. Cheap decimation, not a windowed-sinc
  resampler (good enough for envelope detection).
- `DefaultTrackAnalyzer` / `DefaultTrackAnalysisService` —
  facade that combines the two detectors and persists the
  results to Room. Binds `TrackAnalyzer` + `TrackAnalysisService`
  in `AudioAnalysisModule` (Hilt).
- Hilt wiring depends on `core:common` (for the application
  scope qualifier) and the existing `core:data` Room database.

### New tables — Room migration 3 → 4

- **`track_intro_outro`** — `(mediaItemId, kind)` primary key.
  `kind` is `0=INTRO, 1=OUTRO`. Cascade delete on the parent
  track. ~20k rows for a 10k library.
- **`track_chapters`** — `(mediaItemId, index)` primary key.
  Cascade delete. `autoGenerated` column distinguishes
  detector output from future sidecar / embedded import.

### Domain port

- `TrackAnalyzer` (in `:core:domain`) — the analyse call.
- `TrackAnalysisService` (in `:core:domain`) — the
  read-side: hot `Flow<List<IntroOutro>>` per mediaItemId,
  suspend `getIntroOutro(id, kind)`, fire-and-forget
  `scheduleFirstListenAnalysis(id, uri)`.
- `IntroOutro` / `Chapter` / `IntroOutroKind` value types.

### Player-stack integration

- `MediaControllerPlayerController` injects
  `TrackAnalysisService`. On every `play()`:
  1. If the current item is `MediaType.AUDIO` and the user
     has `intelligentSilenceEnabled` set in
     `UserSettings` (default ON), the controller calls
     `analysisService.scheduleFirstListenAnalysis(id, uri)`.
  2. After `play()`, it calls `applyIntroSkip(currentItem)`
     which looks up the saved intro row and, if the user is
     still near t=0 and the toggle is on, seeks to
     `intro.endMs`.
- A new `intelligentSilenceEnabled` field on `UserSettings`
  (with a setter, a `SettingsPayload` field for backup, and a
  `Settings` → `Playback` switch row) gates both the schedule
  and the auto-skip. The manual "skip intro" button on Now
  Playing is **not** gated — it's a deliberate override.

### Now Playing integration

- A new "↪ skip intro" button (icon: `Icons.Filled.FastForward`,
  content description `cd_skip_intro`) appears on the transport
  row when the current track has a saved intro row. Tapping
  seeks to `intro.endMs`.
- The Markers sheet's chapter list merges sidecar
  `.chapters.txt` (existing) and auto-generated chapters (new).
  Both are rendered through the same `ChaptersLoader.Chapter`
  shape; auto chapters show as "Chapter N".

### What did *not* change

- **No live PCM decode during initial scan.** Detection runs
  on the first play of a track, not during the library scan.
  This keeps scan time unchanged and means a never-played track
  has no analysis row.
- **The "Settings → Playback → Auto chapter generation" toggle
  is not added** — auto chapters are written speculatively. The
  v1.6.0 user-visible surface is "I see auto-chapter rows in
  the Markers sheet; I can delete them one at a time". A bulk
  delete + master toggle is round 2.5.
- **PCM decode downsamples** (cheap decimation) rather than
  windowed-sinc resampling. Good enough for the envelope-based
  detectors; a real resampler is a future round if a heavy
  chapter detector ever needs better signal fidelity.
- **`runBlocking` is not used** in the decoder path; the
  analysis runs on `Dispatchers.Default` inside the service
  via a coroutine launched on the application scope. The
  loader thread isn't touched.

### Tests

- `SilenceDetectorTest` — 4 contract tests:
  - Empty PCM → no intros.
  - All-loud PCM → no intros.
  - The canonical 2s-audio-then-4s-silence case produces an
    INTRO at ~2000–2200 ms (start) and ~5800–6200 ms (end),
    with ±200 ms tolerance for windowing jitter.
  - 1.5s of silence is below the 2s `minRunMs` threshold → no
    intro.
  - A silence run that starts past the 30s candidate window is
    rejected.

### Why this is the right next move

The silence/chapter pipeline is the first Wave 3 work that
creates a new background data flow (PCM-decode + analysis)
that needs a Hilt application-scoped coroutine to outlive any
one Activity. Landing it as its own module + Room migration
keeps the change focused, testable, and reversible — the
master toggle is round 2.5; the per-track skip button is the
escape hatch users have right now.

versionCode 22.

---

## v1.7.0 — Phase 3 / Wave 3 / Round 3 (on-device recommendations)

Milestone 7 from the Phase 3 plan landed as a small, auditable
collaborative filter. Pure addition — no new modules, no
schema migration, no engine changes.

### What's in v1.7.0

- **"Recommended for you" row** at the top of the Library tab
  (single-pane and two-pane). Renders only when the engine has
  at least one recommendation; the cold-start install with no
  play history shows the existing tab content without a banner.
- **Settings → Storage → "Reset recommendation data"** drops
  the in-memory co-occurrence matrix. The next emission of
  the recommendation flows rebuilds from play history. Play
  history itself is **not** deleted (the existing "Clear playback
  history" handles that).
- **Settings → About → privacy disclosure** explicitly notes
  that the recommendations engine runs on-device only.
- **`MediaLibraryRepositoryImpl.recordPlayed`** and
  **`clearPlayHistory`** notify the engine on every change so
  the next call to `forYou` / `upNext` / `becauseYouListened`
  re-emits.

### What did *not* change (deferred to round 3.5)

- **Now Playing → "Up next for you"** section in the queue
  sheet. The engine exposes `upNext()` as a hot flow; the
  queue sheet just needs to render it below the existing queue
  list, deduped against the current queue. UX-wise the user
  wants "don't play tracks I already have queued" + a clear
  visual separation. This is a screen-design call, not an
  engine call — round 3.5 picks it up.
- **Search → "Because you listened to X"**. The engine
  exposes `becauseYouListened(seedTrackId)`; the search screen
  needs a heuristic to detect when a query is "the title of a
  known track" and look up the seed. Same story — design
  call, round 3.5.
- **Per-day / per-week recompute** instead of on every play.
  For a 5k-row history the build is ~50ms; for a 50k-row
  history it's ~100ms. The plan's risk note about a 200ms
  threshold isn't hit on real libraries. A debounce is a
  future polish; eager invalidation is correct for round 1.

### Architecture

The engine lives in `:core:data` (it reads from Room) and is
exposed via a domain port in `:core:domain`
(`RecommendationEngine`). The port is bound by Hilt in
`DataModule` — same pattern as `ThemeRegistry` and the audio
analysis ports.

**Two pieces of state, both held in memory:**

1. **Co-occurrence matrix** — a sparse `Map<Pair<Long, Long>, Int>`
   keyed on the smaller-id-first track pair. Each play session
   (≤ 30 min between consecutive plays) contributes +1 to every
   pair within it. Bidirectional top-50 neighbours per track.
2. **Time-of-day × energy matrix** — a 4×4 matrix
   (`morning/afternoon/evening/night × energetic/mellow`)
   derived from the most recent 30 days of plays, normalised to
   sum to 1 per row. The energy heuristic is a round-1 stand-in
   (short track OR rock/electronic/hip-hop/metal/pop = energetic;
   everything else = mellow); a real classifier can replace it.

**Ranking** for `forYou()` is:
```
score(track) = Σ_heavy (cooccurrence(heavy, track) * weight)
            × (0.5 + timeOfDay.affinity(nowBucket, energy(track)))
```
plus a 10% "discovery injection" of random tracks the user
hasn't played recently. The 10% keeps the row diverse so a
user who only ever plays Arabic tracks still sees
recommendations from the rest of their library.

### Privacy

The plan's "data egress" risk is the biggest one for this
milestone. The mitigation: **the engine reads from
`MediaItemDao` and `PlayHistoryDao`; it never opens a
network socket.** The Settings → About disclosure makes that
explicit ("Recommendations are computed on this device from
your play history. No listening data is sent off-device.").
A future round that adds cloud-side recommendations (out of
scope for the Phase 3 plan) will need to add a per-provider
toggle to honour the user's choice.

### Tests

- **`RecommendationEngineImplTest`** covers the contract
  that's hardest to spot-check by hand:
  - Session boundary: two plays 10 min apart are in the same
    session; two plays 90 min apart are in different sessions.
  - Single-play and empty-history edge cases.
  - Cold start: `forYou` is empty when there's no play
    history (the engine never throws).

### Why this is the right next move

The Library row is the highest-leverage surface for the
recommender — it's the home tab, the user is most likely to
discover a track they didn't know they had. The Now Playing
and Search surfaces round 3.5 picks up are valuable but
smaller; the engine contract is in place, the UI is the
remaining work.

versionCode 23.

---

## v1.8.0 — Phase 3 / Wave 3 / Round 3.5 (clear-the-deck for Wave 4)

Five deferred items from the Phase 3 plan landed in this
release. The headline is the two recommender UI surfaces
(Now Playing "Up next" + Search "Because you listened to X") and
the SMB/FTP/WebDAV-as-`MediaSourceProvider` adapters; the
Network two-pane, the auto-chapter Settings toggle, and the
APK-form plugin discovery foundation round out the release.

### Milestone 7 follow-up — Now Playing "Up next for you"

- `NowPlayingViewModel.upNext: StateFlow<Recommendation.UpNext>` —
  combines the engine's `upNext()` with the current queue's id
  set, dedup'ing any track that's already queued.
- A new `UpNextSection` composable in the queue sheet renders
  up to 5 rows above the current queue. Each row has:
  - **Play next** (inserts at `currentIndex + 1` — the immediate
    successor)
  - **Add to queue** (appends at the end)
- The section is hidden when the engine returns an empty list
  (cold start, or the user has no play history).

### Milestone 7 follow-up — Search "Because you listened to X"

- `SearchViewModel.becauseYouListened: StateFlow<Recommendation.BecauseYouListened?>` —
  emits non-null only when the search has exactly one result
  AND the engine has at least one neighbour for that track.
- The search screen renders the recommendations below the
  single-result row with a small header. Tapping a
  recommendation row plays it as a one-track queue.
- Cold start and broad queries (>1 result) hide the section
  naturally — the engine doesn't return anything to surface.

### Milestone 4 follow-up — SMB/FTP/WebDAV as `MediaSourceProvider`

- Three new providers in `:core:network/plugin/`:
  - `SmbMediaSourceProvider` — wraps the existing `SmbClient`.
  - `FtpMediaSourceProvider` — wraps the existing `FtpClientHolder`.
  - `WebDavMediaSourceProvider` — wraps the existing `WebDavClient`.
- Each resolves the credential context via
  `NetworkLibraryRepository.findServerCached(url)` — same path
  the legacy `RoutingDataSource` uses, so behaviour matches the
  pre-plugin browsing/streaming surface exactly.
- Hilt-bound via `BuiltInNetworkProvidersModule` (`@IntoSet`),
  so the Settings → About → Plugins list now shows all four
  first-party providers (HTTP / SMB / FTP / WebDAV) instead of
  just the HTTP one.
- `:core:network` now depends on `:core:domain` (for the
  `MediaSourceProvider` port).

### Milestone 3 follow-up — Network browser two-pane

- `NetworkScreen` is now width-aware. At medium/expanded
  widths the server list sits on the leading pane and the
  directory browser on the trailing pane (separated by a 1-px
  glass outline). At compact width the existing single-pane
  behaviour is unchanged.
- The system back gesture: at compact width, back while
  browsing goes up one directory (the existing behaviour); at
  medium/expanded width, back always exits the screen because
  the panes are persistent.
- The `findActivity()` helper un-wraps the `ContextThemeWrapper`
  chain to get the `ComponentActivity` for
  `calculateWindowSizeClass` — same pattern `VeloxNavHost` uses.
- New string `network_two_pane_hint` (en + ar).

### Milestone 6 follow-up — Auto chapter Settings toggle + badge

- New `UserSettings.autoChapterGenerationEnabled` (default
  OFF). The Settings → Playback switch toggles it; the
  `BackupManager.SettingsPayload` carries the field for backup.
- `TrackAnalysisService.scheduleChapterOnlyAnalysis` —
  chapter-only path that skips the silence detector. Used by
  the player controller when only auto-chapter is on.
- The player controller reads both toggles at `play()` time
  and dispatches to the right call:
  - both on → `scheduleFirstListenAnalysis`
  - silence only → `scheduleFirstListenAnalysis` (existing)
  - chapters only → `scheduleChapterOnlyAnalysis`
  - both off → no analysis
- `ChaptersLoader.Chapter` now carries an `autoGenerated: Boolean`
  field; the Markers sheet renders an "auto" badge (en + ar)
  on auto-detected chapters. Sidecar `.chapters.txt` parses
  default to `false`.

### Milestone 4 follow-up — APK-form plugin discovery foundation

- New `PluginDiscovery` port in `:core:domain` — `discover():
  List<MediaSourceProvider>`. The interface lives in
  `:core:domain` so a real implementation (PackageManager +
  signature permission + DexClassLoader) can drop in without
  changing the registry or engine.
- `EmptyPluginDiscovery` in `:core:data` returns an empty list
  for v1.8.0. The host doesn't load any third-party APK today;
  the discovery returns no plugins, the Settings → About →
  Plugins list shows only the four first-party providers, and
  the round-1.5 APK loading lands when a real third-party
  consumer is on the horizon.
- `PluginRegistryAdapter` merges first-party (Hilt-bound) +
  third-party (discovery) providers, with first-party winning
  on id collision.

### Tests

- New `PluginRegistryAdapterTest` covers the merge + dedup
  contract: first-party wins on duplicate id; empty discovery
  returns the first-party list unchanged; `providerForScheme`
  is the hot first-party lookup (discovery doesn't appear in
  the hot path).

### What did *not* change

- **APK loading is still empty.** v1.8.0 ships the *interface*
  for `PluginDiscovery`; loading the actual APK classpath
  (PackageManager walk + signature permission + DexClassLoader)
  is a Round 1.5 / v2.x surface. The signature permission would
  need a new `<permission>` declaration, a manifest update, and
  DexClassLoader with proper ClassLoader isolation. None of
  that ships in v1.8.0.
- **Chapter deletion is still per-row.** The Markers sheet
  supports tapping a chapter to seek but doesn't have a
  "delete all auto chapters" affordance. That was a deferred
  item too; round 4 picks it up.
- **PCM decode downsampling is still cheap decimation.** A real
  resampler (windowed-sinc) is a future round; the envelope
  detector is satisfied with the cheap approach.

versionCode 25.

---

## v1.9.0 — Phase 3 / Wave 3 / Round 3.5e (the two remaining items)

The two deferred items from v1.8.0 — APK-form plugin loading
and chapter bulk-delete — both landed. This closes Phase 3.

### Milestone 4 follow-up — APK-form plugin loading

A real `PackageManagerPluginDiscovery` replaces the empty
v1.8.0 stub. The contract:

- **Host manifest** declares
  - a signature-level permission
    `com.exapps.velox.permission.PLUGIN_HOST` (only same-key
    APKs can hold it);
  - a `<queries>` element for the
    `com.exapps.velox.MEDIA_SOURCE_PROVIDER` action so Android
    11+'s package visibility doesn't hide the discovery.
- **Plugin APK manifest** declares
  - a `<service>` with the `MEDIA_SOURCE_PROVIDER` action;
  - a `<meta-data>` of the same name whose `android:value`
    names the FQCN of a `MediaSourceProvider` implementation;
  - a `<uses-permission>` for the host's signature permission.
- **Discovery walk**:
  - `PackageManager.queryIntentServices(intent, GET_META_DATA)`
  - for each match, `checkSignatures(host, plugin) ∈ {SIGNATURE_MATCH, SIGNATURE_FIRST_SAME_SIGNER}`
    and `checkPermission(PLUGIN_HOST_PERMISSION, plugin) == PERMISSION_GRANTED`
  - read the meta-data, instantiate via `PathClassLoader` rooted
    at the plugin APK's source dir
- The plugin runs **in the host process**. There is no
  per-plugin sandbox in v1.9.0. A future round can add
  per-process isolation via `android:process` + a remote
  binder; that work is tracked as the next-layer polish
  alongside the per-plugin enable/disable toggle.

The Settings → About → Plugins screen now shows a "Third-party
plugins" footer when the registry has only the four first-party
providers. The footer is the developer-facing manifest template
description; end users never see it.

The new constant surface (`ACTION_MEDIA_SOURCE_PROVIDER`,
`PLUGIN_HOST_PERMISSION`, `META_KEY`) is unit-tested in
`PackageManagerPluginDiscoveryContractTest`. The full
PackageManager walk needs an Android test harness; the contract
test guards the names that ship in the host's manifest.

### Milestone 6 follow-up — Chapter bulk-delete

- New `TrackAnalysisDao.clearAllAutoChapters(): Int` deletes
  every auto-generated chapter across all tracks. Sidecar /
  embedded chapters are unaffected (they aren't stored in
  this table).
- New `TrackAnalysisService.clearAllAutoChapters()` port method
  and `DefaultTrackAnalysisService` impl.
- `NowPlayingViewModel.onClearAllAutoChapters()` exposes the
  action.
- The Markers sheet renders a "Clear all auto-detected
  chapters" row (en + ar) below the chapters list, visible
  only when at least one chapter in the current view is
  auto-generated. Tapping the row deletes every auto chapter
  in the database.

### Architecture

The full plugin contract is documented in
`velox-docs/adr/0001-plugin-architecture.md`. The ADR covers
the security model, the manifest template, the loader bridge,
and the v2.x polish items (per-plugin process, per-plugin
permission grant, per-plugin enable/disable toggle).

### What did *not* change

- **Per-plugin enable/disable.** A plugin loaded through the
  registry is always active. A Settings toggle per plugin is
  the next-layer polish.
- **Per-plugin process isolation.** Plugins run in the host
  process. Same-signature APKs are trusted to that level; a
  third-party plugin from the Play Store wouldn't be loaded
  because it can't hold the signature permission.
- **Plugin manifest template in a real APK.** The v1.9.0 host
  ships the loader + the registry + the manifest declarations
  in the host. A reference plugin APK is not part of this
  repo (a third-party plugin would live in its own repo). The
  ADR has the manifest template; the user-facing footer in
  Settings → About → Plugins points at the README.

### Tests

- `PackageManagerPluginDiscoveryContractTest` guards the
  three-string contract (action, permission, meta-data key).
  Fails if any of them drift from the documented value.
- `PluginRegistryAdapterTest` (existing, v1.8.0) covers the
  first-party + discovery merge with first-party winning on
  id collision.

versionCode 26.

## v1.9.1 — hotfix: CI build failures (post-v1.9.0 ship)

`v1.9.0` shipped six files with compile errors that the local check could
not catch (no committed wrapper, AGP 9 / `compileSdk 37` + `platforms;android-37`
on the runner). Five separate error sites, all blocking `assembleRelease`:

### 1. `ThemePreferences.kt:60 / :143` — `/*` inside KDoc closes the comment

```kotlin
/**
 * User-imported themes from `filesDir/themes/*.veloxtheme.json`. Not
 * cached …
 * /
```

The `/*.veloxtheme.json` inside backticks contains the literal
characters `/*`. Kotlin's lexer does **not** respect backticks for
block-comment nesting — `/*` opens a nested comment that never closes.
The file then reports `Missing '}'` at the next method and `Unclosed
comment` at EOF, which cascades into "unresolved `ACTIVE_THEME_KEY` /
`DEFAULT_THEME_ID` / `loadBundledFromAssets`" (the companion object
after the unclosed comment is never parsed).

**Fix:** rewrite the KDoc line so it never contains `/*` — say
"`filesDir/themes/`" without the glob, and mention the `.json`
extension on the next line.

Cascaded into `ThemeRegistryAdapter.kt:58/59/65/71` — all four
"unresolved `imported` / `it` / `setActive` / `current`" diagnostics
were downstream of the same unclosed comment; the type of
`preferences: ThemePreferences` was an error type because the class
file could not be resolved.

### 2. `PackageManagerPluginDiscovery.kt:91 / :99` — wrong PackageManager APIs

- `pm.packageName` does not exist; the host package is
  `context.packageName`. Now `pm.checkSignatures(context.packageName, packageName)`.
- `PackageManager.SIGNATURE_FIRST_SAME_SIGNER` is not a constant in
  the current SDK — only `SIGNATURE_MATCH` / `SIGNATURE_NEITHER_SIGNED`
  / `SIGNATURE_UNKNOWN_PAGE` exist (the `SIGNATURE_FIRST_SAME_SIGNER`
  alias was removed with the `PackageManager` signature API cleanup).
  Gate now checks only `SIGNATURE_MATCH` — the strictest "same key"
  gate. Any other result rejects the plugin.

### 3. `EqualizerPreferences.kt:52` — smart-cast impossible across modules

```kotlin
if (settings.presetId == null) … else prefs[PRESET_KEY] = settings.presetId
```

`presetId` is declared in `:core:domain` (`EqualizerSettings` moved there
in v1.1.0 so `:player:engine` can depend on it without a `:core:data`
edge). Kotlin cannot smart-cast a `val` from another module because the
getter could be overridden. Capture in a local first:

```kotlin
val presetId = settings.presetId
if (presetId == null) … else prefs[PRESET_KEY] = presetId
```

### 4. `RecommendationEngineImpl.kt:130 / :187 / :213 / :393` — four sites

- `:130` `withLock` extension unresolved — missing
  `import kotlinx.coroutines.sync.withLock`. And `return` inside the
  `withLock` lambda is a non-local return (illegal for a suspend
  inline lambda); change to `return@withLock`.
- `:187` `todMatrix.add(tod, energy, 1)` — `add` takes `Double`, `1`
  is `Int`. Pass `1.0`.
- `:213 / :238 / :258 / :265 / :278` `mediaItemDao.getById / count /
  idAtOffset / getByIds` are `suspend` but were called from
  non-suspend `rankForYou` / `rankUpNext` / `randomDiscoveryPicks` /
  `lookup`. Make all four methods `suspend` — the call sites
  (`rebuildTrigger.map { }`) are already inside a `suspend` transform,
  so no caller change is needed.
- `:393 / :396` `MediaItemEntity.toDomain()` passed `artistId` and
  `mimeType`, neither exists on `MediaItemEntity` nor on the domain
  `MediaItem`. Replace with the real columns `folderPath`, `fileName`,
  `genre` which were missing from the mapping.

### 5. `NetworkScreen.kt:108 / :111` — experimental window-size-class API

```kotlin
val wsc = @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
          calculateWindowSizeClass(LocalContext.current.findActivity())
```

`@OptIn` is not applicable to an expression target, the API is still
experimental, and `findActivity()` returns `ComponentActivity?` (nullable)
while `calculateWindowSizeClass` needs a non-null `Activity`.

Move the opt-in to the composable function
`@kotlin.OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`, then

```kotlin
val activity = LocalContext.current.findActivity()
val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
val isCompactWidth = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact
                     || windowSizeClass == null
```

`null` (no activity — preview or detached context) falls back to
compact (single-pane), which is the safe default.

### 6. `core:audio-analysis` — KSP `error.NonExistentClass` / missing `core:common`

Two independent oversights in the new `:core:audio-analysis` module:

- `AudioAnalysisModule.kt:6` imported
  `com.exapps.velox.core.audioanalysis.TrackAnalyzer` (the same package)
  instead of `com.exapps.velox.core.domain.audio.TrackAnalyzer`. The
  class `DefaultTrackAnalyzer : TrackAnalyzer` correctly implements the
  **domain** port, but the Hilt module bound the wrong type — KSP
  reported `TrackAnalyzer could not be resolved`.

  **Fix:** `import com.exapps.velox.core.domain.audio.TrackAnalyzer`.

- `DefaultTrackAnalysisService.kt:31` injects
  `@ApplicationScope CoroutineScope` — that qualifier lives in
  `:core:common` (`core.common.di.ApplicationScope`), but
  `:core:audio-analysis`'s `build.gradle.kts` only declared
  `implementation(project(":core:domain"))` + `...(":core:data"))`.
  Because `":core:data"` exposes `":core:common"` as `implementation`
  (not `api`), the scope annotation was not on the classpath and KSP
  emitted `error.NonExistentClass` for the whole constructor.

  **Fix:** add `implementation(project(":core:common"))` to
  `core/audio-analysis/build.gradle.kts`.

### 7. `AudioAnalysisModule` — second import + `VeloxTypography.bodySmall` / `VerticalBandSlider`

Second layer that was hidden behind the earlier `kspReleaseKotlin` failure
(the first fix only corrected `TrackAnalyzer`; `TrackAnalysisService` was
the same wrong package and KSP still failed on the next run):

- `AudioAnalysisModule.kt:5` also imported
  `com.exapps.velox.core.audioanalysis.TrackAnalysisService` — the
  interface lives in `:core:domain`
  (`com.exapps.velox.core.domain.audio.TrackAnalysisService`).

  **Fix:** `import com.exapps.velox.core.domain.audio.TrackAnalysisService`.

- `PluginsScreen.kt:93/124` + `SettingsScreen.kt:193/638` reference
  `VeloxTheme.typography.bodySmall`, but `VeloxTypography` only defined
  `bodyLarge` / `bodyMedium` + the three `label*` slots. Material3 has
  `bodySmall` (12 sp) and the two screens were generated assuming it
  exists — hence `Unresolved reference 'bodySmall'`.

  **Fix:** add `bodySmall` to `VeloxTypography` (12 sp, Normal, 16 sp
  lineHeight) and wire it in `rememberVeloxTypography()` +
  `toMaterial3Typography()`. Keep the four call sites as-is.

- `VerticalBandSlider.kt:108` calls `glassSurfaceColor(elevated = true)`
  (a `@Composable`) inside `Canvas { }`'s `DrawScope` lambda, which is
  not a composable context — `e: @Composable invocations can only
  happen from the context of a @Composable function`.

  The slot already hoists `accentColor()` above the `Canvas` for the
  same reason. Hoist the glass color too:

  ```kotlin
  val accent = accentColor()
  val trackColor = glassSurfaceColor(elevated = true)
  // … drawRoundRect(color = trackColor)
  ```

### 8. `SilenceDetector.kt:65/71/86` — stale field + `Int`/`Long` mismatch

Third layer hidden behind the earlier `ksp`/compile failures:

- `:65` `.db` unresolved — `Window` is now `data class Window(val rmsDb: Double)` (renamed from the early draft's `db` to match `ChapterDetector.Window.rmsDb`), but the filter still read `windows[it].db`.

  **Fix:** `windows[it].rmsDb`.

- `:71 / :86` `minRunMs = 2_000` is `Int`, but both
  `findSilenceRunStartingInRange` and `findSilenceRunEndingInRange`
  take `minRunMs: Long`. Hence `Argument type mismatch: actual type is 'Int', but 'Long' was expected` at both call sites.

  **Fix:** `val minRunMs = 2_000L`.

versionCode 27.
