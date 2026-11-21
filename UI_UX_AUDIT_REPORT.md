# Ninety — UI/UX Audit Report

**Scope:** whole app, working tree as of 2026-11-21 (includes the uncommitted redesign on top of `3ce1ba5`).
**Deliverable:** analysis and recommendations only. No application code was changed.

## How this audit was done, and what it cannot claim

| Evidence | Used for | Limits |
|---|---|---|
| Every file under `app/src/main/kotlin/com/niranjan/ninety/ui/` read in full, plus `domain/`, `NinetyRepository.kt`, the relevant parts of `RoomNinetyRepository.kt`, `Backup.kt`, `platform/Reminder*.kt` | Navigation, flows, copy, states, validation, accessibility semantics | Source shows structure and logic, not rendered pixels |
| 28 phone captures in `.impeccable/review/native-1794510837826142103/` and `docs/screenshots/` (1080×2400, normal font scale, light and dark, synthetic data, dated 13 Nov) | Visual hierarchy, density, icon legibility | They match the current code where I compared them, but they are 8 days old and cover only the states listed below |
| `PRODUCT.md`, `PROJECT_SPEC.md` §2–§7, `DEVICE_TESTING_HANDOFF.md` | Product intent and constraints | — |
| Contrast ratios computed from the hex values in `Theme.kt` | Colour findings | Computed, not measured on a device |

**Not verified.** No device was attached (`adb devices` returned an empty list) and I did not build or run the app. The following have no screenshot and were assessed from code only: every dialog except the two course editors (duty-leave picker, extra class, copy weekday, opening balance, holiday confirm, restore preview, timetable conflicts, reminder time), weekend / holiday / future / partially recorded days, an expanded lab group, the "needs attention" list, a browsed or completed semester, font scale above 1.0, the ≥600 dp navigation-rail layout, TalkBack behaviour, and all motion.

Each finding is marked **Confirmed** (follows directly from code or a screenshot) or **Potential** (plausible from code, needs a device to settle).

**A note on the "icon-led, minimal" direction.** `PRODUCT.md` records that the owner asked for an icon-led, uncluttered redesign. The same paragraph also says to "retain concise text where attendance uncertainty or a consequential decision requires it." Many findings below are places where the first instruction was applied and the second was not. The recommendations keep the minimal look; they add words only where an icon currently stands alone on an action that changes records.

---

## 1. Executive Summary

Ninety has a correct, carefully protected attendance engine and a clean visual base: one typeface, a restrained palette, consistent spacing, real undo, and safe bulk actions. The happy path — open the app, tap **Mark all present** — is one tap and well designed.

Almost everything outside that happy path is harder than it should be. The three biggest weaknesses are:

1. **Consequential actions are unlabeled icons, and the same icons mean different things on different screens.** The period sheet offers four bare glyphs for duty leave, clear record, class not conducted and substitute (`DayScreen.kt:513-520`). A lone check mark next to the word "Active" archives the semester (`SettingsScreen.kt:201-206`). Approve / reject / reset on a leave request are ✓ ✕ 🕓 hidden inside an expanded card (`LeaveScreen.kt:149-155`). Labels exist only as long-press tooltips. Four of five bottom tabs show no label.
2. **The interface does not tell the student how far to trust a percentage.** Unrecorded past periods are silently left out of the calculation and are flagged only by small red text on Courses and a list at the bottom of History — never on Today. A percentage that depends on unapproved duty leave is marked only by the grey word "Provisional". "Not recorded", "Not conducted" and "Not counted" render as the same grey dash.
3. **Features sit where the data model put them, not where a student would look.** The Duty Leave tab cannot create a leave. The timetable is behind a calendar icon on Courses. Today cannot step back to yesterday. A semester cannot be renamed or have its start date fixed, although the repository supports it.

The most valuable changes, in order:

| # | Change | Effort |
|---|---|---|
| 1 | Label every tab, and every action that changes a record | Low |
| 2 | Put "N past days unrecorded" on Today with a one-tap catch-up, and make the Courses warning tappable | Low–Medium |
| 3 | Keep Present / Absent visible on every period row after recording, so a correction is one tap and no sheet | Medium |
| 4 | Give the three "dash" states distinct chips, and show the confirmed-only percentage next to a provisional one | Low |
| 5 | Make Today a day pager (‹ date ›) and stop collapsing completed days | Medium |
| 6 | Go from five tabs to four: Today · Courses · Calendar · Settings, with Duty leave and Timetable as labelled rows on Courses | Medium |
| 7 | Explain the mid-semester choice in setup, and add semester editing | Medium |

None of these touches the attendance arithmetic.

---

## 2. Current Application Structure

### 2.1 Screen map

```
NinetyApp (ui/NinetyApp.kt)
├─ Loading spinner                        while app state loads
├─ Setup wizard (full screen, 4 steps)    first run, or Settings → + New semester
│   1 Your semester     name · start date · college timezone
│   2 Your courses      list + "Add a course" dialog (name, type, optional opening attendance)
│   3 Weekly timetable  Mon–Fri chips × 6 period rows, dropdown per row
│   4 Review            → "Start tracking"
└─ Main scaffold
    Top bar: "Ninety" · [back on Day/Timetable] · semester name (static text)
    Bottom bar (phone) / rail (≥600 dp): 5 destinations
    ├─ Today      route "today"     DayScreen(isToday = true)
    │    └─ Day   route "day/{date}?fromHistory"   DayScreen for any date (pushed)
    ├─ Courses    route "courses"
    │    └─ Timetable  route "timetable"  (pushed from the calendar icon)
    ├─ History    route "history"   month grid + "Needs attention" list → Day
    ├─ Duty Leave route "leave"     list of leave requests
    └─ Settings   route "settings"  Appearance · Reminder · Semesters · Backup · About
```

### 2.2 Dialogs, sheets and menus

| Host | Surface | Purpose |
|---|---|---|
| Day | ⋮ menu | Mark as holiday · Copy a weekday here · Add extra class · Apply duty leave |
| Day | Period bottom sheet (tap a period row) | Present · Absent · 4 icon actions · extra-class controls |
| Day | Dialogs | Holiday confirm · Copy weekday · Add extra class · Apply duty leave · Replace recorded attendance · "Recorded periods kept" |
| Courses | ⋮ menu per course | Edit course · Edit opening balance · Retire course |
| Courses | Dialogs | Add/Edit course · Opening balance · Retire confirm |
| Timetable | Dialogs | Effective-date picker · "Saved with N conflicts" |
| Duty Leave | ⋮ menu per period | Pending · Approved · Rejected |
| Settings | Dialogs | About · Font license · Mark semester done · Restore preview · Reminder time |
| Global | Snackbar | Undo for the most recent change; error and info messages |

### 2.3 How the main jobs are done today

| Job | Current path | Interactions |
|---|---|---|
| Record a normal day | Today → **Mark all present** | 1 |
| One absence, rest present | Today → ✕ on that period → **Mark remaining present** | 2 |
| Fix a wrong mark on a finished day | Today → **Review attendance** → tap the row → sheet → Absent | 3, and the row gives no hint it is tappable |
| Attended 2 of 3 lab periods | chevron → tap the missed period → sheet → Absent → ✓ on the group | 4, one of them modal |
| Record yesterday | History tab → tap date → Mark all present | 3 |
| Duty leave for a whole day | Today → ⋮ → Apply duty leave → Select all periods → Apply | 4 |
| Approve a leave | Duty Leave tab → chevron → ✓ | 3, on an unlabeled icon |
| Mark a holiday | Day → ⋮ → Mark as holiday → confirm | 3; impossible for a future date |
| Working Saturday | History → tap the Saturday → ⋮ → Copy a weekday here → pick a day | 5; nothing names this as the way to do it |
| Class cancelled | tap period → sheet → the "—" icon | 2, unlabeled |
| Change the timetable | Courses → calendar icon → pencil → edit → ✓ | 4+, three of them icon-only |
| See standing | Courses tab | 1 |
| Back up | Settings → scroll → Export → pick a location | 3 |
| Switch semester | Settings → book icon on the semester card | 2, unlabeled |

### 2.4 State handling in place

- **Empty states:** no semester; no classes; weekend; no duty leave; no attendance for a course; no timetable.
- **Loading:** spinners for app, day and courses.
- **Errors:** snackbar via `Throwable.userMessage()`; inline error text in setup and two dialogs.
- **Confirmation:** holiday toggle, retire course, mark semester done, replace recorded attendance with duty leave, restore backup.
- **Undo:** every journal write records a reversible batch; the newest one is offered in a ~4 s snackbar.

---

## 3. Identified UI/UX Issues

Format: **Issue · Location · Evidence · User impact · Fix · Why · Priority / Complexity**.
Priority = user harm. Complexity = implementation effort for a Compose codebase of this size.

### A. Navigation and information architecture

#### A1. Four of five tabs have no visible label — *Confirmed*
- **Location:** bottom navigation.
- **Evidence:** `NinetyApp.kt:336` draws the label only `if (showLabels && selected)`; `:304` turns labels off entirely above font scale 1.3. Screenshots show five outline icons and one word.
- **Impact:** the icons are not self-explanatory. The duty-leave glyph is a clipboard with a tick (reads as "tasks"); Today is a calendar with a tick; History is a clock-arrow; Settings is a pair of sliders. A new user must tap each to learn it. Users with enlarged text — the ones most in need of help — get no label at all.
- **Fix:** always show all labels, as Material 3 specifies for 3–5 destinations. Use `labelSmall`, one line. At very large font scales let the bar grow taller rather than dropping labels. Shorten "Duty Leave" to "Leave" if it remains a tab (see A2).
- **Why:** recognition instead of recall; costs 16 dp of height that is already reserved (the bar is fixed at 72 dp).
- **Priority:** High · **Complexity:** Low

