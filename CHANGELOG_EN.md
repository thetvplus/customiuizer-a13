# Changelog

[简体中文](CHANGELOG.md) | English

## r13.14.1

`versionCode 142`, a maintenance update from `r13.13.0`.

- Refine Toast duration, per-app vibration, audio focus, immediate volume adjustment and screen-off animation against Android 13 interfaces, preserving native call order and failure recovery.
- Fix Launcher gesture state after cancellation, multiple pointers and repeated completion; refresh Dock assistant bounds per gesture and isolate controllers.
- Unify settings groups and extend detail-page search/navigation; fix off-screen slider writes, stale values after external writes/restore, dynamic step changes and failed-writer state while preserving preference keys, defaults and old backups. Regress the existing long-key backup and rollback fixes (Issue #12).
- Preserve the native power menu when custom resources are missing, corrupt or cannot be installed; read resources completely, close files, keep the resource outside evictable cache and recheck availability before suppressing the native plugin.
- Correct battery-temperature type detection in SecurityCenter. Verified samples do not establish compatibility with SecurityCenter 11.1.6.
- Remove confirmed ineffective amplitude/scheduled-vibration controls and implementation while retaining historical configuration backup compatibility.
- Add separate protected A13 signing and dual-repository promotion: require complete CI on final main, certificate continuity, APK validation and provenance; recover with the original artifact without rebuilding or re-signing.

Primary target: MIUI 14 / Android 13. HyperOS 1 / Android 13 remains experimental. Requires libxposed API 102 and `arm64-v8a`.
No A13 logs or device acceptance were obtained for this work; static and build evidence are recorded separately in the engineering handoff. Issue #14 remains partially unresolved. Music visualization and the extended power menu remain `RETIRE_CANDIDATE`; SecurityCenter 11.1.6 still needs an actual sample.

## r13.13.0 — 2026-10-19

`versionCode 141`, a maintenance update based on `r13.12.2`.

> **Signing certificate changed; direct updates are not supported.** The previous A13 signing key is currently unavailable. This release uses a different certificate explicitly authorized by the maintainer. Export your settings, uninstall the old app, install this version, restore settings, re-enable the module and reboot.

### Fixes

- Fixed settings backup failing when a preference key exceeded 128 bytes (Issue #12). Backup V2 now allows keys up to 256 bytes and continues to reject longer keys.
- Filter call events through the actual Android 13 callback without changing app subscriptions; deselection or disabling restores system dispatch.
- Fixed notification-channel targets, public settings intents and user selection. Launch failure preserves system behavior; panel cleanup failure after launch does not open a second page.
- Application now owns service binding and preference listeners, preventing duplicate registration after Activity recreation, stale connections clearing replacements, and failed writes or deletions being hidden by the SDK cache.
- Unified install-once and partial-failure behavior across process entry points, Features and legacy hooks to avoid initialization races and duplicate installation.

### Framework requirements

- Requires libxposed API / Service 102.0.0, with minimum and target API 102. API 101-only frameworks are no longer supported.
- Methods and constructors share native installation policy, with stable IDs for bootstrap hooks. Hot reload and explicit hook replacement remain disabled.

### Build Toolchain

- AGP 9.4.1, Gradle 9.8.1, AGP built-in Kotlin 2.4.21, JDK / bytecode target 25 and compileSdk 37, retaining strict dependency verification.
- Removed duplicate scripts, historical reports and repeated CI builds. DexSQL queries remain development-only, outside the APK and default CI.
- Android 13, MIUI 14 and arm64-v8a remain the targets; HyperOS 1 / Android 13 compatibility remains experimental.

### Verification limits

- Static checks, unit tests, lint, R8 and Fast / Full CI passed. Version, package contents and this release's certificate are checked before APK publication; the signing change is explicitly documented.
- This version has no A13 device or runtime-log acceptance. Corrupt components in the HyperOS sample were excluded; no general ROM or device-performance claim is made.

## r13.12.2 — 2026-08-19

`versionCode 140`, the final stable release in the r13.12 series. Supersedes `r13.12.0` / `r13.12.1`.

### New features

- USB default-mode options mapped onto the existing A13 hook.
- Installer purify, and hiding the app-details report entry.
- Launcher dock height and hiding the IME dismiss button.
- Additional status-bar visual options.
- Folder blur can be disabled without discarding opacity.
- Settings backup V2 with legacy restore.
- Battery indicator custom colors from existing colorval prefs; screen-dim ratio writes AOSP fields from the 0–99% slider.

### Fixes

- Custom/gesture actions no longer snap back to "No action": invalid or unknown values normalize to legal default 1, and save no longer uses an out-of-range spinner index.
- The Launcher gesture page now exposes Restart Launcher, matching hook ownership in the Launcher process.
- USB default mode reapplies after unplug/replug.
- Fixed the two-hop return path `MultiAction -> AppSelector -> ActivitySelector -> AppSelector -> MultiAction`, where AppSelector (as back-stack relay target) incorrectly applied source `isAdded` gating and dropped Activity results; result delivery now uses source-selector lifecycle validation while allowing back-stack targets to consume plain result data, matching the existing `openSubFragment(replace + back stack)` navigation contract.

### Stability and compatibility

- Selector results are delivered only while the owning Fragment is added.
- Features still install per process; disabled features create no business hooks.
- 63 ROM/device-gated capabilities remain holds; they are not guessed without device evidence.

### Performance

- No new device performance claims. Existing clock and gesture hot paths keep bounded state caches; this release does not add speculative micro-optimizations.

### Architecture and maintenance

- A13 architecture vs A14 product capability review is statically closed. Existing capabilities are proven against the A13 ABI; Dynamic Island stays out of A13.

### Verification

- Static gates, unit tests, Release compile, Lint, R8, and signed-artifact inspection run before publication.
- DEVICE_VERIFIED = NO
- LOG_VERIFIED = NO
- No ADB; this release does not claim on-device or MIUI/HyperOS runtime verification.

### Artifact

- File: `CustoMIUIzer-A13-r13.12.2.apk`
- versionName: `r13.12.2`
- versionCode: `140`

## r13.11.1 — 2026-08-08

`versionCode 137`, released as the completed Android 13 performance, lifecycle, and build-governance milestone.

### Core Changes

- Hardened SubFragment delayed-scrolling lifecycle: pending callbacks are cancelled when the View is destroyed, preventing delayed actions on invalid Views.
- Hardened AppSelector asynchronous app-list loading by using the application context, input snapshots, and owner cleanup to reduce Activity/View lifecycle coupling.
- Hardened ActivitySelector asynchronous loading so results are only committed within the current valid View lifecycle, while preserving the existing re-query behavior on View recreation.
- Optimized the status-bar clock's default-format hot path by caching stable format-conversion and resource-resolution results, reducing repeated work on every time update.
- Preserved existing system time format, seconds, 12/24-hour mode, AM/PM, leading zero, and custom-format behavior.
- Completed Android 13 Release compilation, unit tests, Lint, R8, strict dependency verification, signing, and core on-device loading verification.

### Compatibility Notes

- MIUI 14 / Android 13 remains the primary target.
- HyperOS 1 / Android 13 SystemUI customizations depend on the specific ROM internal classes and system-app versions; a missing target only affects the corresponding feature and does not prevent other module functions from loading.
- No numerical claims are made for memory, CPU, GC, or power-consumption improvements without completed device metric sampling.

## r13.10.1 — 2026-08-06

`versionCode 135`, targeting MIUI 14 / Android 13, capability-detected HyperOS 1 / Android 13 compatibility, `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Split SystemUI, Launcher, `system_server`, and regular-app entry points into process-routed Installers. Stable feature identities, process scopes, install phases, and install-once state prevent unrelated loading and duplicate installation.
- Hardened early preference snapshots, empty-snapshot handling, concurrent loading, and failed-install state so preference updates cannot incorrectly reset installed Hooks or trigger duplicate installation.
- Improved MIUI 14 and HyperOS 1 / Android 13 environment detection, Hook Contracts, target resolution, and variant selection. Missing targets skip only the affected feature without mixing candidates.
- Unified Hook, reflection, Receiver, Observer, delayed-callback, and diagnostics boundaries. Ordinary compatibility failures remain isolated, while direct or wrapped `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` continue to propagate.
- Completed owner, replacement, stale-state, and release paths for Receivers, Observers, Views, Handlers, and controllers, and removed blocking waits from selector UI paths.
- Hardened callback paths for call-interruption control, secure-window removal, temporary overlay hiding during screenshots, notification/share floating-window actions, and multi-window restrictions; aligned installation and compatibility boundaries across status bar, control center, volume, lock screen, and settings Hooks.
- Replaced Launcher animation-scale reflection with a direct API, cached HotSeats density, touch thresholds, and gesture state per View, and reused the install-time `BaseRecentsImpl` Class in FSG callbacks.
- Reworked DeviceInfo sampling around fixed buffers and byte-wise sysfs parsing, reducing periodic `Properties`, `RandomAccessFile`, Binder queries, and temporary objects while preserving sampling and failure backoff.
- Added runtime invariant, Hook-contract, source-hazard, ROM-compatibility, Release compilation, unit-test, Lint, R8, and dependency-integrity gates.
- Updated the build toolchain and dependency verification, removed the unused `miui.jar` and an invalid external annotation, and normalized shared ignore rules.

### Verification Scope

- The current code passes Python and Gradle static gates, Release Kotlin/Java compilation, Release unit tests, Release/Vital Lint, R8, strict dependency verification, Manifest checks, and Xposed metadata checks.
- The known deployed baseline is Redmi Note 11T Pro (`xaga`), MIUI `V14.0.10.0.TLOINXM`. HyperOS 1 feature availability depends on the ROM and system-app versions.

## r13.9.2 — 2026-08-01

- Cancelled unfinished lock-screen album-art work and released module-owned backgrounds, one-frame caches, and static processed results when the owner View detached;
- Set settings-page transitions to `350ms`;
- Reused the containing row's pressed state for switches and removed per-tap alpha animators;
- Added a dedicated concise LSPosed module summary.

### Historical Core Implementation Summary

The A13 line established an independent package and Android 13 maintenance path; delivered libxposed API 101/102 compatibility; separated System, SystemUI, and Launcher domains; performed staged Kotlin migrations; hardened resource and preference Hooks; governed lifecycles; bounded caches; added cancellable asynchronous work; defined fatal-error boundaries; and introduced Contract/Resolver compatibility diagnostics. Fine-grained history remains in Git commits and historical tags.
