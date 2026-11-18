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

## 通知计时校准

浮动通知不自动隐藏属于 `A13_COMPAT_VARIANT`。MonwF 当前上游已无该入口，A13 初始源码通过旧的 `HeadsUpManager` 私有入口阻止计时关闭，本地主线仍沿用该目标，A14 当前实现已移除该设置。保留 A13 设置的原意和 preference key。

xaga `MiuiSystemUI.apk!classes2.dex` 的 `HeadsUpManagerPhone$HeadsUpEntryPhone.updateEntry(Z)V` 同步调用 `AlertingNotificationManager$AlertEntry.updateEntry(Z)V`。父类先更新最早移除时间和状态，再为非 sticky 条目提交自动关闭回调。显式移除可单独经 `removeAsSoonAsPossible()V` 调度。

A13 在手机浮动条目的更新成功后复用 `removeAutoRemovalCallbacks()V`，仅取消已绑定、非 Doze 条目的自动关闭回调。原生显式移除、通知更新和 Doze 计时照常执行。安装前解析所需 Field 和 Method，视图或成员缺失时保持原生行为。核对查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。veux 对应 DEX 校验失败，实际行为待日志或设备验证。

## 插件加载和音量计时

xaga 宿主 `MiuiSystemUI.apk!classes.dex` 中，`PluginInstance$Factory.create` 调用 `getClassLoader(ApplicationInfo, ClassLoader):ClassLoader`。该入口可返回 null，也可从按包名的缓存返回 loader。模块在成功返回后绑定 `miui.systemui.plugin` 的实际 loader，空结果和异常可等待下一次宿主加载。各消费者保留进程内一次安装语义。

两个样本的 `product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk!classes.dex` 都显示 `MiuiVolumeDialogImpl.rescheduleTimeoutH()V` 调用 `computeTimeoutH()I`。原生折叠 2000ms、展开 5000ms、鼠标悬停 16000ms 都使用无障碍 controls 标志，安全音量提示的 5000ms 使用 controls 和 text。自定义时长依旧以原有偏好值为毫秒数，优先使用原生无障碍推荐值。未配置、悬停或必要成员缺失时交给插件原方法。

这两处属于 `A13_COMPAT_VARIANT`。时长语义参考 MonwF 当前主线、A13 初始时长设置及 A14 当前实现，实际入口与分支依据 A13 插件 Smali。xaga 和 veux 插件各 DEX 的 ZIP CRC 与 Adler-32 通过，头部 SHA-1 不匹配。结论覆盖所核对的成员和控制流。veux 宿主的同名入口来自 CRC 失败的 `classes2.dex`，其宿主加载链保持待验。复查 SQL 见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 桌面与应用详情入口

输入为 xaga 的 `product/priv-app/MiuiHomeT/MiuiHomeT.apk`，以及 veux 同路径 Home 和 `product/priv-app/MiLauncherGlobal/MiLauncherGlobal.apk`。三份 APK 的两个 DEX 均通过 ZIP CRC 与 Adler-32，头部 SHA-1 不匹配。Manifest 标明 Home 和 Global Launcher 分别是 `com.miui.home` 和 `com.mi.android.globallauncher`。

- 三份样本的 `classes2.dex` 均定义 `ShortcutMenuManager.startAppDetailsActivity(ShortcutMenuItem, View):void`，由长按菜单点击调用，参数提供组件名和所属用户。当前 MonwF 的入口与该目标一致，A13 初始实现按包名区分旧国际版，A14 当前实现也使用长按菜单目标。A13 优先挂载确认的完整签名，兼容保留缺少该入口的旧国际版。
- 三份样本的 `classes.dex` 均定义 `Folder.updateViewsLayout():void`，绑定数据或屏幕尺寸变化时原生重写内容宽度。模块只在原生成功更新后复用文件夹宽度设置。保留 `resetViewsLayoutParams()` 作为旧 ABI 的可选回退。
- Dock 的 `calcHotSeatsHeight(Context, boolean)`、上下间距方法、桌面滑动和最近任务过滤均保留对应宿主。两份安全中心的应用详情和菜单声明与当前绑定匹配，应用信息页仍使用原有 Fragment 所有权。