#### A2. The Duty Leave tab cannot create a leave, yet holds a primary tab — *Confirmed*
- **Location:** Duty Leave tab; Day ⋮ menu.
- **Evidence:** `LeaveScreen.kt` has no add action. Its empty state says "No duty leave yet — Add leave from a day's menu." (`:90-92`). Creation exists only in `DayScreen.kt:276` (⋮ → Apply duty leave), the expanded lab group (`:408`) and the period sheet (`:514`).
- **Impact:** a student who has been granted leave opens the tab named Duty Leave and finds nowhere to enter it. The instruction "a day's menu" does not say which screen or which control. Meanwhile a feature used a few times a semester takes 20 % of primary navigation.
- **Fix:** (1) add **Add duty leave** to the screen: pick a date (default today) → the existing period picker for that date. (2) Move the screen out of the tab bar to a labelled row at the top of Courses — see §5. (3) Keep the contextual entry points on the day.
- **Why:** the feature is reachable from where it is named, and the tab bar is left to daily destinations.
- **Priority:** High · **Complexity:** Medium

#### A3. The timetable is hidden behind an unlabeled calendar icon — *Confirmed*
- **Location:** Courses heading.
- **Evidence:** `CoursesScreen.kt:83` — `ActionIcon(R.drawable.ic_calendar, "Timetable", …)`. The same drawable is the date-picker trigger in setup, the effective-date control in the timetable editor, and the illustration in every empty state (`Components.kt:163`).
- **Impact:** the weekly timetable — one of the three things set up on day one — has no named entry anywhere in the app. A calendar icon beside "+" on a course list suggests "pick a date".
- **Fix:** replace the icon with a full-width row above the course list: `Timetable ›` with a one-line summary ("Mon–Fri · 28 periods a week"). Also link to it from the "No classes scheduled" empty state on a weekday.
- **Why:** a named row is found without long-pressing; the empty-state link catches the moment the user needs it.
- **Priority:** High · **Complexity:** Low

#### A4. Today cannot move to another day — *Confirmed*
- **Location:** Today tab.
- **Evidence:** `DayScreen.kt:259-262` renders previous/next only `if (!isToday)`. `DayViewModel.setDate()` (`:39`) exists but nothing calls it.
- **Impact:** the most common recovery task — "I forgot to mark yesterday" — requires leaving Today, opening History, finding the date in a grid, and tapping it.
- **Fix:** make Today a day pager. Header: `‹  Fri 13 Nov  ›`, with a **Today** chip shown whenever the visible date is not today. Change the date in place via the existing `setDate()`; do not push a route. Tapping the date opens a date picker for long jumps.
- **Why:** one tap to yesterday; the tab's selected state resets to today on re-entry, so "Today" stays truthful.
- **Priority:** High · **Complexity:** Medium

#### A5. Day-to-day navigation pollutes the back stack, and its arrows disappear at today — *Confirmed*
- **Location:** Day screen opened from History.
- **Evidence:** `NinetyApp.kt:243` — `onOpenDay = { nav.navigate(dayRoute(it, fromHistory)) }` pushes a new entry for every arrow tap. `:242` passes `isToday = date == today`, so stepping forward to today removes both arrows (`DayScreen.kt:259`). The top bar shows a back arrow (`NinetyApp.kt:196`) drawn with the same `ic_back` drawable as "Previous day" (`DayScreen.kt:260`).
- **Impact:** after stepping through five days, Back must be pressed five times to return to the calendar. Reaching today strands the user with no arrows. Two identical left-arrows sit 100 dp apart with different meanings.
- **Fix:** solved by A4's in-place pager. When a day is opened from the calendar, push one entry; arrows replace its date argument (`popUpTo` the current entry, or hold the date in the ViewModel). Use chevrons (`‹ ›`) for day stepping and keep the arrow for Back.
- **Why:** Back always means "return to where I came from", once.
- **Priority:** High · **Complexity:** Medium (shared with A4)

#### A6. The top bar spends a full row on the app name — *Confirmed*
- **Location:** every main screen.
- **Evidence:** `NinetyApp.kt:188-209` — the title is always "Ninety"; each screen then draws its own `headlineMedium` heading (`PageHeader.kt:25`). In `phone-light-today.png` the first period row starts roughly 40 % down the screen. The semester name at the right is plain text.
- **Impact:** about 64 dp of permanent branding on a screen whose job is to show six rows. On Day and Timetable the bar reads "← Ninety", which names neither the screen nor where Back leads.
- **Fix:** remove the brand title. Use the screen's own heading as the top-bar title (Today's date; "Courses"; "Calendar"; "Settings"; "Timetable"; "Duty leave"). Keep the semester name as a small tappable label beneath or beside it; with more than one semester it opens a switcher sheet (see A7).
- **Why:** one header instead of two, a meaningful Back context, and a full extra period row above the fold.
- **Priority:** Medium · **Complexity:** Low–Medium

#### A7. Browsing another semester changes every tab with almost no signal and no way back — *Confirmed*
- **Location:** Settings → Semesters → book icon; then all tabs.
- **Evidence:** `NinetyApp.kt:262-265` sets `viewedSemesterId` and jumps to Today. The only cue is the 12 sp name in the top bar plus "Semester done" for archived ones (`:202-207`). The override lives in memory (`AppViewModel.kt:57`) and is cleared only by browsing the active semester again or restarting the app. Records in the browsed semester remain editable (`requireWritableSession`, `RoomNinetyRepository.kt:678-686`, has no archive check).
- **Impact:** a student who peeks at last semester and then marks "today" may be editing the wrong semester, or may believe their current data is gone.
- **Fix:** while a non-active semester is shown, pin a banner under the top bar on every tab: `Viewing Semester 4 (finished) · Back to Semester 5`. Tint the top bar `surfaceVariant` in that mode. For a finished semester, also relabel **Mark all present** as an ordinary outlined button so it does not invite routine use.
- **Why:** a mode that changes the meaning of every screen needs a persistent indicator and a one-tap exit.
- **Priority:** High · **Complexity:** Low–Medium

#### A8. "History" is a calendar that also shows the future — *Confirmed*
- **Location:** History tab.
- **Evidence:** `HistoryScreen.kt:77-88` — unbounded month navigation; future dates open a projected schedule.
- **Impact:** minor mislabel; "History" does not suggest "this is where I mark a working Saturday" or "see next week".
- **Fix:** rename the tab and heading to **Calendar** and use a plain calendar icon. (This frees the clock-arrow icon, which is currently overloaded — see B1.)
- **Priority:** Low · **Complexity:** Low

#### A9. Re-tapping the active tab may not return to its root — *Potential*
- **Evidence:** `NinetyApp.kt:178-184` navigates with `restoreState = true` whenever `currentRoute != tab.route`. From a pushed Day or Timetable, tapping the owning tab restores the saved stack, which is likely the same pushed screen.
- **Fix:** if the tapped tab is already selected, `popBackStack(tab.route, inclusive = false)`.
- **Priority:** Low · **Complexity:** Low

### B. Daily recording

#### B1. Record-changing actions are bare icons, and icons are reused with different meanings — *Confirmed* — **Critical**
- **Location:** period sheet, expanded lab group, Duty Leave cards, Settings semester cards, Timetable header.
- **Evidence:** `phone-light-period-sheet.png` shows **Present** and **Absent** as labelled buttons and then four unlabeled glyphs (`DayScreen.kt:513-520`). Labels exist only in `ActionIcon`'s long-press tooltip (`Components.kt:233-243`). Reuse across the app:

  | Drawable | Meanings found |
  |---|---|
  | `ic_check` | Present · Save timetable · Approve all leave · **Complete semester** · default icon of every primary button |
  | `ic_close` | Absent · Cancel timetable edit · Reject all leave · Cancel setup |
  | `ic_history` | History tab · Clear record · Restore period · Reopen semester |
  | `ic_courses` | Courses tab · Substitute course · Browse semester |
  | `ic_minus` | "Mark not conducted" action · Not recorded chip · Not conducted chip · Not counted chip |
  | `ic_clock` | Leave pending chip · Move all to pending · Change timezone |
  | `ic_calendar` | Timetable · Pick date · Change effective date · every empty state |
  | `ic_leave` | Duty Leave tab · Apply duty leave |
  | `ic_arrow_right` | Continue · Activate semester |

- **Impact:** the features the brief calls out — duty leave, cancelled classes, substitutions, clearing a mistake — are present but effectively undiscoverable. A user cannot predict what the book icon in a period sheet does, having learned it as "Courses". Touch users rarely long-press to find out.
- **Fix:** rule: *an icon may stand alone only for universal navigation (back, close, more, add, expand, previous/next) or when the same icon and meaning are visible with a label elsewhere on the same screen.* Apply it:
  - Period sheet → a vertical list of labelled rows under Present / Absent: `Duty leave` · `Class not held` · `Different course was taught` · `Clear mark`.
  - Leave card → text buttons **Approve** / **Reject** (see E2).
  - Semester card → text buttons **View**, **Finish semester**, **Reopen** (see G1).
  - Timetable header → **Cancel** / **Save** text buttons.
  - Give "Clear mark" its own eraser/undo-style icon so the clock-arrow means only one thing.
- **Why:** this is the cheapest change with the largest effect on discoverability. It is consistent with the brand commitment to keep text on consequential decisions.
- **Priority:** Critical · **Complexity:** Low

#### B2. Present / Absent controls vanish once a period is recorded — *Confirmed*
- **Location:** period rows on any day.
- **Evidence:** `DayScreen.kt:452` shows the ✓ ✕ pair only when `!session.recorded`. A recorded row shows a chip, and can be changed only by tapping the row (`:467-469`), which has no visual affordance, to open the modal sheet.
- **Impact:** correcting a mis-tap after the 4-second undo has gone takes a hidden gesture and a modal. Users who never discover the row tap cannot correct a mark.
- **Fix:** keep a two-segment control on every writable, countable row, right-aligned: `[ ✓ Present | ✕ Absent ]`. The recorded outcome is the filled segment; tapping the other segment switches it; no sheet. Duty-leave rows show a third, non-interactive filled state ("Leave · pending"). A trailing `⋯` (or the row tap) opens the labelled sheet from B1 for the rarer actions.
- **Why:** state and control become the same element, so the row always shows both what was recorded and how to change it. Record and correct are both one tap.
- **Alternatives considered:** swipe right/left for present/absent — fast, but undiscoverable and conflicts with horizontal day paging (A4). Tap-to-cycle — hides the available states. The segmented control is the standard, visible answer.
- **Priority:** High · **Complexity:** Medium

