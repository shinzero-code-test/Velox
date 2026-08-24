# Velox Design System

**App ID:** `com.exapps.velox`  
**Style:** Dark Glass • Large Artwork • Subtle Gradients • Rounded Cards • Smooth Micro-animations

---

## 1. Design Philosophy

Velox should feel like premium glass floating over rich media.

- **Dark first** — deep backgrounds that make artwork and content glow
- **Glassmorphism** — frosted surfaces with blur, soft borders, and translucency
- **Artwork as hero** — album art and video frames are large and celebrated
- **Restrained motion** — every animation has purpose and feels physical
- **Arabic-first clarity** — excellent typography, generous spacing, clear hierarchy in RTL

---

## 2. Color System

### 2.1 Core Palette

| Token                  | Light (future) | Dark (default)      | Usage                          |
|------------------------|----------------|---------------------|--------------------------------|
| `background`           | —              | `#0B0D10`           | App background                 |
| `surface`              | —              | `#12151A`           | Cards, sheets                  |
| `surface-glass`        | —              | `rgba(255,255,255,0.06)` | Glass panels              |
| `surface-glass-elevated`| —             | `rgba(255,255,255,0.09)` | Higher glass              |
| `on-background`        | —              | `#F2F4F7`           | Primary text                   |
| `on-surface`           | —              | `#E8EAED`           | Secondary text                 |
| `on-surface-variant`   | —              | `#9AA0A6`           | Tertiary / hints               |
| `outline`              | —              | `rgba(255,255,255,0.08)` | Borders                   |
| `outline-strong`       | —              | `rgba(255,255,255,0.14)` | Stronger borders          |

### 2.2 Accent Colors

Default accent: **Velox Teal**

| Token            | Value     | Notes                    |
|------------------|-----------|--------------------------|
| `accent`         | `#2EE6A6` | Primary actions, progress|
| `accent-muted`   | `#1A9B74` | Pressed / secondary      |
| `accent-container`| `rgba(46,230,166,0.15)` | Chips, highlights   |

User-selectable accents (future):
- Teal (default)
- Soft Blue
- Violet
- Amber
- Rose
- Emerald

### 2.3 Semantic Colors

| Token     | Value     |
|-----------|-----------|
| `error`   | `#FF6B6B` |
| `success` | `#2EE6A6` |
| `warning` | `#FFB020` |

### 2.4 Gradients

- **Artwork overlay** (bottom): `linear-gradient(to top, rgba(11,13,16,0.92) 0%, rgba(11,13,16,0.4) 45%, transparent 100%)`
- **Glass card subtle**: soft radial or linear from white 8% → transparent
- **Now Playing background**: blurred artwork + dark scrim

---

## 3. Typography

### 3.1 Font Stack

**Arabic (Primary):**
- **Display / Headlines:** IBM Plex Sans Arabic or Cairo / Tajawal (bold weights)
- **Body:** IBM Plex Sans Arabic or Noto Sans Arabic
- **Fallback:** System Arabic fonts

**Latin:**
- **Display:** Plus Jakarta Sans or SF Pro / Roboto
- **Body:** Inter or Roboto

### 3.2 Type Scale (Compose)

| Style              | Size | Weight    | Line Height | Usage                     |
|--------------------|------|-----------|-------------|---------------------------|
| `display-large`    | 34sp | Bold      | 40sp        | Now Playing title         |
| `display-medium`   | 28sp | Bold      | 34sp        | Section headers           |
| `headline-large`   | 24sp | SemiBold  | 30sp        | Screen titles             |
| `headline-medium`  | 20sp | SemiBold  | 26sp        | Card titles               |
| `title-large`      | 18sp | Medium    | 24sp        | List primary              |
| `title-medium`     | 16sp | Medium    | 22sp        | Secondary titles          |
| `body-large`       | 16sp | Regular   | 24sp        | Body text                 |
| `body-medium`      | 14sp | Regular   | 20sp        | Secondary body            |
| `label-large`      | 14sp | Medium    | 18sp        | Buttons, chips            |
| `label-medium`     | 12sp | Medium    | 16sp        | Captions                  |
| `label-small`      | 11sp | Medium    | 14sp        | Overlines, timestamps     |

**Arabic-specific adjustments:**
- Slightly increased line-height for Arabic
- Avoid overly condensed weights
- Prefer fonts with excellent Arabic coverage and modern proportions

---

