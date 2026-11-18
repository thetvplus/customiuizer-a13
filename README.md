# 米客 A13

简体中文 | [English](README_EN.md)

米客 A13 是用于定制 **MIUI 14 / Android 13** 系统界面、桌面与交互的 Xposed 模块。支持 `arm64-v8a`，需要提供 **libxposed API 102** 的框架。**HyperOS 1 / Android 13** 为实验兼容，功能取决于 ROM 与系统应用版本。其他平台见[兼容矩阵](COMPATIBILITY.md)。

[下载正式版](https://github.com/thetvplus/customiuizer-a13/releases/latest) · [Xposed 模块仓库](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r13/releases/latest) · [更新日志](CHANGELOG.md)

## 功能概览

| 范围 | 主要功能 |
| --- | --- |
| 系统界面 | 状态栏、信号、控制中心、通知、音量、锁屏与充电界面 |
| 桌面 | 图标、文件夹、Dock、最近任务、手势与动画 |
| 操作与应用 | 导航键与按键动作、电源菜单、浮窗、多窗口、安装器与分享 |

## 安装与升级

1. 从发布页下载并安装正式 APK。使用相同证书的版本可覆盖升级。
2. 在框架中启用模块，按所需功能授予宿主作用域，并重启设备。
3. 在模块设置中启用所需功能。安装型开关变更后，按页面提示重启目标进程或设备。

从使用旧证书的 r13.12.2 或更早版本升级时，先导出设置，然后卸载旧版、安装并恢复，最后重新启用模块。详见[签名与升级](COMPATIBILITY.md#签名与升级)。同一设备仅启用一个 CustoMIUIzer 模块。

## 文档与开发

应用 ID 为 `tv.withaibuild.customiuizer.r13`。当前正式版本和来源见[发布页](https://github.com/thetvplus/customiuizer-a13/releases/latest)；源码版本以[构建配置](app/build.gradle.kts)为准。

项目优先修复 Android 13 上的正确性、兼容性与稳定性问题。通过 [GitHub Issues](https://github.com/thetvplus/customiuizer-a13/issues) 报告问题，请注明模块版本、ROM、宿主版本和重现步骤。分享日志前请先脱敏，参见[隐私说明](PRIVACY.md)。

维护任务从[工程规则](AGENTS.md)进入。构建与验证见[工程文档](docs/README.md)，宿主进程与 Hook 入口见[架构](ARCHITECTURE.md#入口与进程)。

## 支持与许可

可通过 [PayPal](https://paypal.me/Jinjitv) 支持维护。

项目依据 [GPL-3.0](LICENSE) 分发，派生自 [Mikanoshi/CustoMIUIzer](https://code.highspec.ru/Mikanoshi/CustoMIUIzer)，并参考 [MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 13 实现。