#### B3. A completed day hides its own results — *Confirmed*
- **Location:** Today and every completed past day.
- **Evidence:** `DayScreen.kt:238` — `scheduleCollapsed = complete || day.holiday`; the list is replaced by a check mark, "Attendance complete", "6 / 6 recorded" and a **Review attendance** button (`:282-323`). This applies equally to days opened from History.
- **Impact:** the screen confirms that marks exist but not what they are. The list disappears the instant the last period is tapped, so the user cannot see what they just recorded. A student who opens a past date to check it sees a blank success state and must tap again.
- **Fix:** never collapse. When complete, replace the progress bar and the bulk button with one compact line — `✓ All 6 recorded · 5 present · 1 absent` — and leave the period list visible with its segmented controls. Keep collapsing only for holidays, where the list really is irrelevant.
- **Why:** the summary gives closure and the list gives verification. The spec asks for "a clear completion state" with details "available"; a visible list satisfies both. `PROJECT_SPEC.md` §4.2 mentions "a review action", so this is a deliberate change to that sentence and should be approved by the owner.
- **Priority:** High · **Complexity:** Low

#### B4. Partial lab attendance takes four interactions and a modal, and the group collapses mid-edit — *Confirmed*
- **Location:** grouped lab card.
- **Evidence:** per-period rows appear only after the chevron (`DayScreen.kt:384-386, 401-406`) and each opens the bottom sheet. The `expanded` state is keyed on `remaining == 0` (`:368-370`), so recording the last period resets it to collapsed.
- **Impact:** "I left after the second period" — a routine lab situation — is the slowest recording path in the app, and the card snaps shut while the user is still working in it.
- **Fix:** when expanded, give each period row the same inline segmented control as B2. Keep the group-level control for "all three". Key the expanded state on the group id only. After recording, show the three outcomes with their period labels (`P3 ✓  P4 ✓  P5 ✕`), not three anonymous chips (`:396-399`).
- **Why:** three independent units stay visible as three units (a product rule), and each is one tap.
- **Priority:** High · **Complexity:** Medium (shares the component from B2)

#### B5. Three different states look identical — *Confirmed*
- **Location:** `OutcomeChip`, used on Day and Duty Leave.
- **Evidence:** `Components.kt:106-122` — "Not conducted", "Not counted" and "Not recorded" all use `surfaceVariant` with `ic_minus`. The only difference is the TalkBack description. Present and Absent chips are also icon-only.
- **Impact:** a cancelled class, an extra class that does not count, and a period the student forgot to mark are indistinguishable. Two of them need no action; one distorts the percentage. `PRODUCT.md` requires these states to "remain distinguishable".
- **Fix:** text chips for every non-present/absent state: `Not held` (struck-through course name, no control), `Doesn't count` (outlined chip), `Not marked` (error-outlined chip on past days, neutral today), `Leave · pending` / `Leave · approved` / `Leave · rejected`. With B2 in place, Present and Absent are shown by the segmented control and need no chip.
- **Priority:** High · **Complexity:** Low

#### B6. The quick ✓ and ✕ are small, adjacent, and far from the thumb — *Confirmed (layout), Potential (error rate)*
- **Evidence:** `DayScreen.kt:453-457` — two 22 dp glyphs in 48 dp targets, 8 dp apart, left-aligned under the course name. In `phone-light-today.png` they sit in the left third of the screen with nothing separating them.
- **Impact:** opposite outcomes next to each other with no boundary invite mis-taps, and a right-handed thumb has to reach across.
- **Fix:** covered by B2 — right-aligned segments with a visible outline and at least 56 dp width each.
- **Priority:** Medium · **Complexity:** — (part of B2)

#### B7. Undo is a 4-second snackbar that fires on every tap — *Confirmed*
- **Evidence:** `NinetyApp.kt:168-174` shows a `Short` snackbar for each new batch. Single marks are all labelled "Attendance updated" (`RoomNinetyRepository.kt:715`). Thirty batches are retained (`:700`) but only the newest is ever reachable.
- **Impact:** marking six periods one by one produces six snackbars over the bottom of the list. The label does not say what changed. Once it fades there is no undo, even though the data is kept.
- **Fix:** (1) with B2, single marks no longer need a snackbar — the control itself is the undo; keep the haptic. (2) Keep snackbars for bulk and structural actions, with specific labels ("Marked 6 periods present", "Mon 9 Nov marked as holiday") and `Long` duration. (3) Optional: a "Recent changes" list in Settings exposing the retained batches.
- **Priority:** Medium · **Complexity:** Low (1–2), Medium (3)

#### B8. The day menu mixes unrelated actions under unclear names — *Confirmed*
- **Evidence:** `DayScreen.kt:267-278` — four items, two with icons and two without. "Copy a weekday here" is the working-Saturday feature but does not say so. The weekend empty state has an empty body (`:326-327`). The holiday banner reads "classes do not count today" on any date (`:253`).
- **Impact:** a student facing a working Saturday sees "No classes on weekends" and nothing else. "Copy a weekday here" is recognisable only to someone who already knows the mechanism.
- **Fix:** rename and group:
  - `No classes today (holiday)` / `Undo holiday`
  - `Use another day's timetable…` → dialog title "Which day's timetable was followed?"
  - `Add an extra class…`
  - `Duty leave…`

  Give all four icons or none. On a weekend with no classes, show a secondary button in the empty state: **Working day? Use a weekday timetable**. Change the banner to "Holiday — classes don't count".
- **Priority:** Medium · **Complexity:** Low

#### B9. Nothing can be planned ahead — *Confirmed; a policy decision*
- **Evidence:** `RoomNinetyRepository.kt:788` rejects future holidays; `:814`, `:879` reject future copied days and extras; the menu items are disabled without explanation (`DayScreen.kt:269-275`, `writable = !future`).
- **Impact:** colleges announce holidays and working Saturdays in advance. The student must remember to return on the day; if they forget, the day shows up later as unrecorded.
- **Fix (two steps):** *now* — when a future date is open, show one line under the header: "You can change this day once it arrives." *Later* — allow holidays on future dates. This touches materialisation and must not alter totals (a holiday contributes zero either way), so it needs its own tests. Copying and extras can stay same-day.
- **Priority:** Medium · **Complexity:** Low (message), Medium–High (future holidays)

#### B10. The extra-class dialog uses typed times and a disguised dropdown — *Confirmed*
- **Evidence:** `DayScreen.kt:575-590` — the course selector is a `TextButton` showing the first course's name; start and end are free-text "HH:MM" fields defaulting to 17:00–18:00. Settings uses a Material time input for the reminder (`SettingsScreen.kt:286`).
- **Fix:** an outlined dropdown field labelled "Course"; two tappable time fields that open the Material time picker; show "Doesn't count toward attendance" as helper text when the checkbox is off.
- **Priority:** Medium · **Complexity:** Low

#### B11. Today gives no hint of where each course stands — *Opportunity*
- **Evidence:** period rows show only time and name. `observeCourseStats` already supplies allowance and recovery figures.
- **Impact:** the question a student most often has when opening the app — "can I afford to miss this one?" — needs a tab switch and a scan.
- **Fix:** one supporting line under each course name on today's and future rows: `92.7 % · can miss 1` or, in error colour, `89.2 % · attend next 3`. Omit when the course has no recorded attendance.
- **Why:** puts the decision next to the class it concerns. It is small text, not a new panel.
- **Priority:** Medium · **Complexity:** Medium

#### B12. The reminder does the minimum — *Confirmed*
- **Evidence:** off by default and never offered during setup (`AppSettings.reminderEnabled = false`); the notification only opens the app (`ReminderNotifier.kt:50-61`).
- **Fix:** offer the reminder on the last setup step ("Remind me at 7 pm to mark attendance"). Add a notification action **All present** that calls the existing `markAllPresent`, then updates the notification to "Marked 6 periods present".
- **Priority:** Medium · **Complexity:** Medium

### C. Courses and the meaning of the numbers

#### C1. Unrecorded past days are excluded from the percentage and barely surfaced — *Confirmed* — **Critical**
- **Location:** Today, Courses, History.
- **Evidence:** `Attendance.kt:109-115` skips unrecorded periods in both numerator and denominator (correct per spec). The UI shows this as 12 sp red text "Past records missing" on the course row (`CoursesScreen.kt:171`), which is not tappable, and as a "Needs attention" list below the calendar (`HistoryScreen.kt:90-101`). Today shows nothing. The reminder covers only the current day.
- **Impact:** a student who skips logging for a week sees percentages built on stale data, with no prompt where they look every day. If the missed week included absences, the app is quietly optimistic. The spec asks the app to "disclose limitations in statistics derived from partial data".
- **Fix:**
  1. **Today:** when any earlier day is incomplete, show one strip above the period list: `3 earlier days not marked · Catch up`. "Catch up" opens the oldest incomplete day in the pager with a **Next unmarked day** button after completion.
  2. **Courses:** replace the red text with a tappable line: `4 periods not marked — % may be off ›`, opening the list of dates for that course.
  3. **Courses header:** when anything is unrecorded, add "Some days aren't marked" beneath "90 % minimum".
- **Why:** the engine's honesty about incomplete data only helps if the student sees it. One strip in the daily path closes the loop.
- **Priority:** Critical · **Complexity:** Low–Medium

#### C2. A percentage that depends on unapproved leave looks final — *Confirmed*
- **Evidence:** the headline figure includes pending duty leave (`Attendance.kt:75`, by spec §5). The only marker is the word "Provisional" in grey `labelMedium` (`CoursesScreen.kt:169`); see "Networks Lab 95.2 %" in `courses.png`. The confirmed-only figure is in the collapsed details, labelled "Confirmed (without pending duty leave)" (`:224`).
- **Impact:** "95.2 % · Can miss 1 period" reads as safe. If the leave is rejected the course may already be below 90 %. The spec says not to "misrepresent provisional safe skips as certain".
- **Fix:** keep the provisional figure as primary. Directly under it, when pending leave exists: `90.5 % if leave is rejected`. Prefix the allowance line: `Can miss 1 period (counting 3 pending leave)`. Make the line a link to the pending requests for that course.
- **Priority:** High · **Complexity:** Low

#### C3. Rounding can display "90.0 %" for a course that fails — *Confirmed by arithmetic*
- **Evidence:** `Format.kt:28` rounds to one decimal; eligibility uses integers (`Attendance.kt:79`). Example: 188 of 209 = 89.952 % → shown as "90.0 %", in red, with "Attend 1 period to reach 90 %".
- **Impact:** a rare but self-contradictory display at the exact moment the number matters.
- **Fix:** truncate instead of rounding (`floor` to one decimal), so a failing course never shows 90.0 %. The decision logic is untouched.
- **Priority:** Low · **Complexity:** Low

