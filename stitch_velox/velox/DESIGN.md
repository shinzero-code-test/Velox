---
name: Velox
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#3a3939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c7c4d7'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#908fa0'
  outline-variant: '#464554'
  surface-tint: '#c0c1ff'
  primary: '#c0c1ff'
  on-primary: '#1000a9'
  primary-container: '#8083ff'
  on-primary-container: '#0d0096'
  inverse-primary: '#494bd6'
  secondary: '#cebdff'
  on-secondary: '#381385'
  secondary-container: '#4f319c'
  on-secondary-container: '#bea8ff'
  tertiary: '#ffb783'
  on-tertiary: '#4f2500'
  tertiary-container: '#d97721'
  on-tertiary-container: '#452000'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#e8ddff'
  secondary-fixed-dim: '#cebdff'
  on-secondary-fixed: '#21005e'
  on-secondary-fixed-variant: '#4f319c'
  tertiary-fixed: '#ffdcc5'
  tertiary-fixed-dim: '#ffb783'
  on-tertiary-fixed: '#301400'
  on-tertiary-fixed-variant: '#703700'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  display-lg:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: 0px
  headline-md:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: 0px
  body-lg:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
    letterSpacing: 0px
  body-md:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0px
  label-sm:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
  headline-lg-mobile:
    fontFamily: IBM Plex Sans Arabic
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  container-margin: 20px
  gutter: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
---

## Brand & Style
The design system for this media player focuses on an immersive, cinematic experience that feels "at rest" and high-performance. The brand personality is premium, fast, and elegant, specifically tailored for a modern Arabic-speaking audience.

The aesthetic utilizes **Dark Glassmorphism**. This involves deep, multi-layered backgrounds that provide a sense of infinite depth, contrasted by foreground elements that appear as "frosted glass." Visual priority is given to media content, while the UI serves as a sophisticated, translucent frame. Every interaction should feel fluid and lightweight, evoking an emotional response of calm focus and high-tech sophistication.

## Colors
The palette is rooted in a deep charcoal-black (`#0A0A0A`) to maximize the contrast of media artwork. The primary accent is a vibrant Electric Violet-Blue (`#6366F1`), used sparingly for high-action touchpoints and active states. 

The "Glass" effect is achieved through a specific layering technique:
- **Surface:** A very low-opacity white tint (`5%`) applied over the dark background.
- **Stroke:** A subtle highlight on the top and left edges (`12%` white) to simulate light catching the edge of a glass pane.
- **Glows:** Secondary accents use the primary color with high-diffusion blurs to indicate progress or active playback status.

## Typography
The system uses **IBM Plex Sans Arabic** to maintain a technical, clean, and modern feel. The font’s structure ensures that both Arabic and Latin characters harmonize perfectly within the same interface.

- **Headlines:** Use Bold weights to establish clear hierarchy over media artwork.
- **Body:** Regular weights with generous line-height ensure readability against translucent backgrounds.
- **RTL Support:** All typography components must default to Right-to-Left alignment, with punctuation and icons mirrored appropriately.
- **Labels:** Use Medium weights in all-caps (for Latin) or slightly tighter tracking to differentiate metadata (e.g., bitrate, file format) from content titles.

## Layout & Spacing
The design system employs a **Fluid Grid** model with a focus on safe margins for mobile reachability.

- **Margins:** A standard 20px outer margin ensures content doesn't hit the bezel of modern edge-to-edge displays.
- **Rhythm:** An 8px linear scale governs all spacing.
- **Stacking:** Components like "Now Playing" cards should use dynamic padding that expands when the player is minimized to a bottom bar or expanded to full-screen.
- **Mobile Considerations:** Primary controls (Play/Pause, Seek) are centered or bottom-weighted to accommodate one-handed use in a "thumb-zone" layout.

## Elevation & Depth
Depth is not communicated via shadows, but through **Backdrop Blur** and **Opacity Tiers**.

1.  **Level 0 (Base):** The #0A0A0A background or full-screen album art with a 40% black overlay.
2.  **Level 1 (Navigation/Lists):** 5% white glass tint with a 20px backdrop blur.
3.  **Level 2 (Modals/Popovers):** 10% white glass tint with a 40px backdrop blur and a 1px solid border at 15% opacity.
4.  **Floating Elements:** Interactive icons (Play/Pause) use a subtle radial glow of the primary color (`#6366F1`) behind the icon to create "lift" without traditional drop shadows.

## Shapes
The shape language is consistently **Rounded**, reflecting a premium, approachable tech feel. 

- **Containers:** Cards and player controls use a 16px (`rounded-lg`) radius.
- **Interactive Elements:** Smaller buttons or chips use an 8px (`rounded`) radius.
- **Media Art:** Album covers and video thumbnails must always match the container's roundedness to maintain the "encapsulated" glass look.
- **Progress Bars:** Seek bars should use fully rounded (pill-shaped) ends to feel soft and touch-friendly.

## Components
- **Buttons:** Primary buttons use a solid `#6366F1` fill. Secondary buttons use the glass style with a semi-transparent white border.
- **The Seek Bar:** A thin glass track with a vibrant `#6366F1` fill for the "progress" section. The handle (scrubber) should be a high-contrast white circle with a soft outer glow.
- **Glass Cards:** Used for library items. They feature a 1px top-stroke to catch "virtual light" and separate items in a list without needing heavy dividers.
- **Chips:** Small, pill-shaped glass elements for categories (e.g., "Recently Played," "High-Res").
- **Transport Controls:** Large, sleek icons with high-contrast white fills. The "Play" button is the only element that may use a subtle outer glow of the primary color to indicate it is the focal point.
- **Bottom Sheets:** For playlists or settings, these should use a high backdrop blur (30px+) to keep the underlying media visible but obscured, maintaining the sense of context.