# PR 17 workflow consolidation plan

## Source of truth
`.github/workflows/android-build.yml` is the canonical required-check workflow. It exposes the separate required jobs `unit-tests`, `apk-build`, and `smoke-test-minimal`, plus the non-blocking Euronics UI validation. It uploads the APK before smoke testing and publishes `Open-Jarvis-Preview-APK` only after the smoke job succeeds.

## Duplicate retirement sequence
1. Keep `main.yml` enabled during PR 17 validation; it is not a required-check source for the PR 17 base.
2. Verify the three canonical jobs above on PR 17 and confirm the canonical APK/logcat artifacts are retained.
3. In a separate, reviewable workflow-only commit after that validation, remove `.github/workflows/main.yml`. No job is removed from `android-build.yml`; the canonical workflow retains the same three required checks and its smoke logcat artifact.
4. Do not retire or weaken any test. A failed smoke check remains a failed check and blocks the release-ready APK artifact.

## Run #99 source comparison
- `android-build.yml` pins API 34, x86_64, Pixel 2, explicit emulator flags, 600-second boot timeout, and disables animations.
- `main.yml` pins API 34, Google APIs, x86_64, but leaves emulator profile/options/boot timeout/animation settings at action defaults.
- Both run `scripts/smoke-minimal.sh`; the shell script has bounded readiness/install/resume loops, but some adb calls in its failure-diagnostic branches are not individually timeout-wrapped.
- `main.yml` had four unbounded adb diagnostics commands in its always-run crash collection step. PR 17 wraps each with GNU `timeout` (30 seconds for logcat, 20 seconds for each dumpsys) and records timeout/failure in `diagnostics-errors.txt`.
- The run #99 smoke failure root cause is not established by the missing GitHub log blob; the workflow source comparison does not prove why that particular emulator smoke run failed.
