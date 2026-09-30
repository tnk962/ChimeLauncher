# Discover window-token fix: device verification

## Root cause

The launcher supplied `decorView.windowToken` to Google's overlay service. This
identifies the ViewRoot window, not the Activity's application-window token.
Google creates a `TYPE_DRAWN_APPLICATION` window (type 4). WindowManager therefore
rejected attachment with `BadTokenException` / "unknown token". The service binding
and IPC transport succeeded; the visible "no response" was the subsequent timeout.

## Fix

Keep the token in the Activity's `Window.attributes`, falling back only to
`decorView.applicationWindowToken`. Never replace it with `View.windowToken`.
No Companion protocol change or external-Activity fallback is necessary.

## Verified on 2026-10-01 (JST)

- Device: SC-56F, Android 16, cover display, Chime as default HOME.
- Google App: 17.60.15.ve.arm64.
- Updated launcher with `adb install -r`; app data was not cleared.
- Google creates `GoogleDiscoverWindow` attached to Chime's Activity token.
- Rightward drag from the custom feed opens the real Google Discover feed.
- Leftward drag closes it; reopening and HOME return work.
- Top resumed Activity remains `com.myenvironment.launcher/.MainActivity` while
  Discover is displayed. No separate Google Activity launch was used.
- Launcher Release build and 26 JVM unit tests passed.

This verifies one current device / Google App combination, not all versions,
unfolded-display transitions, or complete equivalence to Nova Launcher.

## Regression checks for future changes

1. Keep Chime as default HOME and install the same-signed Companion.
2. Restart Chime, unlock the device, then wait more than ten seconds.
3. Confirm the custom-feed header shows Discover ready, not a timeout.
4. Drag right from the custom feed, then left to close, then reopen and press HOME.
5. In `dumpsys window`, confirm `GoogleDiscoverWindow` has Chime's Activity token
   and reaches full opacity when opened. In `dumpsys activity activities`, confirm
   Chime remains the top resumed Activity.
6. Check current logs for `BadTokenException` / `Window attach failed`.
7. Separately test Fold open/close, rotation, screen lock/unlock and Google App
   replacement; these are not covered by the JVM gesture tests.