两项入口调整属于 `A13_COMPAT_VARIANT`。设备上的长按菜单跳转、多用户、文件夹旋转和折叠切换仍待验证。复查查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 系统服务旋转与充电唤醒

- **旋转动画**。xaga、veux 的 `system_ext/framework/miui-services.jar!classes.dex` 由 `ScreenRotationAnimationImpl.loadEnterAnimation` 和 `loadExitAnimation` 调用四参 `createRotationEnterAnimation(IIII)` 和 `createRotationExitAnimation(IIII)`。`loadRotation180Enter` 和 `loadRotation180Exit` 调用各自无参工厂。四个工厂均返回 `Animation`。进场 loader 还执行 Surface 隐藏。旧门禁查询的 `AppTransitionInjector.createAnimation` 在两个扩展输入中无定义，其 `QUART_EASE_OUT_INTERPOLATOR` 字段仍存在。原生工厂完成后才调整成功结果，保留空结果和异常。关闭动画仍设为 0ms，淡入淡出保留 300ms 和原有插值器。
- **充电唤醒**。两份 `services.jar!classes2.dex` 都定义 `PowerManagerService.wakePowerGroupLocked(PowerGroup, long, int, String, int, String, int):void`。`updateIsPoweredLocked(int)` 调用它并传入 `android.server.power:PLUGGED:` 前缀。模块检查唯一声明的全部参数及返回类型，保留 A13 两种充电选项原有的原因范围。仅在匹配时跳过原生唤醒并返回 null。其它唤醒仍调用原方法。

两项均属于 `A13_COMPAT_VARIANT`。旋转设置保留 A13 初始功能的模式，充电时使用与 MonwF 当前实现相同的 void 跳过值，并保留 A13 自有的选项 3 语义。A14 的选项 3 使用不同产品语义。复用已核实的两个 services 和 MIUI 扩展输入，结论限上述调用。查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 状态栏窗口与系统高度

- **系统窗口配置**。xaga、veux 的 `services.jar!classes2.dex` 中，`DisplayPolicy.onConfigurationChanged()` 调用 `getStatusBarHeightForRotation(int):int` 写入 `mStatusBarHeightForRotation`。原生 `getConfigDisplayHeight` 和 `convertNonDecorInsetsToStableInsets` 消费该缓存。系统 Hook 使用同一 policy 的 `mUiContext` 密度和 `DisplayContent.calculateDisplayCutoutForRotation(int)` 返回的 `WmDisplayCutout`。xaga framework 以 `max(安全顶部, 基础高度 + 瀑布屏顶部)` 计算高度。A13 模块沿用该公式和既有 SystemUI dp 转换。
- **实时窗口 Insets**。xaga SystemUI 的 `StatusBarWindowController.getBarLayoutParamsForRotation` 和 `applyHeight` 通过 `SystemBarUtils` 读取 `android:dimen/status_bar_height_default`。两份 services 都由 `DisplayPolicy.addWindowLw` 的 Insets 提供方读取 `getStatusBarHeight(DisplayFrames)`，使用该方向的 `WindowState` 属性和安全挖孔顶部。原生提供方还处理挖孔触摸区最小高度。

MonwF 当前实现与 A13 初始实现都从状态栏尺寸资源入手。第 5 轮以 A14 的系统窗口几何为语义参考，A13 系统接入口、资源及调用链按以上样本独立确认。veux SystemUI 对应 DEX 尚缺完整输入。复查查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 浮动通知时长与溢出提示

