# Shared Screen Patterns & Components

**App ID:** `com.exapps.velox`

This document defines reusable UI patterns used across multiple screens so implementation stays consistent.

---

## 1. Mini Player

**Height:** 68–72dp  
**Position:** Bottom of screen, above navigation bar if present  

**Anatomy:**
```
[ Artwork 48dp ]  Title (1 line)
                  Artist (1 line)     [ Play/Pause ]
```

- Background: `surface-glass` + top border `outline`
- Artwork: `radius-md`
- Tap anywhere (except button) → expand to Now Playing
- Swipe up → expand
- Optional swipe to dismiss / stop

**Motion:** Shared element artwork with Now Playing hero.

---

## 2. Bottom Sheets

Used for: Queue, Sleep Timer, Sort & Filter, Track selection, Create Playlist, Subtitle delay…

**Specs:**
- Corner: `radius-lg` / `radius-xl` top
- Background: elevated glass or `surface`
- Handle indicator centered
- Scrim: dark 40–50%
- Drag to dismiss
- Keyboard-aware when containing inputs

---

## 3. Context Menus

Long-press on media items:

Common actions:
- تشغيل (Play)
- تشغيل التالي (Play next)
- إضافة إلى قائمة انتظار (Add to queue)
- إضافة إلى قائمة تشغيل… (Add to playlist)
- مشاركة
- معلومات
- حذف من القائمة (when relevant)

Implementation: Material3 dropdown or custom glass menu.

---

## 4. Empty States

Standard structure:
1. Icon or simple illustration (monochrome / accent)
2. Title (bold)
3. Short description
4. Primary CTA button
5. Optional secondary text button

Examples already defined in Library, Playlists, Search.

---

## 5. Loading Patterns

- **Lists / Grids:** Shimmer skeletons matching real content shape
- **Player:** Thin indeterminate or circular near controls
- **Full screen:** Centered glass progress indicator
- Avoid blocking the whole UI when possible

---

## 6. Error Patterns

- Inline for lists (retry row)
- Overlay for player
- Snackbar for transient issues
- Always offer a clear next action (Retry, Open settings, Skip)

---

## 7. Dialogs

- Confirmation for destructive actions (Delete playlist, Clear history)
- Title + body + Cancel / Confirm
- Confirm button uses error color when destructive
- Prefer sheets over dialogs for complex content

---

## 8. Toast / Snackbar

- Used sparingly
- Bottom position, above Mini Player when present
- Short Arabic messages
- Optional action (“تراجع”)

---

## 9. Artwork Treatment Rules

| Context          | Size guidance     | Radius      | Notes                      |
|------------------|-------------------|-------------|----------------------------|
| Grid card        | Large             | radius-lg   | Hero of the card           |
| List leading     | 56–64dp           | radius-md   |                            |
| Mini Player      | 48dp              | radius-md   |                            |
| Now Playing hero | 280–320dp         | radius-xl   | Soft shadow / glow         |
| Notification     | System            | —           | High quality asset         |
| Placeholder      | Same size         | Same radius | Gradient + icon            |

Always prefer embedded art → folder art → generated placeholder.

---

## 10. Button Styles

| Variant     | Usage                          | Appearance                          |
|-------------|--------------------------------|-------------------------------------|
| Primary     | Main CTAs                      | Filled accent, rounded              |
| Secondary   | Secondary actions              | Glass / outline                     |
| Text        | Tertiary                       | Accent or on-surface text           |
| Icon        | Toolbars, player               | Tonal / glass circular              |
| Destructive | Delete confirmations           | Error color                         |

Min height 48dp for text buttons.

---

## 11. List Density

- Default comfortable (56–72dp rows)
- Optional compact mode in Settings later
- Touch targets never below 48dp

---

## 12. Scroll Behavior

- App bars: transparent → subtle glass/border on scroll
- Collapsing hero headers on detail screens (Album, Playlist)
- Overscroll: subtle glow or default system effect adapted to dark

---

## 13. Gesture Consistency

| Gesture              | Global meaning                     |
|----------------------|------------------------------------|
| Swipe down (player)  | Collapse / exit                    |
| Horizontal swipe     | Seek (video) or skip track (audio optional) |
| Long press           | Context menu or speed scrub        |
| Double tap           | ± seek (video)                     |
| Pull down            | Refresh (Library)                  |

Document exceptions per screen when needed.

---

## 14. Reduced Motion

When system “Reduce motion” is on (or app setting):
- Disable shared element complex moves
- Use simple fades
- No staggered list animations
- Keep essential feedback (press states)

---

## 15. Implementation Checklist (per new screen)

- [ ] RTL layout verified
- [ ] Arabic strings complete
- [ ] Empty / Loading / Error states
- [ ] Mini Player coexistence
- [ ] Accessibility labels
- [ ] Design tokens only (no hard-coded colors/spacing)
- [ ] Motion respects reduced-motion
- [ ] Edge-to-edge correct
