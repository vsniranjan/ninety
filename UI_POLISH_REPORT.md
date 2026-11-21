# Ninety UI polish

## Changes

| Screen | Changes |
| --- | --- |
| App shell | Tightened the toolbar and centered the loading indicator directly in its container. The semester selector retains its 48 dp touch target. |
| Today and dated days | Kept one progress count above the period markers. Removed the lab count that repeated the period range, and shortened duty leave labels on grouped period chips. Screen readers still get the full status and period name. An empty weekend uses one heading. |
| Day actions | Shortened “Different course was taught” to “Substitute course.” The extra-class checkbox carries its own meaning. The duty leave form labels recorded periods “Marked,” exposes each row as a checkbox, and retains the separate replacement confirmation. |
| Courses and course details | Course cards lead with the name and percentage. The next step and unmarked count sit together. The minimum and add button share a compact row, and Timetable and Duty leave sit below the cards. The provisional comparison remains visible where relevant. Course type and the attendance breakdown are in the details. |
| Calendar | Reduced spacing around the month controls and used date-first rows for incomplete days. Each row shows the unmarked count and a chevron to open the day. |
| Timetable | Put Save and Cancel together, used a date row for the effective date, and shortened the course assignment hint. Replaced “Copy from…” with a labelled copy icon beside the hint. The setup timetable shares the same editor. |
| Duty leave | Removed zero totals from the empty screen and repeated breakdowns for requests whose periods all share one status. A chevron with an accessible expand or collapse label opens the period details. Approval and record-changing actions retain visible text. |
| Settings | Replaced stacked cards with grouped settings rows, kept the last backup timestamp, and presented reminder time as a labelled row and dialog. The restore confirmation states what the operation replaces. |
| Semester management | Shortened the edit action to “Edit semester” and used the same date and timezone rows as setup. The timezone picker uses an accessible close icon. |
| Setup | Kept the progress indicator and removed the duplicate step fraction. Used date and timezone rows, reduced header padding, and gave the Add course row less visual weight than Continue. Shortened the past-attendance question and empty-timetable confirmation, and removed the redundant sentence in the discard dialog. |

Shared rows use consistent spacing and retain a minimum 48 dp height. Empty states lead with their message, with an action where applicable. Changes are in the Compose presentation and string resources. Attendance calculations, Room storage, and navigation flows use the existing implementation.

## Screen and state checklist

| Surface | Baseline phone review | Updated phone review |
| --- | --- | --- |
| App shell | Four-tab navigation and semester switcher control | Launch, tab navigation, semester switcher dialog, snackbar and undo |
| Today and dated days | Populated day, completed day, grouped lab and pending leave | Mark-all with an existing absence, grouped lab, complete and pending states, future empty day and date before semester start |
| Day actions | Period sheet | Period sheet, substitution menu, date picker, day overflow, holiday confirmation, weekday picker, extra class form and clock, leave form and replacement confirmation. The instrumented journey also covers cancellation and holiday undo. |
| Courses | Course list, provisional credit, standing, course detail and add form | Course list with a long name and three standing levels, provisional credit, attendance rules, course detail, edit, add, opening attendance and stop-tracking confirmation |
| Calendar | Month grid and legend | Month grid and legend. The host UI suite covers opening a day from the list of earlier unmarked days. |
| Timetable | Read and edit modes | Read and edit modes, copy menu, assignment menu and effective-date picker. The host UI suite covers discarding a changed draft. |
| Duty leave | Request list and expanded periods | Request list, request detail, opening leave, dated leave form and replacement confirmation. The instrumented journey checks changing one period and showing mixed approval. |
| Settings | Themes and backup entry | Themes and haptics, reminder time, recent changes, About, license, backup timestamp, Android file picker, restore preview and completion |
| Semester management | Current and paused summaries in Settings | Current and paused summaries, detail, editor, finish confirmation and semester switcher. The instrumented journey finishes, browses and reopens a semester. |
| Setup | All four steps and course editor | All four steps, timezone search surface, course editor with keyboard, empty timetable and discard confirmation. The instrumented journey saves a new semester and uses the resulting timetable. |

All listed destinations and their controls were inventoried in source. The phone review covers the walkthrough states above, with additional edge paths exercised by the host Compose and data suites.

## Verification

- Standard debug and release APKs built successfully. Debug and release lint passed.
- Host tests passed across 21 domain tests and 98 Android app tests, including Compose workflows and theme contrast.
- The connected Redmi M2101K7BI ran Android 13. The final walkthrough used the phone’s default text size and captured 55 screens and states across light and dark themes. An earlier 130% text check exposed a line break in the 90% threshold, which was fixed and reinspected. All device tests used synthetic records in a separate package.
- Eleven hardware instrumentation tests passed. These cover the end-to-end journey, backup integrity, migration, persistence and attendance repository operations. The two dedicated visual-review tests also passed.
- A manual Android file-picker round trip wrote a uniquely named synthetic backup, showed the successful timestamp, previewed the counts and replacement warning, and restored the backup. The temporary export was removed.
- The baseline captures, walkthrough screenshots and test logs are under `.toolchain/ui-polish-review/`. The final default-size captures are in `final-confirmed/`.
- The Courses follow-up reproduced the reported dark-mode state at the phone’s default size with two synthetic courses at 100% and 75%, three and one unmarked periods, and zero pending duty leave. The capture and targeted tests are under `.toolchain/courses-followup/`.

## Remaining coverage

- Hardware captures cover the states listed above. Rare failure and conflict variants, including corrupt backup handling, some timetable conflicts and repository validation errors, rely on automated coverage and source review.
- A second Android device and a full TalkBack walkthrough were outside the captured coverage.
