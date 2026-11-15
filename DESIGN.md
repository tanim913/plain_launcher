---
name: Plain Launcher
description: The landing page as the first post of an Android release thread. Ads gone, nothing lost.
colors:
  green: "#15703F"
  green-deep: "#0F5A32"
  green-wash: "#E3F0E8"
  green-rule: "#BFDCC9"
  graphite: "#1B1D20"
  graphite-2: "#2A2D31"
  silver: "#B9BEC4"
  graphite-dim: "#8E949B"
  ground: "#E7E8EA"
  paper: "#FBFBFC"
  paper-sunk: "#F2F3F5"
  paper-bright: "#FFFFFF"
  rule: "#CDD0D4"
  rule-soft: "#E4E6E9"
  ink: "#1B1D20"
  muted: "#4A4F56"
typography:
  display:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "clamp(3rem, 6vw, 5rem)"
    fontWeight: 880
    lineHeight: 0.9
    letterSpacing: "-0.035em"
    fontVariation: "\"wdth\" 112"
  headline:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "clamp(1.55rem, 2.6vw, 2.25rem)"
    fontWeight: 820
    lineHeight: 1.14
    letterSpacing: "-0.02em"
    fontVariation: "\"wdth\" 96"
  title:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "clamp(1.45rem, 2.2vw, 1.9rem)"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  title-sm:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "1.35rem"
    fontWeight: 780
    letterSpacing: "-0.01em"
  numeral:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "2.6rem"
    fontWeight: 880
    lineHeight: 0.9
    fontFeature: "\"tnum\" 1"
    fontVariation: "\"wdth\" 112"
  body:
    fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Noto Sans, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.6
  body-lead:
    fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Noto Sans, sans-serif"
    fontSize: "1.16rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "0.82rem"
    fontWeight: 600
  label-badge:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 750
    letterSpacing: "0.04em"
  meta:
    fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Noto Sans, sans-serif"
    fontSize: "0.86rem"
    fontWeight: 400
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
    fontSize: "0.92em"
rounded:
  focus: "2px"
  chip: "3px"
  base: "4px"
  panel: "6px"
  screen: "10px"
  avatar: "20px"
spacing:
  gap-chip: "8px"
  gap-list: "14px"
  post-gap: "22px"
  gutter: "24px"
  gutter-mobile: "12px"
  post-pad-x: "40px"
  post-pad-x-mobile: "16px"
  author-column: "196px"
  bar-height: "60px"
  bar-height-mobile: "56px"
  container: "1200px"
  measure: "68ch"
components:
  thread-bar:
    backgroundColor: "{colors.graphite}"
    textColor: "{colors.silver}"
    height: "{spacing.bar-height}"
  jump-link:
    textColor: "{colors.silver}"
    typography: "{typography.label}"
    rounded: "{rounded.base}"
    padding: "7px 11px"
  jump-link-hover:
    backgroundColor: "{colors.graphite-2}"
    textColor: "{colors.paper-bright}"
  jump-download:
    backgroundColor: "{colors.green}"
    textColor: "{colors.paper-bright}"
    rounded: "{rounded.base}"
    padding: "7px 11px"
  jump-download-hover:
    backgroundColor: "{colors.green-deep}"
  tag:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
    rounded: "{rounded.chip}"
    padding: "3px 9px"
  tag-version:
    backgroundColor: "{colors.graphite}"
    textColor: "{colors.paper-bright}"
    rounded: "{rounded.chip}"
    padding: "3px 9px"
  badge-op:
    backgroundColor: "{colors.graphite}"
    textColor: "{colors.paper-bright}"
    typography: "{typography.label-badge}"
    rounded: "{rounded.chip}"
    padding: "2px 7px"
  post:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.base}"
  author-column:
    backgroundColor: "{colors.paper-sunk}"
    textColor: "{colors.muted}"
    width: "{spacing.author-column}"
    padding: "24px 20px"
  post-body:
    padding: "16px 40px 34px"
  attachment-tray:
    backgroundColor: "{colors.paper-bright}"
    rounded: "{rounded.base}"
    padding: "16px 18px"
    width: "720px"
  button-download:
    backgroundColor: "{colors.green}"
    textColor: "{colors.paper-bright}"
    rounded: "{rounded.base}"
    padding: "13px 22px"
  button-download-hover:
    backgroundColor: "{colors.green-deep}"
  segmented-option:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.muted}"
    padding: "7px 13px"
  segmented-option-active:
    backgroundColor: "{colors.graphite}"
    textColor: "{colors.paper-bright}"
  header-demo:
    backgroundColor: "{colors.graphite}"
    textColor: "{colors.paper-bright}"
    rounded: "{rounded.panel}"
    padding: "26px 28px 22px"
  spoiler:
    backgroundColor: "{colors.paper-bright}"
    rounded: "{rounded.base}"
    padding: "13px 16px"
  spoiler-hover:
    backgroundColor: "{colors.paper-sunk}"
  code-block:
    backgroundColor: "{colors.graphite}"
    textColor: "#E6E8EB"
    typography: "{typography.mono}"
    rounded: "{rounded.base}"
    padding: "14px 16px"
  callout:
    backgroundColor: "{colors.green-wash}"
    textColor: "{colors.ink}"
    rounded: "{rounded.base}"
    padding: "12px 16px"
