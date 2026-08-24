# Screen: Settings

**Route:** `settings`  
**Priority:** P0

---

## 1. Purpose

Central place for all user preferences. Clear, grouped, Arabic-first.

---

## 2. Structure

Use a single scrollable list of sections (or master-detail on large screens).

### Sections (v1.0)

1. **المظهر** (Appearance)
2. **التشغيل** (Playback)
3. **المكتبة** (Library)
4. **الترجمة** (Subtitles)
5. **الصوت** (Audio / Equalizer defaults)
6. **الإشعارات** (Notifications)
7. **اللغة** (Language)
8. **التخزين** (Storage & Cache)
9. **حول** (About)

---

## 3. Appearance

| Setting                    | Type        | Notes                              |
|----------------------------|-------------|------------------------------------|
| Theme                      | Choice      | Dark Glass (default), AMOLED Black |
| Accent color               | Color picker| Teal + predefined options          |
| Artwork style              | Choice      | Rounded size preference            |
| Reduce motion              | Switch      | Honor system + app override        |

---

## 4. Playback

| Setting                    | Type        | Notes                              |
|----------------------------|-------------|------------------------------------|
| Resume playback            | Switch / choice | Remember position              |
| Default speed              | Slider / chips | 0.5x – 2.0x                     |
| Gapless playback           | Switch      |                                    |
| Crossfade                  | Switch + duration |                              |
| Background audio           | Switch      | Continue when screen off           |
| Auto PiP on leave          | Switch      | Video                              |
| Seek interval (double tap) | Choice      | 5 / 10 / 15 / 30 s                 |
| Hardware decoder           | Choice      | Auto / Force HW / Force SW         |

---

## 5. Library

| Setting                    | Type        | Notes                              |
|----------------------------|-------------|------------------------------------|
| Scan folders               | Navigation  | Manage included / excluded         |
| Auto scan                  | Switch      |                                    |
| Ignore short files         | Switch + threshold |                            |
| Group by                   | Info        | (mostly controlled in Library UI)  |

---

## 6. Subtitles

| Setting                    | Type        | Notes                              |
|----------------------------|-------------|------------------------------------|
| Preferred languages        | List        | Arabic, English…                   |
| Default size               | Slider      |                                    |
| Text color / background    | Color       |                                    |
| Auto-load external         | Switch      | Match by filename                  |
| Default position           | Choice      | Bottom / Top                       |

---

## 7. Language

- App language: العربية / English / System
- Immediate apply (recreate or dynamic)

---

## 8. Storage

- Cache size display
- Clear image cache
- Clear playback history (with confirm)
- Export playlists
- Optional: database backup (Phase 2)

---

## 9. About

- App version + build
- Open source licenses
- Privacy policy / terms links
- Rate app
- Contact / feedback

---

## 10. UI Patterns

- Preference rows: title + optional subtitle + trailing control
- Navigation rows: chevron
- Destructive actions: confirmation dialog
- Group headers with subtle separation
- Glass or standard surface list (prefer clean dark surface)

---

## 11. RTL

- Full RTL support
- Switches and trailing controls on the correct side
- Long Arabic titles wrap or ellipsize gracefully