- **自动隐藏时长**。xaga 的 `HeadsUpEntry.calculateFinishTime()J` 从 `AlertingNotificationManager.mAutoDismissNotificationDecay` 取基础值，经 `getRecommendedHeadsUpTimeoutMs(I)I` 加到 post time。该方法优先读取条目的 MIUI float time，未提供时用 `AccessibilityManagerWrapper.getRecommendedTimeoutMillis(II)I` 和 flags `7`。手机条目另在父类结果上叠加原生扩展时长。旧实现写入的 `mHeadsUpNotificationDecay` 缺失。模块在取得实际 manager 和无障碍 wrapper 后才应用自定义基础时长，保留默认路径和原生扩展。分类为 `A13_COMPAT_VARIANT`。
- **溢出提示**。xaga 的 `NotificationIconAreaController.onNotificationIconChanged(Z)V` 调用状态栏容器的 `miuiShowNotificationIcons(Z)V`。`NotificationIconContainer.calculateIconXTranslations()V` 用 `MAX_DOTS` 写溢出条目的可见状态。通知搁板也使用同类，并调用 `hasMaxNumDot()`。旧的 `setIconsVisibility()`、`mMoreIcon` 和 `mForceHideMoreIcon` 在样本中未声明，校准须限定状态栏宿主。

第 7 轮另外核对 `NotificationIconAreaController.initializeNotificationAreaViews(Context)` 先用 `notificationIcons` 视图 ID 绑定 `mNotificationIcons`，随后构造函数注册到 `NotificationIconObserver.addCallback`。注册与后续可见性变更都走 `onNotificationIconChanged(boolean)`。xaga 原生 `miuiShowNotificationIcons(boolean)` 同时设置点数、日常图标和锁屏图标上限，再调用 `updateState()`。模块只修正状态栏容器的 `MAX_DOTS`，并在需要时调用一次 `updateState()`。原有图标上限功能的设置结果由原生回调与其现有 Hook 先完成。该入口调整属于 `A13_COMPAT_VARIANT`。

证据来自 xaga `MiuiSystemUI.apk!classes2.dex`，veux 对应条目待完整输入。查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 静音与全屏通知判定

- xaga `MiuiSystemUI.apk!classes2.dex` 的 `MiuiNotificationInterruptStateProviderImpl.shouldHeadsUp(NotificationEntry)` 先运行父类判断，再经单参 `shouldPeek` 调用实际的双参 `shouldPeek(NotificationEntry, ExpandedNotification):boolean`。父类 `shouldLaunchFullScreenIntentWhenAdded(NotificationEntry)` 会读取同一结果。带全屏 Intent 的通知在原生预期不浮动时可进入 `shouldSend`，所以静音过滤只作用于普通浮动通知。
- `MiuiPhoneStatusBarPolicy.miuiInit()` 会调用 `updateVolumeZen():void`。原生读取 `RingerModeTracker.getRingerModeInternal()`，将静音和振动记入继承的 `mMuteVisible:boolean`。勿扰状态单独计算。模块在原生更新成功后读取此字段，浮动回调直接读取进程内的状态。

初始 A13 和本轮基线都按双参数量筛选原生结果。当前 MonwF 和 A14 使用其它版本的通知判断点，本轮保留 A13 的双参专用入口并限定对全屏候选的影响。分类为 `A13_COMPAT_VARIANT`。查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

## 展开通知与原生计时

- xaga `MiuiSystemUI.apk!classes2.dex` 的 `StatusBarNotificationPresenter.onExpandClicked(NotificationEntry, View, boolean)` 经 `HeadsUpManager.setExpanded(NotificationEntry, boolean)` 调用手机条目的 `setExpanded(boolean)`。它先写入状态，展开时取消计时，收起时调用 `updateEntry(false)`。旧 A13 Hook 跳过原生展开并固定提交 5000ms 回调。
- 原生 `AlertingNotificationManager$AlertEntry.updateEntry(boolean)` 先取消旧回调，根据 `isSticky()`、完成时刻和最短显示窗口安排同一个移除回调。手机条目的常驻条件是固定且展开、远程输入、全屏 Intent 或 `mMenuShownPinned`。远程输入和菜单退出都能经原生 `updateEntry(false)` 恢复计时。锁屏状态切换和通知生命周期也会读取常驻结果。

第 9 轮用对应 A13 签名恢复原生计时，保持其它常驻查询的可见结果。当前 MonwF 使用另一套带取消原因的入口。A14 参考使用原生计时但菜单字段不同。A13 属于 `A13_COMPAT_VARIANT`，证据限上述 xaga 路径。查询见 [maintenance_targets.sql](../../tools/dexsql/maintenance_targets.sql)。

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