---

# Design System: Plain Launcher

## Overview

**Creative North Star: "The Release Thread"**

The site is the first post of an Android release thread, the document Xiaomi and Redmi owners already read before they install anything. The visitor lands on grey forum ground, under a graphite thread bar, and reads a thread title with bracketed device tags, then post #1 by the developer: author column on the left, the hook, the attached APK, the attached screenshots. Further posts (#2 Steps, #3 Ad Shield, #4 Install) follow the same anatomy. Changelog, known issues and questions are folded spoilers inside the posts, exactly where a forum puts them.

The system is a two-ink print on paper. Graphite carries the chrome and anything machine-made (thread bar, OP badge, version tag, code, the phone header demo, screenshot bezels); a single dark green carries everything that means "get it", "this stays" or "act here" (Download, ticks, links, step numerals, swipe arrows, focus). Everything else is greys taken from the app icon's brushed silver. Density is forum density: 1px rules separate everything, posts are generous in padding but tight in rhythm, and prose holds to a 68ch measure.

The page refuses the app-landing arrangement: no gradient hero, no floating phone mockup, no row of three feature cards. Proof arrives as attachments, never as staged marketing.

**Key Characteristics:**
- Forum anatomy: thread bar, breadcrumb, title with tag chips, numbered posts with an author column.
- Two inks only: graphite and one green, on grey ground and near-white paper.
- 1px rules for structure; one soft shadow, on posts only.
- Archivo (variable, condensed-to-wide) for thread structure; system sans for post prose; mono only for files and commands.
- Real device screenshots shown as small attached thumbnails with a graphite bezel.
- Motion only to unfold in place: spoilers open, swipe arrows draw, focus isolates.

## Colors

A graphite-and-green two-ink palette printed on cool forum greys.

### Primary
- **Download Green** (`green`): the one chromatic ink. Download buttons, the attachment tray's border and file glyph, tick icons in "what stays" lists, body links, step numerals in the install bands, swipe arrows in the gesture diagram, the second line of the hook, the focus ring and text selection. It is a darker green than the contract's planned #1F8F4E; the build darkened it so white button text and small green text pass contrast.
- **Pressed Green** (`green-deep`): hover state for every green surface and link.
- **Green Wash** (`green-wash`) with **Green Rule** (`green-rule`): the callout's fill and border, the only tinted surface.

### Secondary
- **Graphite** (`graphite`): thread bar, OP badge, version tag, code blocks, the phone header demo, the active segmented option, and the 4px bezel outline on screenshots. Same value as body ink.
- **Graphite Lift** (`graphite-2`): hover fill for jump links inside the thread bar.
- **Icon Silver** (`silver`): text on the thread bar, taken from the app icon's brushed silver.
- **Graphite Dim** (`graphite-dim`): breadcrumb text on the bar and comments inside code blocks.

