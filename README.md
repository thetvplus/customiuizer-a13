# 米客 A13｜CustoMIUIzer A13

简体中文 | [English](README_EN.md)

CustoMIUIzer A13 是面向 **MIUI 14 / Android 13** 的系统界面与交互定制模块，并为 **HyperOS 1 / Android 13** 提供基于能力探测的兼容路径。项目使用独立包名、版本线和现代 libxposed API。

> 正式发布流程强制延续 r13.13.0 的签名证书。由 r13.12.2 或更早版本迁移时，请先备份，卸载旧版后安装并恢复。详见 [兼容说明](COMPATIBILITY.md#签名与升级)。

> **维护状态**
>
> 持续维护，优先修复 Android 13 目标上的正确性、兼容性与稳定性问题。

- 当前版本：`r13.14.1`（versionCode `142`）；正式发行状态见 [Releases](https://github.com/thetvplus/customiuizer-a13/releases)
- 应用 ID：`tv.withaibuild.customiuizer.r13`
- 源码仓库：<https://github.com/thetvplus/customiuizer-a13>
- 用户下载：<https://github.com/thetvplus/customiuizer-a13/releases>
- 实装框架：LSPosed / Vector

## 核心功能

- 状态栏时钟、日期、温度、网速、电池、信号与图标布局；
- 控制中心、通知、音量、亮度、锁屏、媒体与充电界面；
- Launcher 图标、文件夹、Dock、最近任务、手势与动画；
- 导航键、按键动作、电源菜单、浮窗、多窗口、安装器、分享与应用权限。

## 兼容范围

| 项目 | 支持范围 |
| --- | --- |
| 主要系统 | MIUI 14 / Android 13 |
| 兼容探测目标 | HyperOS 1 / Android 13，具体功能取决于 ROM 与系统应用版本 |
| SDK | minSdk 33 / targetSdk 34 |
| ABI | `arm64-v8a` |
| Xposed 框架 | LSPosed / Vector |
| 模块元数据 | `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true` |

需要提供 libxposed API 102 的框架。不支持 Android 14 及以上版本，也不建议与上游版或其他 CustoMIUIzer 派生模块同时启用。

历史实装基线：Redmi Note 11T Pro（`xaga`）、MIUI `V14.0.10.0.TLOINXM`；不代表 `r13.14.1` 已完成实机验收。

## 开发

使用 JDK 25 和仓库的 Gradle Wrapper。[工程文档](docs/README.md) 提供 SDK 配置、验证和运行架构入口。已发布版本的变化见 [更新日志](CHANGELOG.md)。

## 支持与联系

如果这个项目对你有帮助，可以通过 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

项目依据 GPL-3.0 分发，派生自 Mikanoshi/CustoMIUIzer，并参考 MonwF/customiuizer 的 Android 13 实现。
