# Ninety screenshot notes

The checked-in images use a synthetic semester and show the earlier five-tab interface. [Today in light mode](today.png) shows the former quick-mark icons and collapsed lab. [Courses in dark mode](courses.png) shows the former timetable icon and per-course menus.

The current app uses four labelled destinations: **Today**, **Courses**, **Calendar**, and **Settings**. Today has a date pager, inline marks, and a catch-up row when earlier days need marks. Courses contains the **Timetable** and **Duty leave** rows. Opening a course leads to its detail screen and labelled actions.

The host suite generates current synthetic captures under `app/build/qa/v1/`. The current set includes `today-ready.png`, `today-complete.png`, and light and dark 2.0× captures of Today, Courses, Calendar, Settings, and Duty leave on a 393dp-wide Robolectric configuration. The 2.0× Today captures include scrolled states. [V1UiTest](../../app/src/test/kotlin/com/niranjan/ninety/ui/V1UiTest.kt) generates the captures when the app unit tests run. The capture directory can also contain older files from prior runs.

The checked-in PNGs were retained as historical images. Fresh physical-device captures, TalkBack, the wide navigation rail, and motion remain open. See the [verification summary](../../VERIFICATION_SUMMARY.md).