### Neutral
- **Forum Ground** (`ground`): the page background behind posts.
- **Post Paper** (`paper`): post surfaces, tag chips, inactive segmented options.
- **Sunk Paper** (`paper-sunk`): the author column and spoiler-summary hover.
- **Bright Paper** (`paper-bright`): surfaces set into a post (attachment tray, spoilers) and text on graphite or green.
- **Rule** (`rule`): every 1px border: posts, author column divider, post-top line, tags, install bands, spoilers, segmented control.
- **Soft Rule** (`rule-soft`): dividers between items in tick lists and the gesture list.
- **Ink** (`ink`): body text and headings.
- **Muted** (`muted`): meta lines, captions, sub-hook, step descriptions, "what goes" cross icons.

### Named Rules
**The One Green Rule.** Green is the only hue on the page, and it always means get it, keep it, or act. Never use it for decoration, and never add a second accent.

**The Graphite Is Chrome Rule.** Graphite surfaces are for the forum chrome and for machine output (code, the phone header, screenshot bezels). Prose never sits in a graphite box.

## Typography

**Display Font:** Archivo, self-hosted variable (weight 100 to 900, width 62% to 125%), with system-ui fallback
**Body Font:** the platform system sans (system-ui, -apple-system, Segoe UI, Roboto, Noto Sans)
**Label/Mono Font:** ui-monospace stack (SFMono-Regular, Menlo, Consolas)

**Character:** A heavy, slightly widened grotesk carries the thread's structure the way a forum skin styles titles and buttons; the posts themselves read in the phone's own plain sans, as a real post would.

### Hierarchy
- **Display** (880, clamp 3rem to 5rem, line-height 0.9, width 112%): the hook only, "Ads gone. / Nothing lost.", second line in green.
- **Headline** (820, clamp 1.55rem to 2.25rem, line-height 1.14, width 96%, max 46ch, balanced): the thread title, with its bracketed prefix in muted at 700.
- **Title** (800, clamp 1.45rem to 1.9rem, line-height 1.15): the heading of each post after #1.
- **Title small** (780, 1.35rem): section headings inside a post.
- **Numeral** (880, 2.6rem, width 112%, tabular): install-band step numbers, in green.
- **Body** (400, 1.0625rem, line-height 1.6, max 68ch; 1rem under 720px): post prose. Lead paragraphs step up to 1.16rem.
- **Label** (Archivo 550 to 750, 0.82rem to 0.9rem): tags, jump links, spoiler actions, the screenshot label; the OP badge is 0.72rem at 750 with 0.04em tracking, uppercase as forum badges are.
- **Meta** (system sans, 0.82rem to 0.88rem, muted): post number line, thread meta, file details, captions.

### Named Rules
**The Three Voices Rule.** Archivo for thread structure (brand, title, headings, buttons, tags, badges, numerals); system sans for anything a person wrote in the post; mono only for file names, package names and commands.

**The Wide Weight Rule.** Weight 880 at 112% width is reserved for the hook and the install numerals. Nothing else gets the full stretch.

## Layout

A single centred column (max 1200px, 24px gutters) on forum ground. A sticky graphite thread bar (60px) holds the brand, a breadcrumb, and post jump links ending in a green Download link; anchor scrolling is offset by 76px to clear it. Below it, the thread head (title, tags, meta) sits directly on the ground with no card.

Posts are a two-column grid: a fixed 196px author column and a fluid body padded 16px 40px 34px, separated by 22px of ground. Inside a body, content holds to narrower measures rather than the full width: prose 68ch, attachment tray and header demo 720px, code 760px, install bands and spoilers 820px, two-up lists and the gesture diagram 900px, text-plus-screenshot rows 980px. The "what goes / what stays" lists sit side by side with a 40px gap. Post #2 sets text beside a 230px screenshot.

