---
name: Ninety
description: A quiet, native running order for daily attendance.
colors:
  light-primary: '#2457D6'
  dark-primary: '#8AACFF'
  light-primary-container: '#E8EEFC'
  dark-primary-container: '#243653'
  light-on-primary-container: '#173C91'
  dark-on-primary-container: '#DCE6FF'
  dark-on-primary: '#102654'
  light-background: '#F7F8FA'
  dark-background: '#101113'
  light-surface: '#FFFFFF'
  dark-surface: '#191B1F'
  light-ink: '#17191D'
  dark-ink: '#F3F4F6'
  light-muted: '#626975'
  dark-muted: '#A5ACB8'
  light-tonal: '#EFF1F4'
  dark-tonal: '#25282E'
  light-divider: '#E3E6EB'
  dark-divider: '#30343B'
  light-error: '#B3261E'
  dark-error: '#FFB4AB'
  light-error-container: '#FCEDEA'
  dark-error-container: '#44211F'
  light-on-error-container: '#8C1D18'
  dark-on-error-container: '#FFDAD6'
typography:
  display-large:
    fontFamily: Manrope
    fontSize: 44sp
    fontWeight: 700
    lineHeight: 52sp
    letterSpacing: -1sp
    fontFeature: tnum
  display-medium:
    fontFamily: Manrope
    fontSize: 38sp
    fontWeight: 700
    lineHeight: 46sp
    letterSpacing: -0.8sp
    fontFeature: tnum
  display-small:
    fontFamily: Manrope
    fontSize: 32sp
    fontWeight: 700
    lineHeight: 40sp
    letterSpacing: -0.6sp
    fontFeature: tnum
  headline-large:
    fontFamily: Manrope
    fontSize: 32sp
    fontWeight: 700
    lineHeight: 40sp
    letterSpacing: -0.6sp
    fontFeature: tnum
  headline-medium:
    fontFamily: Manrope
    fontSize: 28sp
    fontWeight: 700
    lineHeight: 36sp
    letterSpacing: -0.5sp
    fontFeature: tnum
  headline-small:
    fontFamily: Manrope
    fontSize: 23sp
    fontWeight: 600
    lineHeight: 30sp
    letterSpacing: -0.3sp
    fontFeature: tnum
  title-large:
    fontFamily: Manrope
    fontSize: 20sp
    fontWeight: 600
    lineHeight: 28sp
    letterSpacing: -0.2sp
    fontFeature: tnum
  title-medium:
    fontFamily: Manrope
    fontSize: 16sp
    fontWeight: 600
    lineHeight: 24sp
    letterSpacing: 0sp
    fontFeature: tnum
  title-small:
    fontFamily: Manrope
    fontSize: 14sp
    fontWeight: 600
    lineHeight: 20sp
    letterSpacing: 0sp
    fontFeature: tnum
  body-large:
    fontFamily: Manrope
    fontSize: 16sp
    fontWeight: 400
    lineHeight: 24sp
    letterSpacing: 0sp
    fontFeature: tnum
  body-medium:
    fontFamily: Manrope
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 22sp
    letterSpacing: 0sp
    fontFeature: tnum
  body-small:
    fontFamily: Manrope
    fontSize: 12sp
    fontWeight: 400
    lineHeight: 18sp
    letterSpacing: 0sp
    fontFeature: tnum
  label-large:
    fontFamily: Manrope
    fontSize: 14sp
    fontWeight: 600
    lineHeight: 20sp
    letterSpacing: 0sp
    fontFeature: tnum
  label-medium:
    fontFamily: Manrope
    fontSize: 12sp
    fontWeight: 500
    lineHeight: 18sp
    letterSpacing: 0sp
    fontFeature: tnum
  label-small:
    fontFamily: Manrope
    fontSize: 11sp
    fontWeight: 500
    lineHeight: 16sp
    letterSpacing: 0sp
    fontFeature: tnum
rounded:
  marker: 2dp
  small: 8dp
  tab-indicator: 10dp
  medium: 12dp
  large: 16dp
  extra-large: 24dp
spacing:
  space-2: 2dp
  space-4: 4dp
  space-6: 6dp
  space-8: 8dp
  space-10: 10dp
  space-12: 12dp
  space-14: 14dp
  space-16: 16dp
  space-20: 20dp
  space-24: 24dp
  space-32: 32dp
  space-40: 40dp