#### C4. A course with zero margin looks the same as a safe one — *Confirmed*
- **Evidence:** `CoursePercentage` is red below the minimum and neutral otherwise (`CoursesScreen.kt:195-199`). "No safe skips" is grey body text (`:207`). "Engineering Mathematics 90.3 %" in `courses.png` looks as calm as 95.2 %.
- **Impact:** the course most likely to slip gets no emphasis.
- **Fix:** three tiers, each with icon and text: **below** (error colour, "Attend the next 3 periods"), **at the limit** (amber, "No absences to spare"), **safe** (neutral, "Can miss 2 periods"). Add an amber role to the theme; spec §7.1 reserves amber for status. Add a one-line summary under the heading: `1 below 90 % · 1 at the limit`.
- **Priority:** Medium · **Complexity:** Low

#### C5. Each course row has two small controls and is not itself tappable — *Confirmed*
- **Evidence:** a ⋮ menu and a chevron, stacked at the right edge (`CoursesScreen.kt:148-159, 178-183`). The row has no click handler. Details expand inline.
- **Impact:** two adjacent 48 dp targets with different results; the natural gesture — tap the course — does nothing.
- **Fix:** make the whole row open a **Course** screen: the percentage with its qualifiers, the breakdown, unmarked dates, leave for this course, and the actions (Rename, Attendance before tracking, Stop tracking) as labelled rows. Remove both the ⋮ and the chevron from the list.
- **Alternative:** keep inline expansion but make the whole row the toggle and move the actions into the expanded area as text buttons. Cheaper, but the row grows long once C1 and C2 add content. The detail screen is recommended.
- **Priority:** Medium · **Complexity:** Medium

#### C6. "Retire course" is jargon, permanent, and not undoable — *Confirmed*
- **Evidence:** `retireCourse` writes no undo batch (`RoomNinetyRepository.kt:530-554`); no un-retire path exists in the UI or repository. The dialog says "Scheduling stops from tomorrow" (`CoursesScreen.kt:111-115`). Retired courses stay in the main list, marked by a grey label.
- **Impact:** a mis-tap permanently removes a course from the timetable; restoring it means re-adding it to every slot — and a retired course cannot be put back on the timetable at all (`validateTemplate`, `:605`).
- **Fix:** rename to **Stop tracking this course**. Dialog: "It comes off the timetable from tomorrow. Everything recorded so far is kept. This can't be undone from the app." Move stopped courses into a collapsed "No longer tracked" section. Later, add **Resume tracking** (clears the flag; the user re-adds timetable slots).
- **Priority:** High · **Complexity:** Low (wording, grouping), Medium (resume)

#### C7. Adding a course later cannot record earlier attendance and does not lead to the timetable — *Confirmed*
- **Evidence:** `CoursesScreen.kt:106` calls `addCourse(input, null)`; the dialog has only name and type (`:250-271`), unlike the setup dialog.
- **Impact:** a course added in week 6 starts at zero with no prompt; the student must find ⋮ → Edit opening balance. The new course also appears nowhere until the timetable is edited, and nothing says so.
- **Fix:** reuse the setup dialog's optional "Attendance so far" section. After saving, show a snackbar: `Added. Put it on the timetable?` with action **Timetable**.
- **Priority:** Medium · **Complexity:** Low–Medium

#### C8. The opening-balance dialog speaks in bookkeeping terms — *Confirmed*
- **Evidence:** title "Opening balance · {course}", "Before your first logging date", "First logging date: 2026-10-16" as a raw ISO string (`CoursesScreen.kt:283-289`). Four numeric fields with no numeric keyboard (no `KeyboardOptions` anywhere in the dialog). Errors come straight from the domain: "Approved DL exceeds remaining conducted periods." (`Attendance.kt:46`), "The first logging date is locked once attendance is recorded." (`RoomNinetyRepository.kt:569`).
- **Impact:** the mid-semester joiner — a core target user — has to decode "opening balance" and "logging date" and type digits on a full keyboard.
- **Fix:** title "Attendance before you started tracking". Fields: "Tracking starts on" (formatted date; shown read-only with "Can't change after you've marked attendance" once locked), "Classes held", "Attended", "Duty leave — approved", "Duty leave — pending". Numeric keyboard on all four. Live helper under the fields: "Absent: 5" (held minus the rest), turning to an error if negative.
- **Priority:** Medium · **Complexity:** Low

#### C9. Pending "opening" leave is resolved on Courses, not with the rest of duty leave — *Confirmed*
- **Evidence:** "Approve opening duty leave" / "Reject opening duty leave" are in the expanded course details (`CoursesScreen.kt:230-235`). The Duty Leave screen shows only a count, behind the ⓘ toggle (`LeaveScreen.kt:81-85`).
- **Impact:** the same kind of decision lives in two places depending on when the leave was entered.
- **Fix:** on the Duty leave screen, add a section "From before you started tracking" with one row per course that has pending opening leave, and **Approve** / **Reject** buttons. Keep a link from the Course screen.
- **Priority:** Medium · **Complexity:** Low

#### C10. The app never explains how it calculates — *Confirmed*
- **Evidence:** "90 % minimum" is the only statement of the rule (`CoursesScreen.kt:82`). "Credited", "Physically present", "Provisional" are unexplained (`:219-225`).
- **Fix:** an ⓘ beside "90 % minimum" opening one short sheet: what counts (present, approved leave, pending leave for now), what does not (absent, rejected leave), what is ignored (holidays, classes not held, extra classes unless ticked, unmarked periods), and that "can miss" is counted in periods. Link the same sheet from Settings.
- **Priority:** Medium · **Complexity:** Low

### D. Timetable

#### D1. The "From {date}" control is unexplained and accepts dates that then fail — *Confirmed*
- **Evidence:** a bare row "From Fri, 13 Nov" with a calendar icon (`TimetableScreen.kt:91-99`). The date picker is unrestricted; a past date is rejected only after Save, by snackbar (`RoomNinetyRepository.kt:611-612`). The default uses the device date, not the college date (`TimetableScreen.kt:71`).
- **Fix:** label it "Changes apply from" with helper text "Earlier days keep the old timetable." Disable past dates in the picker (`SelectableDates`). Derive "today" from the semester's zone as the other screens do.
- **Priority:** Medium · **Complexity:** Low

#### D2. Leaving the editor discards changes silently — *Confirmed*
- **Evidence:** the ✕ calls `cancelEditing()` directly (`TimetableScreen.kt:82`); there is no `BackHandler`, and the draft lives in a ViewModel scoped to the route, so system Back also drops it.
- **Fix:** if the draft differs from the saved template, confirm: "Discard timetable changes?" — **Keep editing** / **Discard**.
- **Priority:** Medium · **Complexity:** Low

#### D3. Filling the timetable is one dropdown per slot — *Confirmed*
- **Location:** setup step 3 and the timetable editor (`WeeklySchedule.kt:63-95`).
- **Evidence:** 30 slots; each is tap row → scroll menu → tap course. A three-period lab is three such sequences. No way to repeat a day.
- **Impact:** the longest task in onboarding; roughly 60 taps for a full week.
- **Fix — "pick a course, then tap its periods":** a row of course chips above the six period rows; the selected chip is "armed"; tapping a period assigns it, tapping again clears it. Add **Copy from…** per weekday for similar days. Keep the dropdown as the accessible fallback (it already has good content descriptions).
- **Alternative:** a full 5×6 grid on one screen — fewer day switches, but full course names do not fit a 60 dp cell and the project deliberately dropped short names. The chip-then-tap list keeps full names.
- **Priority:** Medium · **Complexity:** Medium

#### D4. Conflicts are reported after saving, in internal terms — *Confirmed*
- **Evidence:** "Saved with 2 conflicts — Recorded or cancelled periods kept their stored course: · Fri 13 Nov P3 kept Networks Lab" (`TimetableScreen.kt:125-140`). `previewTemplateConflicts` exists in the repository interface and is never called.
- **Fix:** after a save with conflicts: title "Timetable saved", body "2 periods you had already marked were left as they were:" followed by the list with full period labels. Better: call the preview before saving when the effective date is today and show the same list with **Save anyway** / **Cancel**.
- **Priority:** Medium · **Complexity:** Low–Medium

#### D5. Smaller timetable points — *Confirmed*
- The weekday selector always opens on Monday (`WeeklySchedule.kt:50`). **Fix:** open on today's weekday (Monday at weekends).
- "Earlier versions" lists dates that cannot be opened (`TimetableScreen.kt:111-120`). **Fix:** remove, or show as one line "Changed 2 times · last on 9 Nov".
- "No timetable yet" is plain text with no action (`:104-108`). **Fix:** use `EmptyState` with a **Set up timetable** button.
- **Priority:** Low · **Complexity:** Low

### E. Calendar (History) and duty leave

#### E1. The calendar's markings are undocumented, colour-only, and partly invisible — *Confirmed*
- **Evidence:** `HistoryScreen.kt:173-186` — a 5 dp dot under each date: blue = complete, red = incomplete, grey = holiday; a second grey dot for a copied/custom day. No legend. Holidays get a `tertiaryContainer` fill (`:146`) on the card surface: `#EFF1F4` on `#FFFFFF` is **1.13 : 1** in light, `#25282E` on `#191B1F` is **1.17 : 1** in dark.
- **Impact:** complete versus incomplete is a blue/red distinction on a 5 dp dot — unusable for colour-blind users and hard for everyone. Holidays are effectively unmarked. The spec requires the calendar to make "missing, incomplete, holiday, and special timetable days obvious".
- **Fix:** differ by shape as well as colour and add a legend row under the grid:
  - complete — small filled dot
  - not marked — hollow ring in error colour (or the date number in error colour with an underline)
  - holiday — "H" or a struck-through date in secondary colour, plus a fill that reaches 3 : 1
  - different timetable — small corner triangle

  Legend: `● marked   ○ not marked   H holiday   ◣ different timetable`.
- **Priority:** High · **Complexity:** Low

