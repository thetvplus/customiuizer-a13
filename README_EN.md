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

## Development

Use JDK 25 and the checked-in Gradle wrapper. See the [engineering guide](docs/README.md) for SDK setup, verification, and runtime architecture. Published changes are recorded in the [changelog](CHANGELOG_EN.md).

## Support and contact

If this project helps you, you can support continued development via [PayPal](https://paypal.me/Jinjitv).

Distributed under GPL-3.0. Derived from Mikanoshi/CustoMIUIzer and informed by MonwF/customiuizer's Android 13 work.
