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

## 运行架构

- SystemUI、Launcher、`system_server` 与普通应用入口按目标进程路由到独立 Installer，避免无关进程加载不属于自己的安装路径；
- Feature 使用稳定身份、进程范围、安装阶段和一次安装状态，关闭或不兼容的功能跳过无关注册与对象创建；
- ROM Contract、Resolver 与 Installer 共享目标选择结果，缺失目标时仅安全跳过受影响功能；
- Receiver、Observer、View 与控制器按所有者管理替换、失效和释放，降低重复注册与长期引用风险；
- 普通 ROM、反射和回调异常保持隔离，`OutOfMemoryError`、`ThreadDeath` 与 `VirtualMachineError` 不会被伪装成普通兼容失败；
- DeviceInfo 与 Launcher 高频路径减少重复 Binder、反射、I/O、配置读取和临时对象。

本版本变化见 [CHANGELOG.md](CHANGELOG.md)。工程规则、架构与兼容文档见 [AGENTS.md](AGENTS.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[COMPATIBILITY.md](COMPATIBILITY.md) 与 [docs/A13_PARITY.md](docs/A13_PARITY.md)。

`r13.14.1` 包含核心 Hook、Launcher 手势、设置分组和搜索精修，以及独立的受保护签名发布工程。完整变化见 [CHANGELOG.md](CHANGELOG.md)，发布与恢复步骤见 [发行说明](docs/A13_RELEASE.md)。

## 构建与验证

```bash
python tools/verify.py full
```

工程工具链：AGP 9.4.1、Gradle 9.8.1、Kotlin 2.4.21（AGP 内置模式）、JDK 25、compileSdk 37；minSdk 33 / targetSdk 34 保持不变。见 [构建说明](docs/ENGINEERING_UPGRADE.md)。


## 支持与联系

如果这个项目对你有帮助，可以通过 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

- 源码仓库：<https://github.com/thetvplus/customiuizer-a13>
- 用户下载：<https://github.com/thetvplus/customiuizer-a13/releases>

## 开发说明

- 稳定与行为保持优先；兼容逻辑限制在 ROM / ClassLoader 边界；
- 高频 Hook 避免临时数组、集合、Regex、格式化、重复反射和远程偏好读取；
- Java 到 Kotlin 采用行为等价迁移，并配套测试和静态门禁；
- 保留 `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 的 JVM / 框架边界；
- 细粒度历史见 Git commits 和 tags，发布变化见 [CHANGELOG.md](CHANGELOG.md)。

项目依据 GPL-3.0 分发，派生自 Mikanoshi/CustoMIUIzer，并参考 MonwF/customiuizer 的 Android 13 实现。
