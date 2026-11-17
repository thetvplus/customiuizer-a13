# A13 ROM 样本与兼容结论

ROM 留在仓库外；以下记录约束本次修复，不代表整台设备的功能验收。

| 样本 | 真实版本 | 可用证据 |
| --- | --- | --- |
| xaga，目录 `xaga-in-A13_MIUI14` | 国行 MIUI 14，`V14.0.7.0.TLOCNXM`，API 33 | 本次检查的 SystemUI、Settings、framework、services、miui-services、Home、SystemUIPlugin ZIP CRC 均通过 |
| veux，目录 `veux-id-A13_HyperOS1` | 印尼 HyperOS 1，`OS1.0.10.0.TKCIDXM`，API 33 | services、miui-services CRC 通过；Home / plugin 的 DEX CRC 通过，资源 CRC 失败 |

veux 的 SystemUI `classes2.dex` / `classes3.dex`、Settings DEX、framework 部分 DEX 校验失败，
按用户要求跳过这些输入。DexSQL 可以只返回其余 DEX；此时零行结果不能证明 ROM 缺少目标。
Home / plugin 的 DEX 也存在 SHA-1 头摘要不一致，因此不将其当作完整原厂包或资源证据。
机型和地区均不同，不能把所有差异归因于 MIUI / HyperOS。

## 修复依据

- **来电不中断应用**：两份 services 都有 `TelephonyRegistry$Record.matchTelephonyCallbackEvent(I)Z`、
  `callingPackage:String`、`eventList:Set`，没有旧 `events:int`。两条通知路径都经此匹配器分发
  事件 6 / 36。按应用过滤这两个事件，不修改订阅集合；取消勾选或关闭功能后恢复原分发。
  `callingPackage` 在安装任何 Hook 前解析，失败不产生部分安装。
- **通知渠道设置**：xaga 的 `NotificationUtil.isHybrid(ExpandedNotification)Z` 位于
  `com.android.systemui.statusbar.notification`；旧的 `.miui.statusbar.notification` 路径不存在。
  `onClickInfoItem(Context, MenuItem)` 保留原回调时序。Settings Manifest 的公开 action
  `android.settings.CHANNEL_NOTIFICATION_SETTINGS` 指向导出的 `ChannelPanelActivity`，
  由 Settings 自己选完整渠道页面；`UserHandle.getUserHandleForUid` 从通知 UID 选择用户。
  小程序、默认渠道、无效身份和启动失败保留系统行为；启动成功后折叠失败不会重复打开。
- 老的 `VibratorService.doVibratorOn` 在两份 services 中均不存在。振幅调整已在集中维护中退役，
  移除唯一消费者和入口、保留历史偏好与备份，不用未经设备验证的新路径替代它；
  其他按应用振动功能继续保留。详见 [维护记录](../A13_CORE_MAINTENANCE.md)。

分类均为 `A13_COMPAT_VARIANT`，保留旧 preference key 和已有功能意图。
核对顺序：MonwF 当前 `System.java` → A13 `c696d33` 初始实现 → 修改前 A13 →
A14 `cb89042b0ff9e7ce1e9a8efe31ee696fa0694e06`。
A14 提供公开 Intent 和冷路径解析的参考；其单 boolean 菜单 / listener ABI 未移植至 A13。

STATIC_VERIFIED = YES；BUILD_VERIFIED = YES。相关回调测试、完整 JVM / Python 门禁、
lint 与 R8 均通过；最终 DEX 未打包 Hook API，设置入口无直接 Hook API 引用。
LOG_VERIFIED / DEVICE_VERIFIED 均未取得；HyperOS 通知设置尚无完整样本证据。

## 双排信号与 Issue #14（2026-11-17）

- **双排信号（#19）**。已发布的 r13.12.2 含 96 个双排信号 drawable，r13.13.0 与 r13.14.1 的资源表中为零。保留图标资源，确认 SystemUI 的精确视图入口并完成 drawable 注册后才合并信号。xaga 含左右两个控制器的 `setMobileIcons(String,List)` 和 `StatusBarMobileView` 的初始化、更新及明暗切换入口。veux 的完整 DEX 可确认右控制器和视图入口，左控制器仍缺完整样本证据。
- **扩展电源菜单（#14）**。同一对发布 APK 中 `raw/extended_power_menu` 从有到无。r13.14.1 的报告日志包含资源 ID 0 异常和随后出现的原生电源菜单。xaga 的 `MiuiGlobalActions` 构造函数调用 SystemUI 的 MAML ZIP loader；veux 来自 `createScreenElementRoot()` 的引用仅作旁证。保留 MAML 路线，用直接资源引用让压缩器保留包内文件。
- **安全中心（#14）**。两份可用 APK 的应用详情活动均从 Intent 的 `package_name` 解析原生目标，均缺少活动自身的 `PackageInfo` 字段。禁用菜单增加此路径，按 ROM 的 `miui.intent.extra.USER_ID` 保留原生跨用户操作。两份样本都有旧版 `initView()`、`onClick(View)` 与电池 `BatteryFragment` handler。报告的 11.1.6 缺少前两个精确方法；电池、默认打开方式及应用信息页仍需要该版本 APK 或日志来确认绑定。
- **锁屏可视化（#14）**。xaga 的 CentralSurfaces、KeyguardStateController 和 NotificationMediaManager 保留对应入口。面板重复初始化时复用当前视图，替换面板时移除旧视图。播放判定每次读取一次会话状态；仅允许控制器模式下，没有活动会话时跳过音频服务查询。日志未定位报告设备上的失效点。
- **历史 Issue**。#12 已有 256 字节上限与 142 字节回归；#1、#2、#3 的网速、文件夹、手势和悬浮菜单保留既有路径。Home 和插件 DEX 的头校验异常限制了可推断范围，未从零结果扩展 Hook。

以上行为结论为静态证据。历史日志针对报告中的发布版，候选版显示与设备行为仍待验收。

## 输入身份（SHA-256）

路径相对于各自 ROM 根目录。后续更换输入时重新取证，不复用局部 method_idx。

| 样本 / 文件 | SHA-256 |
| --- | --- |
| xaga `system/system/framework/services.jar` | `66cceb59614c69f87ad8cb10ac7c357c8f27875aa75eb6118457807e075d7a7f` |
| veux `system/system/framework/services.jar` | `4cc071acdf3bb53747b4ddbd1d1c699d6f84fd88f7588b0221c3e93cc36f3fb0` |
| xaga `system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | `c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98` |
| xaga `system_ext/priv-app/Settings/Settings.apk` | `c676552ec539e492c47b25294e05085176f75ce0c8eb829fab040fdb2d96e435` |
| xaga `system/system/framework/framework.jar` | `093f53a068eaaabef145e7580825cac1c6dc681e835cd865e078b4a77c327b7a` |

重查先用 Python 标准库 `zipfile` 的 CRC 检查，再运行 [DexSQL 查询](../../tools/dexsql/README.md)。
校验失败保留输入并跳过受影响范围，不自动修补样本，也不建立另一套 DEX 解析器。
