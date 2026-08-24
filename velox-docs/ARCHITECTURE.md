# Velox Architecture

**App ID:** `com.exapps.velox`

---

## 1. High-Level Overview

Velox follows a clean, modular, layered architecture optimized for:

- Fast cold start
- Smooth UI (60/120 fps)
- Efficient media decoding & playback
- Strong separation between UI, domain, and data
- Easy addition of new media sources and renderers

```
┌─────────────────────────────────────────────────────────┐
│                     Presentation Layer                   │
│  Compose UI  •  Navigation  •  ViewModels  •  Effects   │
├─────────────────────────────────────────────────────────┤
│                       Domain Layer                       │
│  UseCases  •  Models  •  Repository Interfaces           │
├─────────────────────────────────────────────────────────┤
│                        Data Layer                        │
│  Local DB  •  Media Scanner  •  Network  •  Cache        │
├─────────────────────────────────────────────────────────┤
│                     Player Core Layer                    │
│  ExoPlayer / Media3  •  Codecs  •  Audio Focus  •  EQ    │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Recommended Tech Stack (Android First)

| Layer              | Technology                                      | Notes |
|--------------------|--------------------------------------------------|-------|
| UI                 | Jetpack Compose + Material 3 (customized)        | Full custom theming for glassmorphism |
| Architecture       | MVVM + Clean Architecture                        | Unidirectional data flow |
| DI                 | Hilt                                             | Compile-time safety |
| Navigation         | Navigation Compose                               | Type-safe routes |
| Async              | Kotlin Coroutines + Flow                         | Structured concurrency |
| Database           | Room + SQLCipher (optional encryption)           | Media library, playlists, history |
| Preferences        | DataStore                                        | Settings, theme, last played |
| Media Playback     | Media3 (ExoPlayer)                               | Primary engine |
| Media Session      | Media3 Session                                   | Notification, lockscreen, Android Auto |
| Image Loading      | Coil                                             | Artwork, blur, crossfade |
| Networking         | OkHttp + Retrofit (for future online features)   | Subtitles, lyrics, metadata |
| Background Work    | WorkManager                                      | Library scanning, cache cleanup |
| Analytics          | Optional, privacy-respecting (disabled by default) | |
| Crash Reporting    | Firebase Crashlytics or Sentry (opt-in)          | |

---

## 3. Module Structure

```
:app                          # Application entry, DI setup, navigation host
:core:common                  # Utilities, extensions, constants
:core:ui                      # Design system, components, theme, typography
:core:domain                  # Domain models, repository interfaces, use cases
:core:data                    # Repository implementations, Room, DataStore, scanners
:feature:library              # Media library, folders, artists, albums, genres
:feature:player               # Now Playing, mini player, queue, controls
:feature:playlists            # Playlists CRUD, smart playlists
:feature:settings             # All settings screens
:feature:equalizer            # Visual EQ + presets
:feature:subtitles            # Subtitle selection, styling, online search
:player:engine                # Media3 integration, custom renderers, audio effects
:player:service               # Foreground service, media session
```

---

## 4. Core Components

### 4.1 Player Engine
- Built on **Media3 ExoPlayer**
- Supports local files, network streams, HLS, DASH (future)
- Hardware decoding preferred, software fallback
- Custom `AudioProcessor` chain for equalizer, bass boost, virtualizer, reverb
- Precise seek, frame-accurate scrubbing where possible
- Multi-audio track & multi-subtitle support

### 4.2 Media Library
- Recursive folder scanner with ignore lists
- MediaStore integration + direct file system access
- Metadata extraction (MediaMetadataRetriever + optional TagLib)
- Artwork extraction & caching (embedded + folder.jpg / cover.jpg)
- Incremental scanning + WorkManager background refresh

### 4.3 Playback Service
- Foreground service with rich media notification
- MediaSession for lock screen, Bluetooth, Android Auto, Wear
- Audio focus handling + ducking
- Sleep timer, fade-out
- Gapless playback & crossfade options

### 4.4 UI Layer
- Single-activity architecture
- Compose-only UI
- Shared element transitions for artwork
- Predictive back support
- Edge-to-edge with dynamic system bars

---

## 5. Data Flow

```
User Action → ViewModel → UseCase → Repository → Data Source
                ↓
             StateFlow / SharedFlow
                ↓
             Compose UI (collectAsState)
```

- ViewModels expose immutable UI state
- Side effects (navigation, toasts, one-shot events) via SharedFlow or Channel
- Player state is observed from a singleton PlayerController / MediaSession

---

## 6. Key Design Decisions

| Decision                        | Rationale |
|--------------------------------|-----------|
| Media3 over custom native player | Mature, well-maintained, excellent Android integration |
| Compose over Views             | Faster iteration, better animations, consistent design system |
| Room for library               | Reliable querying, relations, migrations |
| Arabic-first from day 1        | RTL, typography, date/number formats built into foundation |
| No forced login                | Privacy & simplicity |
| Modular feature structure      | Independent development & potential dynamic delivery later |

---

## 7. Performance Targets

| Metric                        | Target          |
|-------------------------------|-----------------|
| Cold start to interactive     | < 800 ms        |
| Library scroll (10k items)    | 60 fps          |
| Seek latency                  | < 100 ms        |
| Memory (idle + library)       | < 180 MB        |
| APK size (initial)            | < 25 MB         |

---

## 8. Security & Privacy

- No cloud account required
- Optional encrypted local database
- Scoped storage compliant
- No unnecessary permissions
- Network only when user explicitly requests (subtitles, lyrics, streams)
- Clear data export / wipe options

---

## 9. Extensibility Points

- Plugin-style media source providers (future)
- Custom renderer factories
- Theme engine (user-created glass themes later)
- External subtitle providers
- Lyrics providers

---

## 10. Future Architecture Notes

- iOS version will share domain models & business logic via Kotlin Multiplatform (KMP) where practical
- Player core remains platform-specific (Media3 / AVPlayer)
- Design system tokens will be shared conceptually
