# Screen: Playlists

**Route:** `playlists`  
**Priority:** P0

---

## 1. Purpose

Create, manage, and play user and smart playlists.

---

## 2. Layout

```
┌──────────────────────────────────────┐
│ قوائم التشغيل              +  Search │
├──────────────────────────────────────┤
│                                      │
│  ┌──────────┐  ┌──────────┐          │
│  │ Artwork  │  │ Artwork  │   Grid   │
│  │ Playlist │  │ Playlist │          │
│  └──────────┘  └──────────┘          │
│                                      │
│  or List rows                        │
│                                      │
├──────────────────────────────────────┤
│ Mini Player                          │
└──────────────────────────────────────┘
```

---

## 3. Playlist Types

| Type              | Source                     | Editable |
|-------------------|----------------------------|----------|
| User playlists    | Created by user            | Yes      |
| Favorites         | Liked tracks               | Partial  |
| Recently Played   | System                     | No       |
| Most Played       | System                     | No       |
| Recently Added    | System                     | No       |
| Smart (future)    | Rules-based                | Rules    |

---

## 4. Playlist Card / Row

- Artwork: collage of first 4 tracks or custom image
- Title
- Track count + total duration
- Context menu: Play, Shuffle, Rename, Delete, Export M3U…

---

## 5. Create Playlist Flow

1. Tap FAB or “+”
2. Bottom sheet or dialog: name input (Arabic-friendly)
3. Optional: add tracks immediately or later
4. Confirm → appears in list with empty state art

---

## 6. Playlist Detail Screen

```
┌──────────────────────────────────────┐
│ ←  Playlist Name              ⋮      │
│                                      │
│     [Large Artwork / Collage]        │
│                                      │
│  Play All          Shuffle           │
│                                      │
│  1. Track title              3:45    │
│  2. Track title              4:12    │
│  ...                                 │
└──────────────────────────────────────┘
```

- Reorder by drag handle (edit mode)
- Swipe to remove
- Add tracks button
- Header shows total tracks + duration

---

## 7. Empty States

- No playlists: “أنشئ أول قائمة تشغيل” + CTA
- Empty playlist: “أضف أغاني أو فيديوهات” + Add button

---

## 8. Interactions

| Action                | Result                          |
|-----------------------|---------------------------------|
| Tap playlist          | Open detail                     |
| Play All              | Replace queue + play            |
| Shuffle               | Shuffle + play                  |
| Long press track      | Context menu                    |
| Drag in edit mode     | Reorder                         |

---

## 9. RTL & Motion

- Standard RTL list/grid behavior
- FAB position mirrors (start side in RTL)
- Shared element optional from card to detail header
- Reorder animation smooth