## 4. Spacing & Layout

### 4.1 Spacing Scale

```
4, 8, 12, 16, 20, 24, 32, 40, 48, 64
```

### 4.2 Corner Radius

| Token            | Value  | Usage                        |
|------------------|--------|------------------------------|
| `radius-xs`      | 6dp    | Small chips, tags            |
| `radius-sm`      | 10dp   | Buttons, inputs              |
| `radius-md`      | 16dp   | Cards, list items            |
| `radius-lg`      | 24dp   | Large cards, bottom sheets   |
| `radius-xl`      | 32dp   | Hero cards, Now Playing art  |
| `radius-full`    | 999dp  | Pills, FABs, circular art    |

### 4.3 Elevation & Glass

Instead of heavy Material elevation, Velox uses:

- Translucent surfaces
- Thin light borders (`outline`)
- Backdrop blur (where performance allows)
- Soft ambient shadows only when necessary

---

## 5. Core Components

### 5.1 Glass Card
- Background: `surface-glass`
- Border: 1dp `outline`
- Corner: `radius-md` or `radius-lg`
- Optional backdrop blur
- Padding: 16–20dp

### 5.2 Media Card (Artwork)
- Large rounded artwork (`radius-lg` / `radius-xl`)
- Title + subtitle below or overlay
- Subtle gradient scrim when text is overlaid
- Press scale: 0.97

### 5.3 Mini Player
- Height: 64–72dp
- Glass background
- Artwork (rounded square)
- Title / artist (marquee if needed)
- Play/Pause + optional next
- Swipe up → expand to full Now Playing

### 5.4 Now Playing Screen
- Dominant large artwork (with soft shadow / glow)
- Blurred artwork as background
- Glass control cluster
- Large progress bar with accent
- Secondary controls in glass row

### 5.5 Buttons
- **Primary:** Filled accent, rounded (`radius-sm` / `radius-full`)
- **Secondary:** Glass / outline
- **Icon buttons:** Circular or rounded square, glass treatment on player

### 5.6 Lists
- Clean rows with generous touch targets (min 56dp)
- Leading artwork (rounded)
- Trailing duration or chevron
- Dividers very subtle or none (prefer spacing)

### 5.7 Bottom Sheets & Dialogs
- Glass / elevated surface
- Large corner radius
- Handle indicator
- Smooth spring animation

---

## 6. Motion System

### 6.1 Principles
- Purposeful, not decorative
- Physical feeling (spring / ease-out)
- Shared element transitions for artwork
- Respect reduced-motion preference

### 6.2 Durations

| Type              | Duration   |
|-------------------|------------|
| Micro (icons, press) | 120–180ms |
| Short (fade, small move) | 200–280ms |
| Medium (sheets, pages) | 320–400ms |
| Shared element    | 380–450ms  |

### 6.3 Easing
- Standard: `FastOutSlowIn`
- Enter: `Decelerate`
- Exit: `Accelerate`
- Spring: medium stiffness for player expansions

### 6.4 Key Micro-interactions
- Play/Pause icon morph
- Progress thumb scale on press
- Card press scale + opacity
- Mini player → full player shared art transition
- Queue item reorder (smooth)
- Like / favorite burst (subtle)

---

## 7. Iconography

- Outlined style preferred for consistency
- Weight matching text
- Accent color for active states
- Size scale: 20 / 24 / 28 / 32dp
- Custom player icons (play, pause, skip) slightly larger and bolder

---

## 8. Dark Glass Implementation Notes (Compose)

Recommended approach:

```kotlin
// Pseudo-structure
Box {
  // Blurred artwork or gradient background
  Image(... blur, contentScale = Crop)

  // Dark scrim
  Box(Modifier.background(Color.Black.copy(alpha = 0.55f)))

  // Glass surface
  Box(
    Modifier
      .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
      .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
  )
}
```

For true backdrop blur on Android: use `RenderEffect` / `Modifier.blur` carefully (performance cost) or approximate with pre-blurred images + overlays.

---

## 9. Accessibility

- Minimum contrast ratios respected even on glass
- Touch targets ≥ 48dp
- Content descriptions for all icons
- Support for system font scale
- Reduced motion support
- Screen reader logical order in RTL

---

## 10. Design Tokens Summary (Exportable)

All colors, radii, spacing, and type styles should live in a single source of truth (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`) and be consumed via `MaterialTheme` extensions or a custom `VeloxTheme`.