#### E2. Approving leave requires expanding a card to find unlabeled icons — *Confirmed*
- **Evidence:** `LeaveScreen.kt:149-155` — ✓ ✕ 🕓 with tooltips "Approve all", "Reject all", "Move all to pending", shown only when expanded (`phone-light-leave-details.png`). ✓ and ✕ mean Present and Absent on the previous tab.
- **Impact:** the one thing a pending request needs — a decision — is two steps away and unnamed.
- **Fix:** on a collapsed pending card show **Approve** (filled tonal) and **Reject** (text). On a decided card show the status and a **Change** text button revealing the three options. Keep per-period control in the expanded view, using a three-option segmented control per row in place of the ⋮ menu.
- **Priority:** High · **Complexity:** Low

#### E3. The "needs attention" list is capped at twelve and slow to clear — *Confirmed*
- **Evidence:** `HistoryScreen.kt:92` — `incomplete.take(12)`, oldest first, with no "show more"; the header shows the true total. Each card reads "{date} · Unrecorded" with no period count. Clearing one day is tap → Mark all present → Back.
- **Impact:** with 20 incomplete days the user sees 12 and a header saying 20. Clearing a two-week backlog is ~30 taps.
- **Fix:** show all (it is a lazy list), newest first, each as `Mon 9 Nov · 4 of 6 not marked`. Add the catch-up flow from C1. Do **not** add a single "mark every day present" button — it would make false records too easy.
- **Priority:** High · **Complexity:** Low (list), Medium (catch-up flow, shared with C1)

#### E4. A leave spanning several days becomes several requests — *Confirmed*
- **Evidence:** every `applyDutyLeave` call creates a new event (`RoomNinetyRepository.kt:948-949`) and the dialog works on one day. `DlEventView.dateLabels` shows the model supports multiple dates per event.
- **Impact:** a three-day tournament yields three cards, approved three times, and the "requests" count is inflated.
- **Fix:** in the duty-leave dialog add "Part of" → `New request` (default) or a recent pending request by name. Needs an optional `eventId` parameter on `applyDutyLeave`; credit rules are unchanged.
- **Priority:** Medium · **Complexity:** Medium

#### E5. A leave request cannot be withdrawn or deleted — *Confirmed*
- **Evidence:** no delete on the screen or in `NinetyRepository`; requests whose periods were all re-marked remain as "No periods attached" (`LeaveScreen.kt:119`).
- **Fix:** add **Remove request** in the expanded card (confirm; periods return to "not marked"; undoable through the existing batch mechanism). Hide or auto-remove empty requests.
- **Priority:** Medium · **Complexity:** Low–Medium

#### E6. Smaller calendar and leave points — *Confirmed unless noted*
- Summary line "1 date · 1 request · 3 dated periods" (`LeaveScreen.kt:75`): "dated periods" is internal vocabulary. **Fix:** "3 periods on 1 day · 1 request". Replace the ⓘ toggle (`:71`) with an always-visible second line "3 pending · 0 approved · 0 rejected".
- Leave dialog subtitle "Pending approval · provisional credit" (`DayScreen.kt:633`). **Fix:** "Counts as attended until it's approved or rejected."
- Period rows inside a request do not link to their day. **Fix:** make the row open that date.
- Month navigation is unbounded and dates before the semester start show "No classes scheduled — Add a class or copy a weekday from the menu" (`DayScreen.kt:326`). **Fix:** stop at the semester's first month; grey out earlier dates; add a **Today** button when another month is shown.
- *Potential:* day cells are fixed at 48 dp (`HistoryScreen.kt:152`) inside columns about 44.6 dp wide on a 360 dp phone. Check for clipping or overlap on a narrow device.
- **Priority:** Low · **Complexity:** Low

### F. Setup and onboarding

#### F1. Setup never explains the choice a mid-semester user faces — *Confirmed*
- **Evidence:** step 1 opens directly on a form with no introduction (`phone-light-setup-semester.png`). Earlier attendance is an unexplained checkbox, "Opening attendance", inside the course dialog (`SetupScreen.kt:290-294`). If the start date is in the past and the box is left unticked, every weekday since the start is generated as unrecorded (`ensureMaterialized`), so the first screen after setup is a backlog. The date is shown as a raw ISO string (`:297`); abbreviations "Approved DL" / "Pending DL" appear before the term has been introduced (`:310-317`).
- **Impact:** the two real situations — "semester starts now" and "I'm joining in week 6" — are not distinguished. A week-6 student who misses the checkbox starts with weeks of red.
- **Fix:** after the start date, when it is more than a few days in the past, ask one question:
  > **Classes have already started. How do you want to begin?**
  > ◉ Enter my attendance so far for each course *(recommended)*
  > ○ Mark every past day myself

  If the first option is chosen, the course dialog shows "Attendance so far" expanded with "Tracking starts on" pre-filled to today. If the start date is today or later, show neither. Add one line under the wizard title on step 1: "Three short steps: semester, courses, weekly timetable."
- **Priority:** High · **Complexity:** Medium

#### F2. The timezone field is prominent and its picker is broken — *Confirmed*
- **Evidence:** "College timezone" is the third field on step 1. Its menu shows the current zone, eight presets, then alphabetical zones cut at 40 entries (`SetupScreen.kt:196-197`), so most of the world's zones cannot be chosen. Values are raw IANA ids.
- **Impact:** a question almost nobody needs to answer, asked first, with a control that cannot reach most answers.
- **Fix:** default to the device zone and collapse to one line: "Times follow this phone (India Standard Time) · Change". "Change" opens a searchable full list.
- **Priority:** Medium · **Complexity:** Low–Medium

#### F3. Course errors appear later and elsewhere — *Confirmed*
- **Evidence:** the course dialog's Save does no validation (`SetupScreen.kt:322-326`); a blank name is saved as "Untitled" (`:216`). Checks run on **Continue** (`SetupViewModel.kt:99-121`) and print below the list, e.g. "Present and duty leave cannot exceed conducted periods for {name}."
- **Fix:** validate inside the dialog: disable Save while the name is blank or duplicated; show count errors beneath the fields; numeric keyboards.
- **Priority:** Medium · **Complexity:** Low

#### F4. An empty timetable passes without comment — *Confirmed*
- **Evidence:** step 3 is not validated (`SetupViewModel.kt:81-85`); the review shows "1 courses · 0 timetable slots" (`phone-light-setup-review.png`). With no template the Today screen says "Add a class or copy a weekday from the menu", and copying then fails with "No weekly timetable exists for that date." (`RoomNinetyRepository.kt:817`).
- **Fix:** on Continue with nothing assigned: "Your timetable is empty, so no classes will appear. **Add classes** / **Skip for now**." Flag any course that is on no day in the review.
- **Priority:** Medium · **Complexity:** Low

#### F5. Smaller setup points — *Confirmed*
- Review text "Data Structures (opens at 30+2DL of 40)" and "0 timetable slots" (`SetupScreen.kt:257-265`). **Fix:** "Data Structures — 32 of 40 so far" and "28 classes a week"; also fix "1 courses".
- The step indicator reuses `PeriodProgress`, so TalkBack announces "2 of 4 periods recorded" (`Components.kt:261`). **Fix:** pass a description parameter ("Step 2 of 4").
- ✕ cancels setup and the bin removes a course (and its timetable slots) with no confirmation (`SetupScreen.kt:102, 225`; `SetupViewModel.kt:68-71`). **Fix:** confirm cancel when anything has been entered; snackbar-undo for course removal.
- The start date has no default. **Fix:** leave empty but open the picker on today.
- **Priority:** Low · **Complexity:** Low

### G. Settings, semesters and data

#### G1. A bare check mark beside "Active" finishes the semester — *Confirmed*
- **Evidence:** `SettingsScreen.kt:189-207`; in `phone-light-settings.png` the card reads `📖  Active  ✓`. The ✓ is "Complete semester"; the book is "Browse"; an arrow is "Activate"; a clock-arrow is "Reopen". A confirmation protects completion; reopening has none and can fail with "Another semester is already active." (`RoomNinetyRepository.kt:481`).
- **Impact:** the check reads as a status mark, not a button. The most consequential semester action is the least labelled control in Settings.
- **Fix:** each semester is a list row: name, "Started 16 Oct · current" or "· finished 20 Mar". Tapping it opens a **Semester** screen with labelled actions: View records · Rename / change start date · Finish semester · Reopen. Only **+ New semester** stays on the Settings list.
- **Priority:** High · **Complexity:** Low–Medium

#### G2. A semester cannot be corrected after creation — *Confirmed*
- **Evidence:** `updateSemester(id, name, startDate, zoneId)` is implemented with history safeguards (`RoomNinetyRepository.kt:454-469`) and never called from the UI. There is no delete.
- **Impact:** a typo in the name or a wrong start date is permanent. A wrong start date also generates wrong days.
- **Fix:** expose rename / start date / timezone on the Semester screen from G1, using the existing method; present its "cannot move past recorded history" rule as inline helper text. Deleting a semester is a larger decision — defer, and until then let a mistaken semester be finished and labelled.
- **Priority:** High · **Complexity:** Medium

#### G3. Semesters have three states where users expect two — *Confirmed*
- **Evidence:** active; not active and not done ("Activate" arrow, `SettingsScreen.kt:197-199`); done. Creating a new semester leaves the previous one in the middle state (`createSemester` only switches the active pointer, `RoomNinetyRepository.kt:445-449`).
- **Fix:** when a new semester is created while another is current, ask "Finish {old name}? You can still view it." (default yes). Display only **Current** and **Finished**; if a middle-state semester exists, label it "Paused" with a **Make current** text button.
- **Priority:** Medium · **Complexity:** Low

#### G4. Backup gives little reassurance — *Confirmed*
- **Evidence:** fixed file name `ninety-backup.json` (`SettingsScreen.kt:217`). Success message "Backup saved to {uri.lastPathSegment}" (`:95-97`) prints a provider id such as `primary:Download/…` or `msf:1234`. No record of when the last backup was made, though the app has no other copy of the data. The restore preview prints the raw timestamp ("Created 2026-11-13T19:14:02.123Z", `Backup.kt:105`, `SettingsScreen.kt:264`) and counts "classes" and "records". The Restore button is not styled as destructive. The export button uses an upload icon and restore a download icon — the reverse of how many users read them.
- **Fix:** file name `ninety-backup-2026-11-21.json`. Message "Backup saved". Under the buttons: "Last backup: 3 weeks ago" (needs one stored timestamp) and, after 30 days, a quiet "Back up" hint in Settings. Rename the buttons **Save a backup** / **Restore from a backup**. Restore dialog: "Backup from 13 Nov 2026, 7:14 pm · 2 semesters · 9 courses · 412 marked periods. **This replaces everything in the app now.**" with a destructive-coloured **Replace my data** and a secondary **Save a backup first**.
- **Priority:** Medium · **Complexity:** Low (one stored timestamp is the only data change)

