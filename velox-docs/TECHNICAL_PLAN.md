# Velox Technical Plan

**App ID:** `com.exapps.velox`

---

## 1. Goals of This Plan

- Define concrete technology choices
- Establish coding standards and project structure
- Reduce decision fatigue during implementation
- Ensure Arabic-first and design-system compliance from the first commit

---

## 2. Platform & Minimum Requirements

| Item                    | Decision              |
|-------------------------|-----------------------|
| Primary platform        | Android               |
| Min SDK                 | 26 (Android 8.0)      |
| Target SDK              | Latest stable         |
| Language                | Kotlin 100%           |
| UI                      | Jetpack Compose       |
| Architecture            | MVVM + Clean          |
| Package                 | `com.exapps.velox`    |

---

## 3. Project Structure (Gradle Modules)

```
velox/
├── app/
├── core/
│   ├── common/
│   ├── ui/
│   ├── domain/
│   └── data/
├── feature/
│   ├── library/
│   ├── player/
│   ├── playlists/
│   ├── settings/
│   ├── equalizer/
│   └── subtitles/
├── player/
│   ├── engine/
│   └── service/
└── build-logic/          # optional convention plugins
```

**Dependency rule:**  
`app` → `feature:*` → `core:*`  
`feature` may depend on `player:engine` / `player:service` as needed.  
No circular dependencies.

---

## 4. Key Libraries (Locked for v1)

| Purpose              | Library                          | Version Policy      |
|----------------------|----------------------------------|---------------------|
| Compose BOM          | androidx.compose:bom             | Stable              |
| Media3               | androidx.media3:*                | Latest stable       |
| Hilt                 | dagger-hilt                      | Stable              |
| Navigation           | navigation-compose               | Stable              |
| Room                 | room                             | Stable              |
| DataStore            | datastore-preferences            | Stable              |
| Coil                 | coil-compose                     | Stable              |
| Coroutines           | kotlinx-coroutines               | Stable              |
| Serialization        | kotlinx-serialization (as needed)| Stable              |

Avoid unnecessary third-party UI libraries. Prefer custom Compose components aligned with the design system.

---

## 5. Player Implementation Strategy

### Engine
- Single source of truth: `PlayerController` (wraps `ExoPlayer` / `Media3`)
- Exposed via interface in domain or a shared player module
- Service (`PlayerService`) owns the player instance for background playback
- UI observes player state through Flows / MediaController

### Features mapping
| Feature              | Media3 / Custom solution                  |
|----------------------|-------------------------------------------|
| Local files          | ProgressiveMediaSource / Progressive      |
| Tracks               | TrackSelection parameters                 |
| Speed                | PlaybackParameters                        |
| EQ + effects         | Custom AudioProcessor chain + Equalizer API |
| Subtitles            | Text track support + external loaders     |
| Gapless              | ConcatenatingMediaSource / proper prep    |
| Crossfade            | Custom or future Media3 support           |

### Audio Focus & Session
- Use Media3 `MediaSession` + `MediaSessionService` (or legacy equivalent if needed)
- Proper audio focus requests and loss handling
- Notification via MediaStyle / Media3 notification

---

## 6. Library & Scanner Plan

1. **Initial scan**
   - MediaStore query for audio/video
   - Optional full filesystem walk for non-indexed folders (with user permission / SAF)
2. **Metadata**
   - MediaMetadataRetriever for quick data
   - Optional deeper tag reading later
3. **Artwork**
   - Embedded → cache
   - Folder.jpg / cover.jpg → cache
   - Coil disk + memory cache
4. **Database**
   - Room entities: MediaItem, Album, Artist, Playlist, PlaylistItem, Folder, PlayHistory
   - FTS for search if needed
5. **Updates**
   - WorkManager periodic + manual refresh
   - Diff-based updates to avoid full UI reloads

---

## 7. Arabic-First Technical Requirements

- `android:supportsRtl="true"`
- All layouts and Compose code written RTL-aware (use `start`/`end`, not `left`/`right`)
- Fonts loaded with Arabic subsets
- Date, time, number formatting via proper locale
- String resources: `values-ar` as primary quality target, `values` for English
- Test on Arabic locale + RTL force from day one
- Avoid hardcoded English in UI logic

---

## 8. Theming Implementation

- Custom `VeloxTheme` wrapping Material3
- Design tokens as Kotlin objects / CompositionLocals
- Dynamic accent color support prepared
- AMOLED pure black option (`background = #000000`)
- Edge-to-edge + transparent system bars
- Status / navigation bar icon appearance adapted to dark theme

---

## 9. Testing Strategy

| Level          | Scope                              | Tools                    |
|----------------|------------------------------------|--------------------------|
| Unit           | UseCases, mappers, utils           | JUnit, MockK             |
| Instrumentation| Database, basic UI flows           | Espresso / Compose tests |
| Manual         | Real device playback, gestures, RTL| Device matrix            |
| Performance    | Startup, scroll, memory            | Macrobenchmark, Profiler |

Priority devices for testing:
- Mid-range Samsung / Xiaomi / Oppo (common in Arabic markets)
- Pixel (reference)
- One high-refresh-rate device
- Low-end device for performance floor

---

## 10. Build & Release

- Product flavors (optional): `google`, `foss` later
- App Bundle (AAB) for Play Store
- Versioning: `major.minor.patch` (1.0.0)
- Signing: standard release keystore
- ProGuard / R8 rules for Media3, Hilt, etc.
- Baseline profiles for startup optimization

---

## 11. Privacy & Permissions

**Requested only when needed:**
- `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` (or legacy storage with max SDK handling)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS` (Android 13+)
- `WAKE_LOCK` (careful usage)
- Internet: only for optional features (subtitles, lyrics, streams)

No READ_PHONE_STATE, precise location, contacts, etc.

---

## 12. Coding Standards (Short)

- Kotlin official style + project .editorconfig
- Prefer `val` and immutability
- Explicit visibility
- Meaningful names (Arabic comments only if necessary; code in English)
- One primary responsibility per class
- ViewModels lean; logic in UseCases
- No God classes

---

## 13. Documentation & Knowledge

- This `/velox-docs` folder is the source of truth
- Keep architecture decision records (ADRs) for major choices later
- Inline KDoc for public APIs of core modules

---

## 14. Immediate Next Technical Steps

1. Create empty Android Studio project with package `com.exapps.velox`
2. Set up modular structure
3. Implement `core:ui` with VeloxTheme + basic glass components
4. Integrate Media3 and play a local file
5. Add Arabic strings + RTL verification
6. Room schema + simple scanner
7. First Now Playing screen matching design system