components:
  primary-action-light:
    backgroundColor: '{colors.light-primary}'
    textColor: '{colors.light-surface}'
    typography: '{typography.label-large}'
    rounded: '{rounded.medium}'
    padding: 14dp 20dp
  primary-action-dark:
    backgroundColor: '{colors.dark-primary}'
    textColor: '{colors.dark-on-primary}'
    typography: '{typography.label-large}'
    rounded: '{rounded.medium}'
    padding: 14dp 20dp
  text-button-light:
    textColor: '{colors.light-primary}'
    typography: '{typography.label-large}'
  icon-action:
    size: 48dp
  outlined-field:
    typography: '{typography.body-large}'
  filter-choice:
    typography: '{typography.label-large}'
  surface-card-light:
    backgroundColor: '{colors.light-surface}'
    textColor: '{colors.light-ink}'
    rounded: '{rounded.large}'
  surface-card-dark:
    backgroundColor: '{colors.dark-surface}'
    textColor: '{colors.dark-ink}'
    rounded: '{rounded.large}'
  selected-tab-light:
    backgroundColor: '{colors.light-primary-container}'
    textColor: '{colors.light-on-primary-container}'
    rounded: '{rounded.tab-indicator}'
    height: 32dp
    width: 56dp
  selected-tab-dark:
    backgroundColor: '{colors.dark-primary-container}'
    textColor: '{colors.dark-on-primary-container}'
    rounded: '{rounded.tab-indicator}'
    height: 32dp
    width: 56dp
  outcome-present-light:
    backgroundColor: '{colors.light-primary-container}'
    textColor: '{colors.light-on-primary-container}'
    rounded: '{rounded.small}'
    padding: 6dp
  outcome-present-dark:
    backgroundColor: '{colors.dark-primary-container}'
    textColor: '{colors.dark-on-primary-container}'
    rounded: '{rounded.small}'
    padding: 6dp
  period-progress:
    height: 4dp
    rounded: '{rounded.marker}'
  schedule-time-rail:
    width: 52dp
---

# Design System: Ninety

## Overview

**Creative North Star: "Quiet rhythm"**

A quiet running order makes daily attendance easy to scan and record. Full course names, a narrow period/time rail, open rows, and a small set of outline icons carry the interface. Blue identifies action and selection against neutral light or charcoal dark surfaces.

The owner-pinned character is minimal, clean, uncluttered, and icon-led. Supporting text appears when it changes a decision: provisional credit, missing records, recovery requirements, and destructive confirmations remain explicit. There is no decorative imagery, wallpaper-derived color, or purple/lavender surface language.

**Key Characteristics:**
- Open time-led rows rather than repeated dashboard cards.
- Bundled Manrope with tabular numerals and compact hierarchy.
- Restrained blue, neutral groups, and semantic error color in both themes.
- Named icon actions; short text for primary and consequential actions.

This is a scan-mode record of the implemented Kotlin/Jetpack Compose app, not a pre-build seed. The frontmatter records authored native values: `dp` is density-independent layout size and `sp` is scalable text size. Neither is a CSS pixel value. Source authority is `app/src/main/kotlin/com/niranjan/ninety/ui/designsystem/Theme.kt`, the shared components in that directory, `ui/NinetyApp.kt`, every screen directory under `ui/`, and `MainActivity.kt`. Reused local spacing values are extracted here as a vocabulary; the app does not define a separate spacing-token object.

The owner’s settled direction is recorded in `.impeccable/surfaces/otlin-com-niranjan-ninety-ui-ninetyapp-kt-4ee6688b.md`; product policy remains in `PRODUCT.md` and `PROJECT_SPEC.md`. No approved comp or generated app raster applies. The native finish review at `.impeccable/review/finish-review.md` says **ship** for its 28 supplied phone captures at normal text size. That review does not establish tablet, enlarged-text, TalkBack, gesture, or animation-performance guarantees. Native screenshots in `docs/screenshots/` carry origin metadata; review fixtures are synthetic and in memory.

## Colors

A cool neutral ground and one restrained blue accent remain consistent across the explicit light and dark schemes. Frontmatter values are normative; the names below explain their native role mapping.

### Primary

