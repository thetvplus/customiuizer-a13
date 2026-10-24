# Changelog

简体中文 | [English](CHANGELOG_EN.md)

## r13.14.0

`versionCode 142`，相对 `r13.13.0` 的维护更新。

- 按 Android 13 的实际接口精修 Toast 时长、按应用振动、音频焦点、即时音量与熄屏动画，保留系统原生调用顺序及失败恢复行为。
- 修复 Launcher 手势取消、多指中断与重复执行的状态管理。
- 统一设置页面分组，补齐详情页搜索和导航，保留偏好键、默认值及旧备份；回归已有长键备份与失败回滚修复（Issue #12）。
- 修正安全中心电池温度的类型识别；已核验样本不代表安全中心 11.1.6 的兼容证明。
- 移除已证实无效的振幅/定时调节入口和实现，保留历史配置的备份兼容。
- 增加 A13 独立的受保护签名及双仓发布流程：校验最终 main 的完整 CI、证书连续性、APK 和来源证明；恢复发布使用原始产物，不重新构建或签名。

主要目标为 MIUI 14 / Android 13；HyperOS 1 / Android 13 为实验兼容。要求 libxposed API 102，ABI 为 `arm64-v8a`。
本轮尚无 A13 日志或实机验收；静态与构建结果分别记录在工程交接文档中。Issue #14 未全部解决；音乐可视化、扩展电源菜单继续保留为 `RETIRE_CANDIDATE`，安全中心 11.1.6 待取得实际样本。

## r13.13.0 — 2026-10-19

`versionCode 141`，基于 `r13.12.2` 的维护更新。

> **签名变更，不能直接覆盖升级。** 原 A13 发布私钥目前无法取得，本版经用户授权使用重新指定的发布证书。升级前请导出设置备份，卸载旧版后安装，再恢复备份、重新启用模块并重启设备。

### 修复

- 修复设置备份因偏好键长于 128 字节而保存失败的问题（Issue #12）；Backup V2 现在允许最多 256 字节的键，并继续拒绝超限键。
- 按 Android 13 实际回调过滤来电事件，不再修改应用的订阅集合；取消选择或关闭功能后恢复系统分发。
- 修复通知渠道设置的目标类、公开跳转和多用户归属；启动失败保留系统行为，启动后面板清理失败不重复打开页面。
- 设置端由 Application 统一管理 Service 和偏好监听，避免 Activity 重建重复注册、旧连接误清理新连接，以及提交失败后被 SDK 缓存遮住的写入或删除。
- 收敛进程入口、Feature 与 Legacy Hook 的一次安装和部分失败状态，避免初始化竞争和重复安装。

### 框架要求

- 全面使用 libxposed API / Service 102.0.0，最低和目标 API 均为 102；不再兼容仅提供 API 101 的框架。
- 方法和构造器共享原生安装策略，基础入口使用固定 Hook ID；不启用热重载或显式替换。

### 构建工具链

- AGP 9.4.1、Gradle 9.8.1、AGP 内置 Kotlin 2.4.21、JDK / 字节码目标 25、compileSdk 37；保留严格依赖校验。
- 精简重复脚本、历史报告和 CI 构建；DexSQL 查询只用于开发期，不进入 APK 或默认 CI。
- Android 13、MIUI 14、arm64-v8a 的目标范围保持不变；HyperOS 1 / Android 13 仍为实验兼容。

### 验证边界

- 静态、单元测试、lint、R8 和 Fast / Full CI 已通过；正式 APK 在发布前检查版本、内容及本版证书，签名变更已明确说明。
- 本版未取得 A13 实机或运行日志验收。HyperOS 样本部分组件校验失败，已跳过对应范围；不据此声明通用 ROM 支持或设备性能收益。

## r13.12.2 — 2026-08-19

`versionCode 140`，r13.12 系列最终稳定版本，已包含并取代 `r13.12.0` / `r13.12.1`。

### 新增功能

- USB 默认用途沿用现有 A13 Hook，并映射 A14 的模式选项。
- 安装器净化，以及隐藏应用详情页的举报入口。
- 桌面 Dock 高度调节，以及隐藏输入法关闭按钮。
- 状态栏若干视觉增强（时钟/指示相关）。
- 文件夹可关闭模糊，同时保留原有透明度设置。
- 设置备份升级为 V2 格式，并继续可恢复旧备份。
- 自定义电池指示条颜色读取已有 colorval 偏好；息屏变暗比例按 0–99% 滑条写入 AOSP 字段。

### 修复

- 自定义动作/桌面手势保存后可能回到“无动作”：无效或未知动作值会规范为合法默认值 1，保存时不再因 Spinner 越界丢失选择。
- 桌面手势设置页补齐“重启桌面”菜单，与 Launcher 进程中的手势 Hook 安装范围一致。
- USB 默认用途在拔线后重新插上会再次应用。
- 修复 `MultiAction -> AppSelector -> ActivitySelector -> AppSelector -> MultiAction` 两跳返回路径中，AppSelector 作为回栈中继目标时误按 source `isAdded` 校验导致结果丢失的问题；结果回传改为“源选择器生命周期校验 + 回栈目标允许接收”，保持 `openSubFragment(replace + back stack)` 的既有导航契约。

### 稳定性与兼容性

