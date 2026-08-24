# Velox Localization Strategy

**App ID:** `com.exapps.velox`  
**Principle:** Arabic-first, then expand.

---

## 1. Language Priority

| Priority | Language          | Status in v1     |
|----------|-------------------|------------------|
| 1        | Arabic (ar)       | Primary, complete|
| 2        | English (en)      | Full support     |
| 3+       | Other languages   | Phase 2+         |

Arabic is not a translation target — it is the original design language for UX writing, layout decisions, and typography.

---

## 2. RTL & Layout

- Application declared with `supportsRtl="true"`
- All Compose UI uses logical properties (`start`, `end`, `padding(horizontal)`, etc.)
- Mirrored icons where appropriate (e.g. back, forward, list arrows)
- Navigation and sheets respect RTL
- Test continuously with system language = Arabic and “Force RTL” enabled

---

## 3. Typography Strategy

- Primary Arabic font chosen for modern readability and good screen performance (e.g. IBM Plex Sans Arabic, Cairo, or Tajawal)
- Pair with a clean Latin font for English
- Avoid fonts with incomplete Arabic coverage or poor vowel mark handling
- Line height slightly increased for Arabic body text
- Test with real Arabic content that includes diacritics where relevant

---

## 4. String Management

- All user-facing strings in `strings.xml` (or Compose string resources)
- `values-ar` is the high-quality source of truth for tone
- `values` (English) maintained in parallel
- No hardcoded UI strings in Kotlin
- Plural rules handled correctly for Arabic
- Gender and formality kept consistent (modern, clear, approachable)

### Tone Guidelines (Arabic)
- Clear Modern Standard Arabic with natural phrasing
- Avoid overly classical or bureaucratic language
- Avoid heavy dialect unless specifically decided for a playful feature
- Keep technical terms consistent (decide once on “مشغل”, “قائمة تشغيل”, “معادل الصوت”, etc.)

---

## 5. Key Terminology (Initial Glossary)

| English              | Arabic (preferred)      |
|----------------------|-------------------------|
| Player               | المشغل / فيلوكس         |
| Library              | المكتبة                 |
| Playlists            | قوائم التشغيل           |
| Now Playing          | قيد التشغيل             |
| Equalizer            | معادل الصوت             |
| Subtitles            | الترجمة                 |
| Sleep Timer          | مؤقت النوم              |
| Queue                | قائمة الانتظار          |
| Favorites            | المفضلة                 |
| Settings             | الإعدادات               |
| Scan Library         | فحص المكتبة             |
| Playback Speed       | سرعة التشغيل            |
| Audio Tracks         | المسارات الصوتية        |
| Chapters             | الفصول                  |

(This glossary should be expanded and locked early.)

---

## 6. Date, Time, Numbers

- Use Android locale-aware formatting
- Respect user region for date order and calendar where relevant
- Duration always shown in a clear, consistent style (e.g. `1:23:45` or localized equivalent)

---

## 7. Content & Metadata

- Display media titles, artist names, album names as stored (no forced translation)
- Prefer correct shaping and bidi handling for mixed Arabic/Latin metadata
- Search should handle Arabic prefixes and common variations where feasible

---

## 8. Future Languages

When adding new languages:
1. Professional or high-quality native translation
2. RTL / LTR verification
3. Font fallback testing
4. Screenshot and marketing asset updates
5. Keep Arabic and English as the reference quality bar

---

## 9. Testing Checklist (Localization)

- [ ] Full Arabic UI walkthrough
- [ ] Full English UI walkthrough
- [ ] RTL layout integrity (no clipped text, correct mirroring)
- [ ] Long Arabic strings (no overflow)
- [ ] Mixed Arabic + English metadata
- [ ] Plural forms
- [ ] System font scale large
- [ ] TalkBack / accessibility order in RTL

---

## 10. Implementation Notes

- Use `LocaleList` and per-app language override (Android 13+ and backward compatible approaches)
- Allow user to force Arabic or English inside the app regardless of system language
- Keep design system components language-agnostic; only content and some icons change