Screenshot attachments are a four-up grid capped at 470px so post #1's tray and thumbnails stay in the first viewport on wide screens; for the same reason the sub-hook's first clause is hidden above 980px.

Responsive steps:
- **At 980px and below:** breadcrumb hidden; jump links reduce to Install and Download; screenshots become two-up; split lists and text-plus-screenshot rows stack.
- **At 720px and below:** bar drops to 56px with 16px padding; gutters 12px; the author column collapses into a horizontal strip (40px avatar, name, badge) above the body; body padding 16px; the attachment tray becomes a grid with the Download button full width beneath the file; the gesture diagram stacks with the phone centred at 190px; install numerals shrink to 2.1rem in a 40px column; spoiler meta and lower-priority tags hide.

## Elevation & Depth

Flat by construction. Depth is a forum's: grey ground, paper posts on it, and 1px rules dividing everything inside. Surfaces set into a post (attachment tray, spoilers) step to bright white; the author column steps down to sunk paper. The only shadow belongs to the post itself.

### Shadow Vocabulary
- **Post lift** (`box-shadow: 0 1px 2px rgba(27, 29, 32, .05), 0 6px 18px -12px rgba(27, 29, 32, .25)`): every post card, and nothing else.

### Named Rules
**The One Lift Rule.** Only posts lift off the ground. Everything inside a post is flat and separated by rules, not shadows.

## Shapes

Small, near-square corners on everything that is UI: 3px on chips, badges and spoiler actions; 4px on posts, buttons, trays, spoilers, code, callouts, jump links and the segmented control; 6px on the graphite header demo. Larger radii appear only on objects that are physical in the world: phone screenshots (10px, 12px on the side screenshot) with a 4px graphite outline as their bezel, and the app icon as avatar (20px at 84px, 10px at 40px). Borders are 1px throughout; the attachment tray alone takes a 1.5px green border to mark it as the primary action.

Icons are inline SVG line glyphs on a 24px grid, 2px round-capped strokes in currentColor (2.6px for ticks), sized at 1.15em.

## Components

### Thread Bar
The forum's top bar, always present.
- **Style:** graphite, sticky, 1px black bottom edge, 60px tall. Brand (30px app icon, 750 Archivo at 108% width, white), breadcrumb in graphite-dim with lighter links, then right-aligned jump links.
- **Jump links:** silver Archivo 0.9rem at 550, 7px 11px padding, 4px radius; hover fills graphite-2 with white text. The final Download link is filled green at 700 and darkens on hover.

### Tags and Badges
- **Tag chips:** paper fill, 1px rule border, 3px radius, Archivo 0.82rem at 600 in graphite, 3px 9px. The version tag inverts to graphite with white text.
- **OP badge:** graphite, white, 0.72rem at 750 with 0.04em tracking, 3px radius, 2px 7px. Lives in the author column.

### Post
The unit of the whole page.
- **Shape:** paper, 1px rule border, 4px radius, post lift shadow, 22px apart.
- **Author column:** 196px, sunk paper, 1px rule on its right; 84px app-icon avatar, bold name, OP badge, optional one-line bio. Collapses to a horizontal strip on mobile.
- **Post top:** "Post #n" and a "Link" anchor, muted 0.82rem, above a 1px rule.

### Hook
Post #1's headline, at display scale: two lines, the second ("Nothing lost.") in green. A muted 1.12rem sub-hook follows, tucked up 8px.

### Attachment Tray
The primary action, shaped as a forum file attachment.
- **Style:** bright paper, 1.5px green border, 4px radius, 16px 18px, max 720px. Green outlined file glyph labelled APK, the file name in mono at 700, details in muted 0.86rem, then the Download button pushed right.
- **Download button:** green fill, white Archivo 750 at 1.15rem, 13px 22px, 4px radius, leading down-arrow icon. Hover darkens to green-deep; active nudges down 1px; 0.2s ease-out. Full width under 720px.

### Screenshot Attachments
- **Style:** small thumbnails under a muted "Attached images" label, 14px gaps, 10px radius with a 4px graphite outline as bezel, short caption beneath. Each links to the full image.
- **Focus:** hovering or focusing one dims the others to 40% opacity (0.25s).