#### G5. Smaller settings points — *Confirmed*
- Theme is three icon-only chips with no label (`SettingsScreen.kt:135-144`). **Fix:** segmented control "System · Light · Dark".
- "Haptic feedback" sits under Appearance. **Fix:** rename the section "Display & feedback", or move the switch beside the reminder under "Recording".
- Reminder time is a text button "Reminder time: 19:00", always 24-hour (`:166-169, 278`). **Fix:** a row "Time · 7:00 pm" following the device's 12/24 h setting; helper "Only when today still has unmarked classes."
- About is behind an ⓘ in the heading; its dismiss-slot button is "Font license"; no version shown (`:241-244`). **Fix:** an "About" row at the bottom: version, "Works offline. Nothing leaves this phone.", Licences.
- **Priority:** Low · **Complexity:** Low

### H. Cross-cutting

#### H1. The same concept has several names — *Confirmed*
See §7.6 for the full replacement table. Examples: *opening attendance / opening balance / opens with / opening DL*; *duty leave / DL / leave*; *not conducted / cancelled / restore period*; *done / complete / finished / reopen*; *period / class / slot*; *unrecorded / not recorded / incomplete / past records missing / needs attention*.
- **Impact:** each synonym makes the user wonder whether it is a different thing.
- **Fix:** adopt one glossary and apply it everywhere.
- **Priority:** Medium · **Complexity:** Low

#### H2. Internal messages can reach the user — *Confirmed*
- **Evidence:** `userMessage()` passes through any `IllegalArgumentException` / `IllegalStateException` message (`AppViewModel.kt:33-37`). The repository contains messages such as "Unknown class period.", "Invalid course ownership.", "Settings unavailable.", and many `check(...)` calls with no message, which Kotlin reports as "Check failed."
- **Fix:** give user-facing rule violations their own exception type (as `BackupException` already does) and show only those; everything else becomes "Something went wrong. Your data is safe."
- **Priority:** Low · **Complexity:** Low–Medium

#### H3. All text is hard-coded — *Confirmed*
- **Evidence:** `strings.xml` holds only `app_name`. Plurals are built by hand ("1 courses" in `SetupScreen.kt:257`). Times are always 24-hour (`PeriodTimes.label`).
- **Impact:** no localisation, inconsistent plurals, and the copy pass in H1 means editing a dozen Kotlin files.
- **Fix:** move strings to resources with `plurals` as part of the H1 pass; follow the device's time format for display only.
- **Priority:** Low · **Complexity:** Medium

#### H4. Large-text behaviour is unverified and already drops information — *Potential*
- **Evidence:** tab labels are removed above 1.3× (A1). `PROJECT_PROGRESS.md` records that the phone large-text visual review was not completed.
- **Fix:** after the label change, capture every screen at 1.3× and 2.0× and check for truncation in the segmented controls proposed in B2.
- **Priority:** Medium · **Complexity:** Low

---

## 4. Screen-by-Screen Audit

### 4.1 Setup wizard
**Works:** four clear steps with progress; Back preserved between steps; keyboard handling; date picker instead of typed dates; cancel offered only when another semester exists.
**Problems:** F1 (no mid-semester guidance), F2 (timezone), F3 (late validation), F4 (empty timetable), D3 (slow slot assignment), F5 (review wording, accessibility label, silent cancel).
**Verdict:** structurally sound; needs one decision question, in-dialog validation and a faster timetable step.

### 4.2 Today / Day
**Works:** weekday and date are clear; segmented progress bar; **Mark all present** / **Mark remaining present** is prominent and never overwrites; lab grouping; holiday and future states are handled; haptics.
**Problems:** B1 (icon-only sheet), B2 (controls vanish), B3 (collapse on completion), B4 (lab partials), B5 (identical chips), B6 (small adjacent targets), B7 (snackbar per tap), B8 (menu naming), B9 (no planning ahead), A4/A5 (no day stepping; back stack), C1 (no backlog signal), B11 (no standing context).
**Verdict:** the single best and single most important screen. The bulk path is right; individual marks and corrections need the persistent control.

### 4.3 Period sheet
**Works:** course, period and time in the title; Present/Absent labelled; extra-class "Counts toward attendance" switch is labelled.
**Problems:** B1; no indication of the current outcome; the substitute list is a dropdown anchored inside the sheet (`DayScreen.kt:535-544`); "Remove extra class" is an icon.
**Verdict:** rebuild as a short labelled list. With B2 it becomes a secondary surface.

### 4.4 Day dialogs (holiday, copy weekday, extra class, duty leave) — *code only*
**Works:** holiday confirmation explains the effect; duty leave warns per period when it will replace a mark, then confirms; copy reports what was kept.
**Problems:** B8 (copy naming), B10 (extra class inputs), E4 (single-day leave), E6 (leave subtitle), "Recorded periods kept … This is a custom day." (`DayScreen.kt:193`) is internal phrasing.
**Verdict:** logic is careful; wording needs the glossary pass.

### 4.5 Courses
**Works:** large percentage; red below the minimum; an actionable sentence per course; "No attendance recorded" instead of a fake 0 %; details separate physical presence from leave.
**Problems:** C1, C2, C3, C4, C5, C6, C7, C9, C10, A3.
**Verdict:** the right content with the wrong emphasis: qualifiers are too quiet and the controls too scattered.

### 4.6 Course and opening-balance dialogs
**Works:** minimal add form; type chips.
**Problems:** C7, C8; Save shows no reason when disabled.
**Verdict:** rename and add inline help.

### 4.7 Timetable
**Works:** read-only by default with explicit edit mode; full course names; changes are dated so history is preserved; one undo for the whole save.
**Problems:** A3 (entry), D1, D2, D3, D4, D5; icon-only cancel/save (B1).
**Verdict:** safe but slow and under-explained.

### 4.8 History (Calendar)
**Works:** standard month grid; today highlighted; every date opens its day; good TalkBack state descriptions; incomplete days listed.
**Problems:** E1, E3, E6, A8, A5.
**Verdict:** needs a legend, shape-coded states and a real catch-up path.

### 4.9 Duty Leave
**Works:** grouping by request; per-period status; mixed status handled; totals distinguish dates, requests and periods as the spec requires.
**Problems:** A2, E2, E4, E5, E6, C9.
**Verdict:** a status viewer that should become the place to add and decide leave.

### 4.10 Settings
**Works:** short single page; notification permission requested only when the reminder is enabled; restore validates, previews and confirms; privacy footer.
**Problems:** G1, G2, G3, G4, G5, A7.
**Verdict:** preferences are fine; semester management needs its own screen with words.

### 4.11 Global chrome and shared states
- **Top bar:** A6.
- **Bottom bar:** A1. The sliding pill is pleasant and cheap; keep it.
- **Snackbars and undo:** B7. The underlying mechanism is a real strength.
- **Empty states:** every one uses the calendar icon (`Components.kt:163`); "No active semester. Browse or create one in Settings." has no button (`NinetyApp.kt:360`). Give each an action button where one exists.
- **Loading:** fine.
- **No major issues:** haptics, theme switching, edge-to-edge handling, date pickers, and the notification-permission flow.

---

## 5. Proposed Navigation and Information Architecture

### 5.1 Options compared

| Option | Structure | For | Against |
|---|---|---|---|
| A. Keep five tabs, add labels | Today · Courses · History · Leave · Settings | Smallest change | Duty leave and Settings — both occasional — still hold 40 % of the bar; timetable still has no home; five labels are tight at 360 dp |
| **B. Four tabs (recommended)** | Today · Courses · Calendar · Settings | Every tab is used at least weekly; four labels fit easily; duty leave and timetable get named rows next to the data they affect | Duty leave is one level deeper for viewing (not for adding) |
| C. Three tabs + gear | Today · Courses · Calendar, Settings in the top bar | Cleanest bar | Semester switching and backup become hard to find; the gear competes with the semester label |

**Recommendation: B.**

### 5.2 Proposed hierarchy

```
Bottom navigation — 4 destinations, always labelled
│
├─ Today                      day pager, opens on today
│   ├─ ‹ date › · Today chip · date picker
│   ├─ strip: "3 earlier days not marked · Catch up"      (only when true)
│   ├─ summary: progress + Mark all present  →  "✓ All 6 recorded · 5 present · 1 absent"
│   ├─ period rows with [Present | Absent] always visible
│   │    └─ ⋯ sheet: Duty leave · Class not held · Different course was taught · Clear mark
│   └─ ⋮ day menu: No classes today (holiday) · Use another day's timetable · Add an extra class · Duty leave
│
├─ Courses
│   ├─ "90 % minimum ⓘ" · "1 below 90 % · 1 at the limit"
│   ├─ row: Timetable ›            "Mon–Fri · 28 classes a week"
│   ├─ row: Duty leave ›           "3 periods pending"
│   ├─ course rows (tap → Course)
│   │    └─ Course: % + qualifiers · breakdown · unmarked dates · leave · Rename · Attendance before tracking · Stop tracking
│   ├─ + Add course
│   └─ "No longer tracked" (collapsed)
│        Timetable   → view · Edit (Changes apply from…) · Save / Cancel
│        Duty leave  → + Add duty leave · requests with Approve / Reject · "From before you started tracking"
│
├─ Calendar
│   ├─ month grid + legend · Today button
│   └─ "Not marked" list (all, newest first) → opens the day in the pager
│
└─ Settings
    ├─ Semester      current semester row → Semester screen (View · Rename/dates · Finish · Reopen) · + New semester
    ├─ Reminder      on/off · time
    ├─ Display       theme · haptics
    ├─ Backup        Save a backup · Restore from a backup · "Last backup: …"
    └─ About         version · how attendance is calculated · licences
```

Top bar on every screen: the screen title, and beneath it the semester name as a tappable label. While a non-current semester is shown: a persistent banner with **Back to {current}**.

