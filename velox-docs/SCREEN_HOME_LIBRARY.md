# Screen: Home / Library

**Route:** `library`  
**Priority:** P0  
**Primary users:** All personas

---

## 1. Purpose

Main entry point. Browse local media by folders, artists, albums, genres, or recent activity. Quick access to play anything.

---

## 2. Layout Structure (Mobile)

```
┌──────────────────────────────────────┐
│ Status bar (transparent)             │
├──────────────────────────────────────┤
│ [Logo/Title]          [Search] [More]│  ← Top app bar (glass or transparent)
├──────────────────────────────────────┤
│ [Tabs: المجلدات | الفنانون | الألبومات | ...] │  ← Scrollable tabs
├──────────────────────────────────────┤
│                                      │
│   Content area                       │
│   (Grid or List of media cards)      │
│                                      │
│                                      │
├──────────────────────────────────────┤
│ Mini Player (if active)              │
├──────────────────────────────────────┤
│ Bottom Nav (optional)                │
└──────────────────────────────────────┘
```

---

## 3. Top App Bar

- Title: **المكتبة** (or app name on root)
- Actions:
  - Search icon → Search screen
  - More (⋮) → Sort, View mode (Grid/List), Rescan, Settings shortcut
- Background: transparent or very subtle glass when scrolled
- Elevation: none; thin border or blur on scroll

---

## 4. Tabs / Segments

Suggested order (RTL → visual right-to-left):

1. المجلدات (Folders)
2. الفنانون (Artists)
3. الألبومات (Albums)
4. الأغاني / الفيديو (Tracks / Videos) — or separate Audio / Video
5. الأنواع (Genres)
6. مؤخرًا (Recent)

Alternative: Top-level switch between **صوت** (Audio) and **فيديو** (Video), then secondary tabs inside.

---

## 5. Content Variants

### 5.1 Grid View (default for Albums / Artists)
- 2 columns on phone (configurable density)
- Large rounded artwork (`radius-lg`)
- Title + subtitle under art
- Glass or flat card treatment
- Aspect: 1:1 for albums, slightly taller possible for folders

### 5.2 List View
- Leading artwork 56–64dp rounded
- Title (primary)
- Subtitle (artist / track count / path)
- Trailing duration or chevron
- Generous vertical padding (min 64dp row height)

### 5.3 Folders
- Hierarchical or flat with path
- Folder icon or first artwork collage
- Item count
- Tap → enter folder

---

## 6. Media Card Anatomy

```
┌─────────────────────┐
│                     │
│     Artwork         │  ← Large, radius-lg, subtle shadow/glow
│                     │
├─────────────────────┤
│ Title (1 line)      │
│ Subtitle (1 line)   │
└─────────────────────┘
```

- Press: scale 0.97 + light opacity change
- Long press: context menu (Play next, Add to playlist, Share, Info…)

---

## 7. Empty State

- Centered icon (music + video symbolic)
- Title: لا توجد ملفات بعد
- Body: أضف مجلدات أو اسمح بالوصول إلى الوسائط للبدء
- Primary CTA: إضافة مجلدات / السماح بالوصول
- Secondary: كيف يعمل؟

---

## 8. Loading State

- Shimmer placeholders matching grid/list structure
- Or centered subtle circular progress on glass

---

## 9. Interactions

| Action                    | Result                              |
|---------------------------|-------------------------------------|
| Tap item                  | Play (and go to Now Playing or stay)|
| Tap artwork (album)       | Open Album detail                   |
| Long press                | Context menu                        |
| Pull to refresh           | Trigger library rescan              |
| Scroll                    | Collapse/elevate app bar subtly     |
| Tab switch                | Crossfade or horizontal slide       |

---

## 10. Detail Sub-screens (from Library)

### Album Detail
- Large hero artwork at top (with gradient scrim)
- Album title + artist + year + duration
- Track list (numbered)
- Play All / Shuffle buttons (glass + accent)
- Shared element from grid card

### Artist Detail
- Artist image or placeholder
- List of albums + top tracks
- Play All

### Folder Detail
- Breadcrumb or back stack
- List/grid of contained media + subfolders

---

## 11. RTL & Arabic Notes

- Tabs scroll and order correctly in RTL
- Titles align start (right in Arabic)
- Grid flows right-to-left
- Long Arabic titles ellipsize correctly (end)

---

## 12. Motion

- Tab indicator smooth slide
- Grid items staggered fade-in on first load (subtle)
- Shared element to Album / Now Playing
- Pull-to-refresh custom or system style adapted to dark glass