### Tick Lists
Rows divided by soft rules, 7px vertical padding, an icon then the text. "What stays" uses green check icons; "what goes" uses muted crosses.

### Alarm Header Demo
A state comparison of the phone's at-a-glance header. A segmented control (paper options, 1px rule dividers, 4px radius; the pressed option fills graphite with white) switches a graphite panel (6px radius) that shows the date, weather and alarm line in white and light greys, with a caption under a dark rule explaining the state. Updates are announced politely.

### Gesture Diagram
One phone outline (graphite stroke on paper) carrying four green arrows: up, right, down and a double-tap mark, beside a list of the four gestures. The arrows draw in once when the diagram scrolls into view (0.9s, staggered 0.15s). Hovering or focusing a list item isolates its arrow: other items fade to 38%, other arrows to 15%. The same right-swipe glyph is reused as a small inline chip in post #2.

### Install Bands
An ordered list of full-width bands between 1px rules (top on each, bottom on the last), 18px vertical padding, a 52px column holding a large green Archivo numeral, then a bold step title and muted description.

### Fold-Flat Spoilers
Changelog, known issues and questions, folded closed.
- **Style:** bright paper, 1px rule border, 4px radius, max 820px, 10px apart. The summary row is bold, with a muted meta note and a right-aligned green outlined "Show" / "Hide" action (0.78rem at 750, 3px radius).
- **Behavior:** summary hover fills sunk paper; the content unfolds in place below a rule, animating height over 0.45s where supported and motion is allowed.

### Code Block
Graphite fill, light grey mono text at 0.9rem / 1.55, graphite-dim comments, 4px radius, 14px 16px, horizontal scroll, max 760px.

### Callout
Green wash fill with a green-rule border, 4px radius, 12px 16px, max 760px. Used for a single aside inside a post.

### Footer
Plain muted 0.9rem text on the ground under the last post: license line left, source and issue links right, wrapping on narrow screens.

### Motion
One easing for everything, a sharp ease-out (`cubic-bezier(.16, 1, .3, 1)`). Durations: 0.2s for button state, 0.25s for dim and isolate, 0.45s for spoiler unfold, 0.9s for arrow drawing. Smooth anchor scrolling, arrow drawing and spoiler animation are all disabled under reduced motion; without JavaScript the arrows render fully drawn.

### Named Rules
**The Unfold In Place Rule.** Motion only reveals what is already on the page: spoilers open where they sit, arrows draw on their own phone, focus dims the rest. Nothing slides in, floats or loops.

## Do's and Don'ts

### Do:
- **Do** put every block of content inside a numbered post with an author column; only the thread head and footer sit on the ground.
- **Do** keep green for downloads, ticks, links, numerals, gesture arrows and focus, darkening to green-deep on hover.
- **Do** separate content with 1px rules (rule between structures, rule-soft between list items).
- **Do** show the product only through real device screenshots, as attachment thumbnails with a 10px radius and a 4px graphite bezel outline.
- **Do** use Archivo for structure, system sans for prose, and mono only for file names, package names and commands.
- **Do** keep corners at 3 to 6px for interface pieces; reserve larger radii for screenshots and the app icon.
- **Do** keep a visible 2px green focus ring with 3px offset on every interactive element.

### Don't:
- **Don't** build the app-landing template: no gradient hero, no floating phone mockup, no row of three feature cards.
- **Don't** add a second accent hue, gradient surfaces, or tints beyond the single green-wash callout; the app icon's own graphite-to-black gradient stays inside the icon.
- **Don't** put shadows on anything inside a post; the post lift is the only shadow.
- **Don't** put kicker or eyebrow labels above headings; forum structure (post numbers, tags, badges) already locates the reader.
- **Don't** use emoji or icon fonts; icons are inline 2px-stroke SVG in currentColor.
- **Don't** animate for decoration; motion only unfolds, draws or isolates in place, and stops under reduced motion.