- **Action Blue** (`light-primary` / `dark-primary`): `primary`, used for the main action, selection, active controls, and recorded period markers. Primary-button foreground is `light-surface` in light mode and `dark-on-primary` in dark mode.
- **Quiet Blue Wash** (`light-primary-container` / `dark-primary-container`): selected-tab and present/approved outcome backgrounds. `onPrimaryContainer` uses the corresponding `on-primary-container` token. Material `secondaryContainer` intentionally aliases these same blue values.

### Neutral

- **Page Ground** (`light-background` / `dark-background`): scaffold and navigation ground.
- **Grouped Surface** (`light-surface` / `dark-surface`): cards, fields’ surrounding groups, and base Material surfaces.
- **Ink** (`light-ink` / `dark-ink`): `onBackground` and `onSurface`; full course names, headings, and primary reading text.
- **Slate** (`light-muted` / `dark-muted`): `onSurfaceVariant`, secondary/tertiary roles, and the stronger interactive `outline`.
- **Tonal Group** (`light-tonal` / `dark-tonal`): `surfaceVariant`, high containers, neutral pending/not-counted outcomes, and `tertiaryContainer`. Neutral foregrounds use Ink or Slate according to the native role.
- **Quiet Divider** (`light-divider` / `dark-divider`): `outlineVariant` and the highest neutral containers. A decorative divider is not the stronger interactive outline.

### Semantic status

- **Error** (`light-error` / `dark-error`): below-minimum percentage, missing-record warnings, input errors, and destructive text actions.
- **Error Wash** and **Error Ink** (`error-container` / `on-error-container` in each theme): absent and rejected outcome badges.
- The app does not create separate secondary or tertiary accent hues. Present and approved duty leave share the primary family; pending duty leave and at-the-limit standing use the caution role with explicit labels. The fixed institutional minimum is not a configurable color-coded target.

**The Pinned Palette Rule.** Use the explicit light or dark scheme. System mode chooses the scheme; wallpaper colors never replace it.

**The Useful Color Rule.** Blue marks action, selection, focus, or recorded progress. Error colors mark absence, rejection, validation, missing records, or a course below its minimum. Pending credit stays neutral and named.

## Typography

**Display Font:** Manrope. **Body and Label Font:** Manrope. Four static Android font resources supply Regular (400), Medium (500), SemiBold (600), and Bold (700). There is no authored fallback stack in Compose; this record does not invent one. Files live in `app/src/main/res/font/`; `res/raw/manrope_license.txt` contains the SIL OFL 1.1 notice, available through Settings → About Ninety → Font license.

Manrope is a single, open sans voice. All declared text styles enable tabular numerals (`tnum`) so counts, dates, times, and percentages remain steady. Use the frontmatter type roles rather than introducing another display family.

### Hierarchy

- **Display and large headline:** the declared large end of the theme is retained in frontmatter; it is not the default page-heading treatment. Phone pages primarily use `headline-medium`.
- **Headline medium:** page titles and the day name. **Headline small:** course percentages, with an explicit Bold override in `CoursePercentage`.
- **Title large:** the screen title in the top bar and the completion title; the top bar uses Bold. **Title medium:** full course names and key group labels. **Title small:** muted section labels.
- **Body large/medium:** fields, readable content, dates in selectable lists, and dialog content. **Body small:** date support, time rails, course type, attendance next step, and brief helper/error context.
- **Label large/medium/small:** action and choice labels, compact counts, state text, weekday headers, and the small DL label. Sentence case is the normal treatment; period identifiers such as P1 and the product abbreviation DL remain functional labels.

**The Clear State Rule.** Keep full course names and concise uncertainty labels. Icons reduce repeated copy; they do not erase provisional or consequential meaning.

## Layout

The native shell uses edge-to-edge drawing with Scaffold insets. Content is a single vertical flow with a repeated horizontal page inset of `space-24`. Screens choose scroll containers for their content: Today and Settings scroll vertically; Courses, course detail, Calendar, and Duty leave use lazy lists. Setup also applies IME padding and scrolls back to the top when the step changes.

`ScreenHeading` places the title and optional short detail beside trailing actions; its source uses top `space-16`, bottom `space-20`, an `space-8` horizontal gap, and `space-4` between title and detail. Today uses its own date heading with top `space-12` and bottom `space-24`. Settings groups commonly use `space-16` internal padding; list rows separate with quiet dividers rather than outer cards.

