# Tutorial screenshots

The in-app guide (`ui/onboarding/OnboardingDialogFragment.kt`) shows 24 annotated screenshots of the app (recaptured 2026-11-28 for the earth-palette design: bottom tabs, frame-the-leaf, heat map, disease pills).
They are built here and are **not** part of the APK build: the outputs are committed to
`app/src/main/res/drawable-nodpi/tutorial_*.webp`.

- `raw/`: original emulator captures (1080×1920, light mode, signed in as the Demo Farmer account). **Not committed**
  (git-ignored). Recapture the screens you need before re-running; file names are the `raw` values in `shots.json`.
- `shots.json`: one entry per slide: which raw file, the crop, and the highlight boxes (number, label, label side:
  `below`, `above`, `left`, `right` or `inside`, or `"at": [x, y]` to place the label exactly where no side has room).
- `annotate.py`: crops, draws the green outlines, numbered badges and labels, and writes WebP files.

## When the UI changes

1. Recapture the affected screen on the emulator, using the same account and similar data:
   `adb exec-out screencap -p > raw/<name>.png` (in Git Bash, set `MSYS_NO_PATHCONV=1` before `adb push` or `adb pull`).
2. Adjust that slide's `crop` and `box` values in `shots.json` if things moved.
3. Run `python annotate.py --sheet` and check `contact_sheet.png`. Labels must not cover what they point at. Delete the
   sheet afterwards.
4. If what a highlight points at changed, update the slide's caption (`onboarding_NN_body` in `strings.xml`). The ①②③ in
   the caption match the badge numbers.

Slides that depend on recent features: frame the leaf (04), the heat-map legend (17), the disease pills (19), the ⋮ post menu (14), Report (13, 15), and the Settings guide row (24).

If the Android file picker crashes on the emulator, cold-boot it (`emulator -avd Pixel_9 -no-snapshot-load`). If photo taps don't register in the picker, press Tab then Enter.
