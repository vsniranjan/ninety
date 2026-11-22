# Attendance rules

Ninety applies an inclusive 90% minimum to each course. Every academic period is one unit, including the individual periods of a lab and shorter Friday periods. Labs are tracked as separate courses.

## Timetable

| Period | Monday–Thursday | Friday |
| --- | --- | --- |
| 1 | 09:00–10:00 | 09:00–09:45 |
| 2 | 10:00–11:00 | 09:45–10:30 |
| 3 | 11:00–12:00 | 10:30–11:15 |
| 4 | 13:00–14:00 | 11:15–12:00 |
| 5 | 14:00–15:00 | 14:00–15:00 |
| 6 | 15:00–16:00 | 15:00–16:00 |

The weekly template covers Monday through Friday. A special date can copy a weekday's timetable, including on the weekend. Copied periods count when recorded as conducted. Extra classes are excluded by default and can be opted in individually. Timetable changes take effect on the selected college-local date. Recorded periods retain their identities.

## What counts

| Outcome | Conducted periods | Credited periods |
| --- | ---: | ---: |
| Present | 1 | 1 |
| Absent | 1 | 0 |
| Duty leave | 1 | 1 |
| Unrecorded | 0 | 0 |
| Cancelled or holiday | 0 | 0 |

Future and non-counting periods contribute zero. An incomplete past day stays unrecorded until attendance is entered. **Mark all present** fills eligible unrecorded periods and preserves existing marks.

For a course tracked from mid-semester, opening totals cover periods before the first logging date. Physically present and duty leave counts together must be at most the opening conducted count. Later dated attendance is added to those totals.

## Calculation

For each course, let `A` be the credited count (physically present plus duty leave) and `N` the conducted count. If `N` is zero, the app shows that no attendance has been recorded. Otherwise, the displayed percentage is `100 × A / N` and the exact threshold check is `10 × A ≥ 9 × N`.

- **Safe skips** are `max(0, floor((10 × A − 9 × N) / 9))`. Each future skip adds one conducted period.
- **Periods needed** are `max(0, 9 × N − 10 × A)`. Each future attended period adds one conducted and one credited period.

Both projections apply to the same course. Unmarked periods can change the result once recorded.