The repeated time rail is the width recorded in `schedule-time-rail`. Today uses a `space-14` rail/content gap and vertical row padding; the recurring timetable uses a `space-12` gap, minimum row height (72dp), and vertical `space-12` padding. Course names take remaining width and are not replaced by short codes. Consecutive lab periods group visually and expand into individual records. Calendar is a seven-column month grid with native circular day controls.

### Implemented adaptation

At a shell width of 600dp or more, `NinetyApp.kt` switches the bottom bar to a labeled navigation rail. A 760dp maximum is declared on the NavHost modifier; its chain also uses `fillMaxSize()`, so this record does not claim the maximum has been proven to constrain all parents. Setup is a separate Scaffold. Above font scale 1.3, course percentages stack below the course heading and the compact bottom tabs hide visible labels; semantic tab names remain. Choice controls use FlowRow to wrap whole choices. These are source behaviors, not completed tablet or enlarged-text visual verification.

## Elevation & Depth

The app is visually flat and uses neutral tonal separation rather than authored shadow tokens. `NinetyCard` sets the current `surface` color and a large theme shape; it supplies no custom border or elevation. The compact NavigationBar explicitly sets tonal elevation to zero. Native Material menus, tooltips, dialogs, and modal sheets retain their framework depth and scrim behavior; there is no app-authored shadow scale to extract. The theme’s `surfaceTint` is transparent.

**The Tonal Group Rule.** Use open rows for running lists and neutral surface groups where settings, calendar content, or review data need a boundary. Do not turn every row into a bordered card.

Navigation slides and the selected-tab indicator use an authored 160ms `FastOutSlowInEasing` tween. Outcome backgrounds and period markers use 180ms color tweens. Expandable course and duty-leave sections use Compose `animateContentSize()` with its framework default, not an invented fixed-duration token. These native animations follow Compose’s system animation-duration behavior; still captures do not verify their performance.

## Shapes

Soft, modest corners support the native controls. Theme small, medium, large, and extra-large shapes are in frontmatter. PrimaryAction explicitly uses medium; NinetyCard explicitly uses large. OutcomeChip uses small. Compact tab indicators have their own rounded rectangle shape, and progress segments use the marker shape. CircleShape is used for calendar days, the completion check, and some progress tracks. Do not apply the extra-large token indiscriminately to ordinary rows; Material dialogs and sheets keep their native component shape behavior.

Authored outline vectors in `app/src/main/res/drawable/` use a 24-unit viewport, round caps/joins, and a consistent stroke (1.7 viewport units in the sampled control icons). Runtime Material tint supplies their color. Typical action glyphs are 22dp inside ActionIcon; compact navigation glyphs are 24dp, rail glyphs 22dp, primary-action glyphs 20dp, and outcome glyphs 18dp. The launcher mark is a separate identity asset.

## Components

### Buttons

**PrimaryAction** is a concise icon-plus-label commitment. It uses the current scheme’s primary/onPrimary pair, theme medium corners, a minimum height (52dp), vertical `space-14` / horizontal `space-20` padding, a 20dp glyph, and `space-10` icon/text gap. Its minimum height is a constraint, not a guaranteed fixed size. Enabled/disabled treatment and interaction feedback come from Material Button.

**TextButton** carries secondary actions and dialog Save/Cancel controls. Destructive ConfirmDialog text uses `error`. **OutlinedButton** appears for the explicit Absent sheet action; it retains Material defaults and a minimum height (52dp). These variants do not define custom app-wide hover states.

**ActionIcon** is an authored vector in a 48dp IconButton with a 22dp glyph, an accessible label, and a native PlainTooltip. Default tint is `onSurfaceVariant`; disabled tint multiplies alpha by 0.38. Primary actions can request primary tint. Material ripple/focus interactions remain native.

### Chips

**FilterChip** is the native choice control used by ChoiceRow, weekdays, theme choices, and form options. ChoiceRow uses `space-8` horizontally / `space-4` vertically and a minimum height (48dp); weekday chips use a `space-6` gap. Choice state comes from Material selection semantics and the pinned scheme. Default FilterChip shape, stroke, and interaction treatments are inherited from the linked Material 3 version; they are not custom radius/border tokens.

**OutcomeChip** is a compact state badge, not a filter. It has small corners, `space-6` padding, an 18dp state vector, and a semantic full-state description. Present and approved use Quiet Blue Wash, absent and rejected use Error Wash, and pending uses the caution role. Not marked, not held, and non-counting periods use neutral containers. The clock/check/cross/minus prevents color from carrying state alone.