### 5.3 What happens to existing paths

| Existing path | After |
|---|---|
| Duty Leave tab | Courses → **Duty leave** row. Adding leave also remains on the day menu, the period sheet and the lab group. |
| Courses → calendar icon → Timetable | Courses → **Timetable** row. Same route, labelled entry. |
| History tab | Renamed **Calendar**; same position and content. |
| History → date → Day (pushed) | Calendar → date → Day (one pushed entry; arrows change the date in place). |
| Today (fixed date) | Today pager; re-selecting the tab returns to today. |
| Course ⋮ menu and chevron | Tap the course → Course screen; same three actions as labelled rows. |
| Settings semester-card icons | Settings → semester row → Semester screen with labelled actions. |
| Settings ⓘ → About | Settings → About row. |
| Leave ⓘ totals toggle | Always-visible second summary line. |

Tests that reference `tab_leave`, `review_attendance`, `group_present` and similar tags (`V1UiTest`, `AppJourneyDeviceTest`, `RedesignDeviceTest`) will need updating alongside.

### 5.4 Why this is better

- Every tab answers a question asked at least weekly: *what do I mark today*, *where do I stand*, *what did I miss*, plus one home for everything else.
- Features sit beside what they change: duty leave and the timetable both alter course numbers and both live on Courses.
- Nothing is removed. Every current capability keeps a path, and most gain a label.
- It matches spec §7: "Semester/settings/backup: clear but not dominant in everyday navigation."

---

## 6. Improved User Flows

Counts are taps from opening the app.

**First run — semester starting now.** Name and start date (timezone preset) → add courses in a dialog that validates as you type → arm a course chip and tap its periods, **Copy from Monday** for similar days → review in plain words, optional reminder switch → **Start tracking** → Today.

**First run — joining mid-semester.** As above, but after choosing a past start date, answer "Enter my attendance so far (recommended)". Each course dialog then shows Classes held / Attended / Duty leave with a live "Absent: n". Today opens clean, with correct percentages and no backlog.

**Daily, all present.** Open → **Mark all present**. *1 tap.* The header becomes "✓ All 6 recorded · 6 present"; the list remains.

**Daily, one absence.** Open → **Absent** on that row → **Mark remaining present**. *2 taps.* Or mark all present first, then tap **Absent** on the one row — also 2, with no sheet.

**Correct a mark.** Open the day → tap the other segment. *1 tap.*

**Lab, attended 2 of 3.** Group **Present** → expand → **Absent** on P5. *3 taps, none modal.*

**Forgot yesterday.** Open → **‹** → **Mark all present**. *2 taps.*

**Several days behind.** Open → **Catch up** on the strip → mark the day → **Next unmarked day** → … *About 2 taps per day.*

**Duty leave for a whole day.** Day ⋮ → **Duty leave…** → **Select all** → **Apply**. For day two of the same event, choose "Part of: Sports meet". Or: Courses → Duty leave → **+ Add duty leave** → pick the date.

**Leave decided.** Courses → Duty leave → **Approve** on the card. *3 taps, all labelled.* The course percentage loses its "if leave is rejected" line.

**Holiday.** Day ⋮ → **No classes today (holiday)** → confirm. The calendar shows "H".

**Working Saturday.** Open the Saturday (pager or Calendar) → **Working day? Use a weekday timetable** in the empty state → pick the weekday.

**Class not held.** Row ⋯ → **Class not held**. The row greys with a "Not held" chip; **Restore** appears in the same sheet.

**Different course taught.** Row ⋯ → **Different course was taught** → pick the course from a list.

**Timetable change.** Courses → **Timetable** → **Edit** → adjust → "Changes apply from: today" → **Save**. If marked periods would be affected, a list appears before saving.

**Where do I stand?** Courses. At-risk and at-limit courses are counted in the header and distinguished by colour, icon and text. Tap a course for the breakdown.

**New semester.** Settings → **+ New semester** → "Finish Semester 5? You can still view it." → wizard.

**Looking at an old semester.** Settings → semester → **View records**. A banner stays on every tab until **Back to Semester 6**.

**Backup.** Settings → **Save a backup** → choose a location → "Backup saved". The row then reads "Last backup: today".

---

## 7. Visual Design and Component Improvements

The neutral palette, Manrope with tabular figures, 24 dp gutters and divider-separated rows are good and should stay. The changes below are about consistency and legibility.

### 7.1 Layout and density
- **One header per screen** (A6). Recovers about 64 dp.
- **Today above the fold:** date header → one summary line → bulk button → rows. Target: at least four period rows visible on a 360×800 dp screen (currently about three and a half on a taller device).
- **Period row:** `[time rail 52 dp] [course name, optional standing line] [segmented control, right-aligned]`, minimum height 64 dp. Drop the separate line of icons under the name.
- **Rows versus cards:** Settings and History use cards; Courses, Day and Duty Leave use divider rows. Keep cards only for genuinely grouped controls (the calendar grid, a settings group). The "needs attention" items should be rows.
- **Merge** "0 / 6 recorded" and "6 left" into one label: "2 of 6 marked".

### 7.2 Components to add or change

| Component | Change |
|---|---|
| **MarkControl** (new) | Two-segment outlined control, each segment ≥ 56×40 dp inside a 48 dp-tall touch area. Selected Present = primary container; selected Absent = error container; both carry icon and word. Becomes icon-only below 340 dp width or above 1.5× font scale, with content descriptions. |
| **StatusChip** (replaces icon-only `OutcomeChip`) | Text chips: Not marked · Not held · Doesn't count · Leave pending / approved / rejected. Each has its own icon and, where meaningful, its own colour role. |
| **ActionRow** (new) | Icon + label + optional trailing value + chevron, 56 dp. Used for Timetable, Duty leave, sheet actions, the Semester screen and Settings. |
| **ActionIcon** | Restrict to back, close, add, more, expand/collapse, previous/next. |
| **EmptyState** | Accept an icon and an optional action button; stop using the calendar for everything. |
| **Banner** | Give day-status banners (Holiday, Different timetable, Weekend) an icon and `onSurface` text on a `surfaceVariant` pill; today they are grey `labelLarge` (`DayScreen.kt:350-354`) and easy to miss. |
| **PeriodProgress** | Keep for the day; add a description parameter so setup can announce steps (F5). |
| Unused: `AttendanceBar`, `FieldButton`, `PageHeader`, `DlStatusChip`, `AppViewModel.undoBusy` | Never referenced. Remove, or use `AttendanceBar` on the Course screen. |

### 7.3 Colour and meaning
- **Add an amber "caution" role** (for example light `#8A5300` on `#FFF4DF`; dark `#FFCF86` on `#3A2A0E` — verify 4.5 : 1) for "at the limit" and "pending". Spec §7.1 reserves amber for status.
- `tertiary` equals `secondary` (both grey) in `Theme.kt`, so "pending leave" and "holiday" render as plain grey. Point pending at the caution role.
- **Never rely on colour alone:** calendar dots (E1), percentage colour (C4) and chips (B5) all gain a shape, icon or word.
- **Low-contrast fills, computed:** holiday cell 1.13 : 1 (light) and 1.17 : 1 (dark); selected-tab pill on background 1.09 : 1; grey chip on background 1.06 : 1; unfilled progress segment 1.18 : 1. The pill and chip are acceptable because an icon or label carries the state; the holiday cell and unfilled progress segments carry meaning by fill alone and need about 3 : 1 or a second cue.
- Text contrast is fine: secondary text is 5.2 : 1 (light) and 8.3 : 1 (dark); error text 6.2 : 1.

### 7.4 Typography
- The scale is sound. Reserve `headlineMedium` for the one top-bar title per screen.
- Much consequential information is at 12 sp (`bodySmall` / `labelMedium`): "Provisional", "Past records missing", allowance lines, duty-leave summaries. Raise status qualifiers to `bodyMedium` (14 sp) and keep 12 sp for true metadata such as time rails and types.
- Percentages are well served by `headlineSmall` bold with tabular figures.

### 7.5 Touch targets and accessibility
- `ActionIcon` enforces 48 dp — good. Check the calendar cells on 360 dp devices (E6).
- Keep the existing semantics (headings, state descriptions, live region on completion). Add `selected` state and a role to the new segmented control, and custom accessibility actions on period rows ("Mark present", "Mark absent", "More actions") so TalkBack users need not enter each row.
- Fix the setup progress description (F5).
- Restore tab labels at every font scale (A1).
- Verify at 1.3× and 2.0× once the changes land (H4).

### 7.6 Wording — adopt one glossary

| Concept | Use everywhere | Replace |
|---|---|---|
| One slot of teaching | **period** (counts, allowances) / **class** (a scheduled thing: "extra class", "class not held") | "slot", "timetable slots", mixed use of "classes" for counts |
| No mark yet | **not marked** | Unrecorded · Not recorded · Incomplete · Past records missing · Past periods unrecorded · Needs attention |
| Class did not happen | **class not held** / chip **Not held** / undo **Restore class** | Mark not conducted · Not conducted · cancelled · Restore period |
| Extra class excluded | **Doesn't count** | Not counted |
| Duty leave | **duty leave** in full; "leave" only inside a duty-leave screen | DL · Opening DL · "Leave totals" |
| Pre-tracking totals | **attendance before you started tracking** | Opening attendance · Opening balance · opens with · opens at |
| First dated day | **Tracking starts on** | First logging date · logging date |
| End of semester | **Finish semester** / **Finished** / **Reopen** | Complete semester · Mark done · done · Semester done |
| Stop a course | **Stop tracking this course** | Retire course · Retired |
| Follow another weekday | **Use another day's timetable** / banner "Following Monday's timetable" | Copy a weekday here · Copied Monday timetable · Custom day — timetable edited |
| Backup | **Save a backup** / **Restore from a backup** | Export · Restore |

Specific strings:

