# 米客 A13｜CustoMIUIzer A13

[简体中文](README.md) | English

CustoMIUIzer A13 customizes the system UI and interactions on **MIUI 14 / Android 13**, with capability-based compatibility paths for **HyperOS 1 / Android 13**. It uses an independent package, release line, and modern libxposed API.

> Official publishing enforces continuity with the r13.13.0 signing certificate. When migrating from r13.12.2 or earlier, export settings, uninstall the old app, install and restore. See the [compatibility notes](COMPATIBILITY.md#签名与升级).

> **Maintenance status**
>
> Actively maintained, with correctness, compatibility and stability on Android 13 taking priority.

- Current version: `r13.14.1` (versionCode `142`); see [Releases](https://github.com/thetvplus/customiuizer-a13/releases) for publication status
- Application ID: `tv.withaibuild.customiuizer.r13`
- Source repository: <https://github.com/thetvplus/customiuizer-a13>
- User downloads: <https://github.com/thetvplus/customiuizer-a13/releases>
- Deployed framework: LSPosed / Vector

## Core Features

- Status-bar clock, date, temperature, network speed, battery, signal, and icon layout;
- Control center, notifications, volume, brightness, lock screen, media, and charging UI;
- Launcher icons, folders, Dock, recents, gestures, and animations;
- Navigation keys, button actions, power menu, floating windows, multi-window behavior, installer, sharing, and app permissions.

## Compatibility

| Item | Supported range |
| --- | --- |
| Primary system | MIUI 14 / Android 13 |
| Capability-detected target | HyperOS 1 / Android 13; feature availability depends on the ROM and system-app versions |
| SDK | minSdk 33 / targetSdk 34 |
| ABI | `arm64-v8a` |
| Xposed framework | LSPosed / Vector |
| Module metadata | `minApiVersion=102`, `targetApiVersion=102`, `staticScope=true` |

Requires a framework providing libxposed API 102. Android 14 and later are not supported. Do not enable this module together with upstream or another CustoMIUIzer-derived module.

Historical deployed baseline: Redmi Note 11T Pro (`xaga`), MIUI `V14.0.10.0.TLOINXM`. This is not device acceptance for `r13.14.1`.

## Runtime Architecture

- SystemUI, Launcher, `system_server`, and regular-app entry points are routed to process-specific Installers, avoiding unrelated installation paths in the wrong process;
- Features use stable identities, process scopes, install phases, and install-once state; disabled or incompatible features skip unrelated registrations and object creation;
- ROM Contracts, Resolvers, and Installers share the resolved target, while missing targets safely skip only the affected feature;
- Receiver, Observer, View, and controller lifecycles are owner-bound with replacement, stale-state, and release paths;
- Ordinary ROM, reflection, and callback failures remain isolated, while `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` are not disguised as compatibility failures;
- DeviceInfo and Launcher hot paths reduce repeated Binder calls, reflection, I/O, configuration reads, and temporary objects.

See [CHANGELOG_EN.md](CHANGELOG_EN.md) for release changes and [AGENTS.md](AGENTS.md), [ARCHITECTURE.md](ARCHITECTURE.md), [COMPATIBILITY.md](COMPATIBILITY.md), and [docs/A13_PARITY.md](docs/A13_PARITY.md) for engineering rules, architecture, and compatibility.

`r13.14.1` includes core Hook, Launcher gesture, settings grouping/search refinements and separate protected signing/publishing. See [CHANGELOG_EN.md](CHANGELOG_EN.md) and the [release/recovery guide](docs/A13_RELEASE.md#english-operations).

## Build and verification

```bash
python tools/verify.py full
```

Build tools: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21 (AGP built-in mode), JDK 25 and compileSdk 37. Runtime SDK bounds remain minSdk 33 / targetSdk 34. See the [build guide](docs/ENGINEERING_UPGRADE.md).


## Support and contact

If this project helps you, you can support continued development via [PayPal](https://paypal.me/Jinjitv).

- Source repository: <https://github.com/thetvplus/customiuizer-a13>
- User downloads: <https://github.com/thetvplus/customiuizer-a13/releases>

## Development notes

- Stability and behavior preservation come first; compatibility logic stays at ROM / ClassLoader boundaries;
- Hot-path hooks avoid temporary arrays, collections, regex, formatting, repeated reflection, and remote preference reads;
- Java-to-Kotlin migrations are behavior-equivalent and gated by tests;
- `MainModule.java`, `XposedHelpers.java`, and `MemberUtilsX.java` remain the JVM / framework boundary;
- Fine-grained history lives in Git commits and tags; release changes are in [CHANGELOG_EN.md](CHANGELOG_EN.md).

Distributed under GPL-3.0. Derived from Mikanoshi/CustoMIUIzer and informed by MonwF/customiuizer's Android 13 work.