### Cards / Containers

**NinetyCard** groups Settings, calendar, review, and selected attention content on the scheme’s surface with large corners. The wrapper adds no universal padding; the caller commonly uses `space-16` (calendar vertical padding uses `space-12`). Today, Courses, and Duty Leave primarily use open rows. “Card” in a Kotlin function name does not establish a boxed card visually; Course rows are full-width controls with a bottom divider. A tap opens the course detail and its labelled actions.

### Inputs / Fields

**OutlinedTextField** uses Material’s native label, outline, focus, error, and disabled treatments under NinetyTheme. The app authors field content, error text, single-line behavior, keyboard options, and layout, but does not supply a custom universal field radius, stroke width, or focus glow. Course editors use a scrollable AlertDialog body with explicit Save/Cancel and disable Save for a blank name. Date fields open the native Material DatePickerDialog through NinetyDatePicker; FieldButton pairs a short label with a text action.

### Navigation

The compact bar has four always-labelled tabs for Today, Courses, Calendar, and Settings. Its height grows with font scale. Re-selecting a tab returns to its root. Re-selecting Today also returns to the current date. The selected tonal indicator is 32dp high and up to 56dp wide (bounded by tab width); it moves with the native slide timing. Each tab has `Role.Tab`, a full accessible name, ripple feedback, and an authored 1dp primary focus border around its icon area. Back returns from detail destinations to the previous screen. The large-width rail uses Material NavigationRailItem with visible labels.

Each main screen has its own top-bar title. The semester name below it opens a labelled switcher when multiple semesters exist. A banner identifies a browsed semester and provides a direct return to the current one. Courses hosts **Timetable** and **Duty leave** rows above the courses. Course detail collects provisional credit, attendance breakdown, dates that are not marked, and labelled course actions. Duty leave uses an always-visible status line and a separate section for leave from before tracking began. Settings links to semester management and About Ninety through labelled rows.

### Period progress and running rows

**PeriodProgress** is functional progress: one equal-width marker per eligible period, capped to 24 visible markers for larger totals, with exact recorded/total semantics. Markers are 4dp high with a 5dp gap; filled markers use primary and remaining markers use outlineVariant. Setup reuses it for the actual four-step sequence. Completion replaces the main recording action with the check, recorded count, and review affordance; the track is not a permanent decoration.

**WeeklySchedule / Today rows** pair the narrow period/time rail with a full course name and concise actions. Today’s grouped labs expose per-period outcomes inline; the recurring editor opens a native course menu. The same row grammar stays legible without a repeated outer card.

`.impeccable/design.json` extends this record with color metadata, synthesized display ramps, native motion/breakpoint metadata, and ten self-contained web preview tiles. Those HTML/CSS tiles are documentation translations of Compose components: native dp/sp are illustrated in CSS px, local Manrope is requested with a browser sans fallback, and web hover/focus rules illustrate interactivity. They do not define native interaction tokens, replace source code, or establish pixel fidelity.

## Do's and Don'ts

### Do:
- Do use the pinned light and dark schemes and the shared Manrope hierarchy.
- Do keep full course names, period-based counts, and short provisional or incomplete-state labels.
- Do use the authored outline vectors and supply an accessible name for each icon action.
- Do retain concise text on the primary action and on save, cancel, restore, and destructive decisions.
- Do reuse open rows, the time rail, tonal grouping, and native Material interaction behavior.
- Do validate new layouts on the target device; source adaptations are not proof of tablet, enlarged-text, or TalkBack behavior.

### Don't:
- Don't introduce wallpaper dynamic color, purple/lavender surfaces, or additional decorative accents.
- Don't add explanatory paragraphs, decorative imagery, ornamental progress, or repeated bordered dashboard cards.
- Don't replace authored vectors with text glyphs, emoji, or icon fonts.
- Don't make pending credit look confirmed or hide missing-record and below-minimum states to simplify a screen.
- Don't treat documentation HTML previews as app code or pixel-accurate native renders.

No material craft-floor defect was reported in the supplied phone review. Unverified device classes, enlarged text, TalkBack, live gestures, and animation performance are verification limits, not design-system rules or repaired defects. Native source sampling and the absence of a web detector are not a claim of exhaustive policy or accessibility certification.