- 选择器结果只在当前有效 Fragment 生命周期内回传。
- 继续按进程安装 Feature；关闭功能不创建业务 Hook。
- 63 项 ROM/设备相关能力仍保持证据门，不在无设备条件下猜测实现。

### 性能优化

- 无新增设备性能数值声明。既有时钟与手势热路径保持有界状态缓存，本版本未做推测性微优化。

### 架构与维护

- 完成 A13 架构与 A14 产品能力对照的静态收口：已有能力按 A13 ABI 独立证明，灵动额头不进入 A13。

### 验证状态

- 静态门禁、单元测试、Release 编译、Lint、R8 与签名产物检查在发布前执行。
- DEVICE_VERIFIED = NO
- LOG_VERIFIED = NO
- 无 ADB，不声明实机或 MIUI/HyperOS 运行时已验证。

### 产物信息

- 文件：`CustoMIUIzer-A13-r13.12.2.apk`
- versionName：`r13.12.2`
- versionCode：`140`

## r13.11.1 — 2026-08-08

`versionCode 137`，基于已完成的 Android 13 性能、生命周期与构建治理阶段发布。

### 核心变化

- 加固 SubFragment 延迟滚动生命周期，在 View 销毁时取消待执行回调，避免对失效 View 执行延迟操作。
- 加固 AppSelector 异步应用列表加载，通过 application context、输入快照和 owner 清理减少 Activity / View 生命周期耦合。
- 加固 ActivitySelector 异步加载，只允许结果在当前有效 View 生命周期内提交，同时保持界面重新创建时重新查询的既有行为。
- 优化状态栏时钟默认格式高频路径，缓存稳定的格式转换结果和资源解析结果，减少每次时间更新时的重复处理。
- 保持原有系统时间格式、秒钟、12/24 小时制、AM/PM、前导零与自定义格式行为。
- 完成 Android 13 Release 编译、单元测试、Lint、R8、严格依赖校验、签名和核心实机加载验证。

### 兼容说明

- MIUI 14 / Android 13 仍为主要目标。
- HyperOS 1 / Android 13 的部分 SystemUI 定制取决于具体 ROM 内部类和系统应用版本；目标不存在时仅影响对应功能，不阻止模块其他功能加载。
- 不对尚未完成设备指标采样的内存、CPU、GC 或耗电改进做数值声明。

## r13.10.1 — 2026-08-06

`versionCode 135`，面向 MIUI 14 / Android 13、HyperOS 1 / Android 13 兼容探测、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 将 SystemUI、Launcher、`system_server` 与普通应用入口拆分为按进程路由的 Installer，并以稳定 Feature 身份、进程范围、安装阶段和一次安装状态避免无关加载与重复安装。
- 加固启动早期偏好快照、空快照、并发加载和安装失败状态，防止偏好更新将已安装 Hook 错误重置或触发重复安装。
- 完善 MIUI 14 与 HyperOS 1 / Android 13 的环境识别、Hook Contract、目标解析和 variant 选择；缺失目标时只跳过受影响功能，不跨候选混装。
- 统一 Hook、反射、Receiver、Observer、延迟回调与诊断边界：普通兼容异常继续隔离，直接或包装的 `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError` 保持传播。
- 完善 Receiver、Observer、View、Handler 与控制器的所有者、替换、失效和释放路径，并移除选择器主线程中的阻塞等待。
- 加固来电不中断、移除安全窗口、截图临时隐藏悬浮窗、通知/分享浮窗、小窗与多窗口限制等回调路径；同步整理状态栏、控制中心、音量、锁屏和设置相关 Hook 的安装与兼容边界。
- Launcher 动画缩放改用直接 API，HotSeats 手势按 View 缓存密度、触摸阈值与状态，FSG 回调复用安装阶段解析的 `BaseRecentsImpl` Class。
- DeviceInfo 使用固定缓冲区和逐字节 sysfs 解析，减少周期性 `Properties`、`RandomAccessFile`、Binder 查询和临时对象，同时保持采样与失败退避行为。
- 增加运行期不变量、Hook 合同、源码危害、ROM 兼容、Release 编译、单元测试、Lint、R8 与依赖完整性门禁。
- 升级构建工具链与依赖校验，清理未引用的 `miui.jar` 和异常 external annotation，并规范共享忽略规则。

### 验证范围

- 当前代码通过 Python 与 Gradle 静态门禁、Release Kotlin/Java 编译、Release 单元测试、Release/Vital Lint、R8、严格依赖校验、Manifest 与 Xposed 元数据检查。
- 已知实装基线为 Redmi Note 11T Pro（`xaga`）、MIUI `V14.0.10.0.TLOINXM`。HyperOS 1 的具体功能可用性取决于 ROM 与系统应用版本。

## r13.9.2 — 2026-08-01

- 锁屏专辑图所属 View 脱离后取消未完成任务，并释放模块背景、单帧缓存和静态处理结果；
- 设置页切换动画调整为 `350ms`；
- 开关复用所在行的按压状态，并移除逐次创建的透明度动画；
- LSPosed 模块列表使用独立简洁摘要。

### 历代核心实现总结

A13 版本线建立了独立包名和 Android 13 维护线，完成 libxposed API 101/102 兼容、System/SystemUI/Launcher 领域拆分、分批 Kotlin 化、资源与偏好 Hook 加固、生命周期治理、有界缓存、可取消异步任务、致命异常边界与 Contract/Resolver 兼容诊断。细节保留在 Git commits 和历史 tags 中。
