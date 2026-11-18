# Customiuizer A13

[简体中文](README.md) | English

Customiuizer A13 is an Xposed module for customizing the system UI, launcher, and interactions on **MIUI 14 / Android 13**. It supports `arm64-v8a` and requires a framework that provides **libxposed API 102**. **HyperOS 1 / Android 13** support is experimental; features depend on the ROM and system app versions. See the [compatibility matrix](COMPATIBILITY.md) for platform details.

[Download the stable release](https://github.com/thetvplus/customiuizer-a13/releases/latest) · [Download from Xposed](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r13/releases/latest) · [Changelog](CHANGELOG_EN.md)

## Features

| Area | Features |
| --- | --- |
| System UI | Status bar, signal icons, Control Center, notifications, volume, lock screen, and charging UI |
| Launcher | Icons, folders, Dock, recents, gestures, and animations |
| Actions and apps | Navigation and button actions, power menu, floating windows, multi-window, installer, and sharing |

## Installation and upgrades

1. Download and install the official APK from the release page. Versions signed with the same certificate support in-place upgrades.
2. Enable the module in the framework, grant scope to the host apps needed for your features, then reboot.
3. Enable the features you want in the module settings. Follow the prompt to restart the target process or device after changing an option that installs a hook.

To upgrade from r13.12.2 or an earlier release signed with the old certificate, export your settings first. Uninstall the old version, install the new one, restore the backup, and enable the module again. See [signing and upgrades](COMPATIBILITY.md#签名与升级). Enable only one CustoMIUIzer module per device.

## Documentation and development

The application ID is `tv.withaibuild.customiuizer.r13`. The [release page](https://github.com/thetvplus/customiuizer-a13/releases/latest) records the current official version and its source; the [build configuration](app/build.gradle.kts) defines the source version.

Maintenance prioritizes correctness, compatibility, and stability on Android 13. When reporting a problem on [GitHub Issues](https://github.com/thetvplus/customiuizer-a13/issues), include the module version, ROM, host app version, and reproduction steps. Remove personal information from logs before sharing; see the [privacy notes](PRIVACY.md).

Start maintenance work with the [engineering rules](AGENTS.md). See the [engineering guide](docs/README.md) for builds and verification, and the [architecture guide](ARCHITECTURE.md#入口与进程) for process routing and hook entry points.

## Support and license

You can support maintenance through [PayPal](https://paypal.me/Jinjitv).

Distributed under the [GPL-3.0](LICENSE). Derived from [Mikanoshi/CustoMIUIzer](https://code.highspec.ru/Mikanoshi/CustoMIUIzer) and informed by [MonwF/customiuizer](https://github.com/MonwF/customiuizer)'s Android 13 work.
