# A13 ROM 样本与兼容结论

原始 ROM 留在仓库外。以下结果限对应输入与成员，证据等级见 [COMPATIBILITY](../../COMPATIBILITY.md#证据等级)。

| 样本 | 真实版本 | 可用证据 |
| --- | --- | --- |
| xaga，目录 `xaga-in-A13_MIUI14` | 国行 MIUI 14，`V14.0.7.0.TLOCNXM`，API 33 | 已检查的 SystemUI、Settings、framework、services、miui-services、Home、SystemUIPlugin ZIP CRC 均通过 |
| veux，目录 `veux-id-A13_HyperOS1` | 印尼 HyperOS 1，`OS1.0.10.0.TKCIDXM`，API 33 | services、miui-services CRC 通过；Home / plugin 的 DEX CRC 通过，资源 CRC 失败 |

veux 的 SystemUI `classes2.dex` / `classes3.dex`、Settings DEX、framework 部分 DEX 校验失败，
已排除受影响条目。DexSQL 仍可能返回这些条目的定义，命中只作线索，零行结果保持未知。
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
  其他按应用振动功能继续保留。分类见 [A13_PARITY](../A13_PARITY.md)。

分类均为 `A13_COMPAT_VARIANT`，保留旧 preference key 和已有功能意图。
HyperOS 通知设置仍缺完整样本证据。历史维护与构建记录由 [A13_PARITY](../A13_PARITY.md) 链接到 Git。

## 双排信号与 Issue 14

**双排信号（#19）**。历史发布 APK 中，r13.12.2 有 96 个双排信号 drawable，r13.13.0 与 r13.14.1 为零。源码中 `DualRowSignalHook` 在精确成员和资源就绪后才合并。xaga `MiuiSystemUI.apk!classes2.dex` 中定义了以下成员。

```text
Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;->setMobileIcons(Ljava/lang/String;Ljava/util/List;)V
Lcom/android/systemui/statusbar/phone/MiuiDripLeftStatusBarIconControllerImpl;->setMobileIcons(Ljava/lang/String;Ljava/util/List;)V
Lcom/android/systemui/statusbar/StatusBarMobileView;->initViewState(Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;)V
Lcom/android/systemui/statusbar/StatusBarMobileView;->updateState(Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;)Z
Lcom/android/systemui/statusbar/StatusBarMobileView;->applyMobileState(Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;)V
Lcom/android/systemui/statusbar/StatusBarMobileView;->updateImageDrawable()V
Lcom/android/systemui/statusbar/StatusBarMobileView;->applyDarknessInternal()V
Lcom/android/systemui/statusbar/MiuiStatusBarIconViewHelper;->transformResId(IZZ)I
```

`initViewState` 和 `updateState` 把原生 `strengthId` 用作 drawable ID。两个控制器都反转输入列表，再由 holder 和 `StatusBarIconController$IconManager` 将状态交给 `applyMobileState`。该入口内部复制状态，且原生状态相等时可跳过更新。`onDarkChanged` 在颜色变化时调用 `updateImageDrawable` 重画原生图标。

A13 在控制器处复制两个状态，使用私有的可变列表。双排等级与原生资源 ID 分开，视图更新完成后绘制第二排。原生状态更新前恢复视图的 drawable 和可见性，以支持单卡、隐藏、飞行模式和漫游回退。veux 的右控制器与视图命中来自 CRC 失败的 `classes2.dex`，左右控制器与视图绑定均待验。

**扩展电源菜单（#14）**。r13.14.1 资源表缺少过去存在的 `raw/extended_power_menu`。报告日志出现资源 ID 0 和原生电源菜单。xaga `MiuiSystemUI.apk!classes.dex` 的 `MiuiGlobalActions` 构造函数调用 `Lcom/miui/maml/util/ZipResourceLoader;-><init>(Ljava/lang/String;)V`，后者定义在 `classes2.dex`。`Lcom/android/systemui/plugins/PluginEnablerImpl;->isEnabled(Landroid/content/ComponentName;)Z` 定义在 `classes.dex`。veux 的有效 `classes.dex` 可确认这两个入口声明，调用和 ClassLoader 待验证。`ExtendedPowerMenuHook` 使用直接资源引用和完整文件检查，SystemUI 重建后的原生菜单交接仍待确认。

**安全中心（#14）**。两份样本的 `classes2.dex` 均定义 `Lcom/miui/appmanager/ApplicationsDetailsActivity;->initView()V`、`Lcom/miui/appmanager/ApplicationsDetailsActivity;->onClick(Landroid/view/View;)V`，供默认打开方式 Hook 绑定。应用详情使用 Intent 的 `package_name`，禁用菜单依照 `miui.intent.extra.USER_ID` 保留原生跨用户操作。`Lcom/miui/powercenter/BatteryFragment$d;->handleMessage(Landroid/os/Message;)V` 定义在 xaga `classes2.dex`、veux `classes3.dex`，调用原生 `Landroid/os/Handler;->handleMessage(Landroid/os/Message;)V`，A13 温度 Hook 按弱所有者更新。报告的 11.1.6 的成员、类加载及菜单行为待对应 APK 或日志验证。

应用信息页在 xaga 8.0.4、veux 8.9.4 的 `classes2.dex` 中都由
`Lcom/miui/appmanager/fragment/AMAppInformationFragment;->onCreatePreferences(Landroid/os/Bundle;Ljava/lang/String;)V`
初始化本 Fragment 的偏好和 `PackageInfo`。同类的 `onPreferenceTreeClick(Landroidx/preference/Preference;)Z` 处理点击。
xaga 的 `c0()V` 调用 `b0(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V` 添加行，veux 用相同签名的 `h0()` → `g0(...)`。
A13 绑定实际 Fragment 的生命周期、唯一签名和当前 Activity，不按混淆名猜测。11.1.6 未出现在本地两份样本中。

**锁屏可视化（#14）**。xaga 有 CentralSurfaces、KeyguardStateController 和 NotificationMediaManager 对应入口。源码复用当前面板视图，替换面板时移除旧视图。仅允许控制器模式下，无活动会话则跳过音频服务查询。报告设备的失效点仍待定位。

历史日志仅覆盖报告中的发布版本。候选版的宿主回调、显示与生命周期保持待验。

## 输入身份（SHA-256）

路径相对于各自 ROM 根目录。后续更换输入时重新取证，不复用局部 method_idx。

| 样本 / 文件 | SHA-256 |
| --- | --- |
| xaga `system/system/framework/services.jar` | `66cceb59614c69f87ad8cb10ac7c357c8f27875aa75eb6118457807e075d7a7f` |
| veux `system/system/framework/services.jar` | `4cc071acdf3bb53747b4ddbd1d1c699d6f84fd88f7588b0221c3e93cc36f3fb0` |
| xaga `system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | `c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98` |
| veux `system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`（仅 `classes.dex` 有效） | `f0b15216121a8ba92b28043313e90ee96fa640724d190748a28dea95882c0768` |
| xaga `product/priv-app/MIUISecurityCenter/MIUISecurityCenter.apk` | `00d0ae515d36c7b152914d5e8a8e16ef02e116a542c241eafb8e418d7dc381a2` |
| veux `product/priv-app/MIUISecurityCenterGlobal/MIUISecurityCenterGlobal.apk` | `351b9bd398b0b8686cb589222de674a354041f94e6b068eec55355e32edcbc3c` |
| xaga Home（历史核心维护输入） | `4a777f4e6bad31f892a45c6565f04159db503a1177e8930591d7c1223d52eed8` |
| xaga `system_ext/priv-app/Settings/Settings.apk` | `c676552ec539e492c47b25294e05085176f75ce0c8eb829fab040fdb2d96e435` |
| xaga `system/system/framework/framework.jar` | `093f53a068eaaabef145e7580825cac1c6dc681e835cd865e078b4a77c327b7a` |

定向复查方法与输入变更条件见 [DexSQL](../../tools/dexsql/README.md)。