| Where | Now | Proposed |
|---|---|---|
| Course row | Attend 3 periods to reach 90% | Attend the next 3 periods to reach 90 % |
| Course row | No safe skips | No absences to spare |
| Course row | Can miss 1 period | Can miss 1 period *(keep)* |
| Course row | Provisional | Includes 3 pending duty leave · 90.5 % if rejected |
| Course details | Credited 33 / 37 | Counted as attended: 33 of 37 |
| Course details | Physically present | Attended in person |
| Course details | Confirmed (without pending duty leave) | Without pending duty leave |
| Day, complete | Attendance complete · 6 / 6 recorded | All 6 marked · 5 present · 1 absent |
| Day, future | Recording opens on this date | You can mark this day once it arrives |
| Day, holiday | Holiday — classes do not count today | Holiday — classes don't count |
| Day, empty weekday | Add a class or copy a weekday from the menu. | No classes on your timetable for this day. **Edit timetable** |
| Lab group | Class · 2 periods | 2 periods |
| Copy result | Recorded periods kept … This is a custom day. | Added 4 periods. 2 you had already marked were left as they were. |
| Leave dialog | Pending approval · provisional credit | Counts as attended until it's approved or rejected |
| Leave dialog | Apply (3) | Apply to 3 periods |
| Leave empty state | Add leave from a day's menu. | No duty leave yet. **Add duty leave** |
| Leave summary | 1 date · 1 request · 3 dated periods | 3 periods on 1 day · 1 request |
| Timetable | From Fri, 13 Nov | Changes apply from Fri 13 Nov |
| Timetable conflicts | Saved with 2 conflicts | Timetable saved · 2 marked periods left as they were |
| Retire dialog | Scheduling stops from tomorrow. | It comes off your timetable from tomorrow. Everything marked so far is kept. |
| Semester dialog | Mark Semester 5 done? | Finish Semester 5? You can still view it and reopen it. |
| No semester | Browse or create one in Settings. | No current semester. **Open Settings** |
| About | No account, internet or cloud backup. | Works offline. Nothing leaves this phone. Back up from Settings. |
| Restore | This replaces everything currently in the app. | This replaces everything in the app now. Save a backup first if you're unsure. |

---

## 8. Quick Wins

Each is small, self-contained, and needs no structural change.

| # | Change | Issue | Where |
|---|---|---|---|
| 1 | Show labels on all tabs at every font scale | A1 | `NinetyApp.kt` `SlidingTabBar` |
| 2 | Replace the four icons in the period sheet with labelled rows | B1 | `DayScreen.kt` `SessionActions` |
| 3 | **Approve** / **Reject** text buttons on collapsed pending leave cards | E2 | `LeaveScreen.kt` `EventCard` |
| 4 | Text buttons on the semester card: View · Finish semester · Reopen | G1 | `SettingsScreen.kt` |
| 5 | Text chips for Not marked / Not held / Doesn't count / Leave states | B5 | `Components.kt` `OutcomeChip` |
| 6 | Stop collapsing completed days; show the outcome summary line | B3 | `DayScreen.kt` `DayContent` |
| 7 | Replace the Courses calendar icon with a labelled **Timetable** row | A3 | `CoursesScreen.kt` |
| 8 | Add the legend and shape-coded marks to the calendar | E1 | `HistoryScreen.kt` `DayCell` |
| 9 | Show every incomplete day with its period count | E3 | `HistoryScreen.kt` |
| 10 | "90.5 % if leave is rejected" under provisional percentages | C2 | `CoursesScreen.kt` `CourseCard` |
| 11 | Truncate percentages instead of rounding | C3 | `Format.kt` `percentOf` |
| 12 | Numeric keyboards on all count fields | C8, F3 | `CoursesScreen.kt`, `SetupScreen.kt` |
| 13 | Restrict the effective-date picker to today onward; add helper text | D1 | `TimetableScreen.kt` |
| 14 | Confirm before discarding timetable edits | D2 | `TimetableScreen.kt` |
| 15 | Rename day-menu items; add the weekend empty-state action | B8 | `DayScreen.kt` |
| 16 | Dated backup file name; plain "Backup saved"; formatted restore date | G4 | `SettingsScreen.kt` |
| 17 | Theme as a labelled segmented control | G5 | `SettingsScreen.kt` |
| 18 | Open the timetable on today's weekday | D5 | `WeeklySchedule.kt` |
| 19 | Correct the setup progress accessibility label | F5 | `Components.kt` `PeriodProgress` |
| 20 | Apply the glossary and string table from §7.6 | H1 | all UI files |
| 21 | Banner and exit action while viewing a non-current semester | A7 | `NinetyApp.kt` |
| 22 | Validate course name and counts inside the setup dialog | F3 | `SetupScreen.kt` |

Items 1–5 alone remove most of the "what does this icon do?" problem.

---

## 9. Major Redesign Recommendations

### R1. Persistent mark control on every period (B2, B4, B6, B7)
Replace "two icons that disappear, then a chip, then a hidden tap, then a sheet" with one component that shows the state and changes it. This is the central interaction of the app and should be built once and reused for single periods, lab sub-periods and the leave status selector.
**Risk:** row width at large font scales — define the icon-only fallback up front.

### R2. Today as a day pager with a backlog strip (A4, A5, B3, C1, E3)
One Day destination holding a date, with ‹ ›, a Today chip, a date picker, and a "Catch up" strip that steps through unmarked days. Calendar taps reuse it. This removes the separate `today` / `day/{date}` routes, the back-stack problem and the collapsed-complete state together.
**Depends on:** R1, so the non-collapsed list is compact.

### R3. Four-tab information architecture (A1, A2, A3, A6, A8, C5, C9)
Today · Courses · Calendar · Settings. Courses gains **Timetable** and **Duty leave** rows and a per-course detail screen. The Duty leave screen gains **+ Add duty leave** and the pre-tracking section. The top bar carries the screen title and a tappable semester label.
**Risk:** existing users lose a tab they know; the row on Courses carries the pending count so the feature stays visible.

### R4. Make trust in the numbers visible (C1, C2, C4, C10, B5, B11)
Three additions on Courses — the "if leave is rejected" figure, a tappable "n periods not marked" line, and an amber "at the limit" tier — plus a one-screen explanation of the calculation, and optionally the per-course standing line on Today. None changes any calculation; all present values the engine already computes (`confirmedCredited`, `missingPastPeriods`, `safeSkips`, `recovery`).

### R5. Setup that asks the one question that matters (F1–F5, D3)
A single branch for mid-semester joiners, in-dialog validation, the chip-then-tap timetable editor shared with the Timetable screen, a plain-language review, and an optional reminder switch.

### R6. Semester management with words (G1, G2, G3, A7)
A Semester screen per semester with View, Rename / dates, Finish and Reopen; two visible states; a banner while viewing a non-current one. Uses the existing `updateSemester`.

**Deliberately not recommended:** an overall semester percentage (the rule is per course, and an average would mislead); swipe gestures for marking; a "mark every past day present" bulk action; home-screen widgets; charts. Each adds surface area without serving the daily task.

---

## 10. Prioritized Implementation Roadmap

Each phase leaves the app shippable. Attendance logic is untouched except where noted.

### Phase 0 — Labels and wording (no structural change)
Quick wins 1–5, 7, 11–20, 22.
**Covers:** A1, A3, B1, B5, B8, C3, C8, D1, D2, D5, E2, F3, F5, G1 (labels only), G4, G5, H1.
**Why first:** highest ratio of benefit to risk; fixes the Critical B1; the glossary set here is reused by every later phase.
**Do alongside:** move strings into resources (H3) while each is being edited.

### Phase 1 — Trust in the numbers
Quick wins 8–10, 21; C1 strip on Today and tappable Courses line; C4 tiers and the amber role; C10 explainer; E1; E3 full list; A7 banner.
**Covers:** the Critical C1, plus C2, C4, C10, E1, E3, A7.
**Depends on:** Phase 0 glossary. The "Catch up" button can initially open the oldest unmarked day through the existing route; the stepping flow arrives in Phase 2.

### Phase 2 — Daily recording
R1 (MarkControl), then R2 (day pager, no collapse, catch-up stepping), B4, B7, B10. Optional: B11.
**Covers:** A4, A5, B2, B3, B4, B6, B7, B10, (B11).
**Depends on:** Phase 0 (sheet rows), Phase 1 (backlog strip).
**Verify:** screenshots at 1.0×, 1.3× and 2.0× (H4); update `V1UiTest`, `RedesignDeviceTest`, `AppJourneyDeviceTest` selectors. Get owner sign-off on dropping the "review action" wording in spec §4.2.

### Phase 3 — Information architecture
R3: four tabs, Courses entry rows, Course detail screen, Duty leave screen with add and the pre-tracking section, top-bar change, Calendar rename, A9.
**Covers:** A2, A6, A8, A9, C5, C7, C9, E5, E6.
**Depends on:** Phase 2 (the day picker used by "Add duty leave").
**Do alongside:** update `DESIGN.md`, `PRODUCT.md` "Operating Context", and README screenshots.

### Phase 4 — Setup, timetable and semesters
R5 and R6; D3 chip-then-tap editor; D4 pre-save conflict preview; C6 "No longer tracked" section; G2; G3; F1; F2; F4.
**Depends on:** Phase 3 (the Timetable entry and Semester screen location).
**Small data additions:** none required; `updateSemester` and `previewTemplateConflicts` already exist.

### Phase 5 — Deeper capabilities (each needs new logic and tests)
- E4: attach leave to an existing request (new optional parameter).
- B9: holidays on future dates (touches materialisation).
- C6: resume a stopped course.
- B12: notification action "All present"; reminder offer in setup.
- B7 (3): recent-changes list.
- G4: stored last-backup timestamp and reminder hint.
- H2: user-facing exception type.

### Order at a glance

```
Phase 0  labels + wording ─┬─► Phase 1  trust ─┬─► Phase 2  recording ─► Phase 3  IA ─► Phase 4  setup / timetable / semesters
                           │                   │
                           └───────────────────┴─► Phase 5 items can be scheduled independently once Phase 0 is in
```

### Suggested acceptance checks

| Check | Target |
|---|---|
| Unlabeled icons that change a record | 0 |
| Taps to correct one mark on a completed day | 1 (now 3) |
| Taps to mark yesterday all present | 2 (now 3, across two tabs) |
| Taps to record "2 of 3 lab periods" | 3, none modal (now 4, one modal) |
| Taps to approve a leave request | 3, all labelled (now 3, final one unlabeled) |
| States distinguishable without colour on Day and Calendar | all |
| A course below the minimum can display "90.0 %" | never |
| Unmarked past days visible from Today | always |
| Tab labels visible at 2.0× font scale | all four |
