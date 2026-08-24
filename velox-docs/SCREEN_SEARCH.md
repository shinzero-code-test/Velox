# Screen: Search

**Route:** `search`  
**Priority:** P0

---

## 1. Purpose

Fast, accurate search across titles, artists, albums, folders, and playlists. Arabic-aware.

---

## 2. Layout

```
┌──────────────────────────────────────┐
│ ←  [ Search field..................] │
├──────────────────────────────────────┤
│ Recent searches / Suggestions        │
│                                      │
│ Results                              │
│  • Tracks                            │
│  • Artists                           │
│  • Albums                            │
│  • Playlists                         │
│  • Folders                           │
└──────────────────────────────────────┘
```

---

## 3. Search Field

- Auto-focus on enter
- Placeholder: ابحث عن أغنية، فنان، ألبوم...
- Clear button when text present
- Optional voice input (system)
- Debounced query (300ms)

---

## 4. Results Structure

Grouped sections with sticky headers or simple section titles:

1. الأغاني / الملفات (Tracks)
2. الفنانون (Artists)
3. الألبومات (Albums)
4. قوائم التشغيل (Playlists)
5. المجلدات (Folders)

Each item:
- Leading artwork
- Title + subtitle
- Tap → play or open detail (context-dependent)

Show “See all” if a section is truncated.

---

## 5. Empty & Intermediate States

| State              | UI                                              |
|--------------------|-------------------------------------------------|
| Idle (no query)    | Recent searches + optional quick filters        |
| Typing / Loading   | Subtle progress or shimmer                      |
| No results         | “لا توجد نتائج لـ ‘…’” + suggestions           |
| Error              | Retry                                           |

---

## 6. Recent Searches

- Stored locally
- Tap to re-run
- Swipe or clear-all to remove
- Limited to last 10–15

---

## 7. Arabic Search Considerations

- Handle Arabic prefixes and common definite article (ال)
- Case and diacritic insensitive matching where possible
- Mixed Arabic/Latin metadata supported
- Results order: relevance then recency / play count

---

## 8. Interactions

- Tap track → play (optionally open Now Playing)
- Tap artist/album → detail screen
- Keyboard search action → execute
- Back → previous screen (preserve query optional)
