# A13 DexSQL Hook 精修计划

发布候选为 `r13.14.3`。从 `r13.14.2` 之后的主线实现开始，覆盖当前能从安装器触发的 Hook，包括动态目标、插件、构造器和资源 Hook。历史上已退役的入口按现有记录处理。参考 Telegram Android 的直接实现、局部状态和明确取消路径，复用项目自己的 Hook API 和回退机制。

| 轮次 | 宿主与范围 | 优先核对 |
| --- | --- | --- |
| 1 | SystemUI 宿主 | 通知、状态栏、锁屏、截图、导航、电源菜单。先校准浮动通知计时和实际通知决策链。 |
| 2 | SystemUI 插件 | 控制中心、快捷开关、音量和滑杆。分清宿主声明、插件定义、插件 ClassLoader 与加载时序。 |
| 3 | Launcher 和独立 MIUI 应用 | Home / Global Launcher 的手势、Dock、图标、文件夹和最近任务；Settings、安全中心、安装器等各自的入口及 View 或 Activity 所有权。 |
| 4 | system_server 和 boot framework | 输入、显示、Toast、音频、振动、通话、窗口和其他系统服务。通用应用路径同时核对真实进程范围。 |
| 5 | 跨进程和发布候选收口 | 补漏和复查动态寻址、资源、类加载与偏好重启目标；汇总两份 ROM 的结果，运行完整门禁，复测有设备的路径。 |
| 6 | SystemUI 通知补漏 | 校准浮动通知的自动隐藏时长，并区分状态栏、锁屏与通知搁板的图标溢出路径。 |
| 7 | 状态栏通知图标 | 校准旧三点溢出提示开关，核对初始化、可见性切换及图标数量上限。 |
| 8 | 静音时禁止浮动通知 | 核对静音状态来源与全屏通知的共用判定链，校准普通浮动通知。 |
| 9 | 展开后的浮动通知 | 校准展开状态和原生计时交接，核对正在回复、菜单及全屏候选。 |
| 10 | 浮动通知下滑 | 核对按应用的小窗资格、原生小窗启动及通知抽屉手势的优先级。 |
| 11 | 浮动通知居中 | 校准垂直位置读取，补查自动展开的绑定与固定时序。 |
| 12 | 浮动通知自动展开 | 校准入队事件、延迟展开及活动条目复用。 |
| 13 | 通知抽屉自动展开 | 将应用名单覆盖合入原生展开状态更新。 |
| 14 | 解锁后保留通知 | 校准锁屏显示策略与解锁清理的边界。 |

每轮从 [进程矩阵](A13_PROCESS_MATRIX.md) 和实际安装器追到功能的读取点。按宿主或插件批量查声明，再对疑点查调用者、字段和少量 Smali。对无异常且证据充分的目标记录复用来源。有差异时记录实际目标、修正或待验和对应样本。动态寻址与资源分别检查运行时选择条件和 APK 资源表。最终用活跃安装器回查覆盖范围。

两份 ROM 优先使用 [已核实的样本输入](A13_SAMPLES.md)。输入变化或损坏影响结论时补查相关条目。每轮复用同一输入的有效证据。xaga 是 MIUI 14 主样本，veux 是 HyperOS 1 实验样本，损坏 DEX 相关目标待完整输入。正式发行步骤遵循 [A13_RELEASE](../A13_RELEASE.md)。

## 第 1 轮起步

用 DexSQL 0.1.1 对 xaga SystemUI 宿主的 147 组字面类名及方法名做定义筛查，125 组命中。这个初筛用于排序，参数、继承、动态目标与插件定义需要单独核对。

| 功能 | 首批结果 |
| --- | --- |
| 浮动通知不自动隐藏 | 旧的四个目标均不在原声明类。已按实际浮动条目更新路径修正，保留原生显式移除。详见 [通知计时校准](A13_SAMPLES.md#通知计时校准)。 |
| 静音时禁止浮动通知 | xaga 调用是 `shouldHeadsUp(NotificationEntry)` → 单参数 `shouldPeek` → 双参数 `shouldPeek`。仍需核对静音状态来源与有效 ROM 变体。 |
| 通知抽屉按钮与快捷应用 | xaga 已确认清空按钮、两种通知头部和三个原生启动 helper 的定义及直接调用。 |
| 音量面板 | xaga 插件 `classes.dex` 定义 `MiuiVolumeDialogImpl.computeTimeoutH()I`、`showH(I)V` 等。控制中心和音量相关入口在第 2 轮按插件 loader 继续核对。 |
| 浮动通知显示时长、隐藏更多图标 | xaga 旧字段或目标缺失。继续查当前推荐时长链和图标溢出机制，验证语义后修改。 |

浮动通知本批证据来自 xaga 静态 DEX 与宿主模型测试。veux 的相关 SystemUI DEX 待补。实机优先验证持续显示、手势或点击关闭、通知撤回和 AOD 脉冲回退。

## 第 2 轮 SystemUI 插件

| 功能 | 静态复查与处理 |
| --- | --- |
| 插件加载 | xaga 宿主的 `PluginInstance$Factory.create` 调用精确的 `getClassLoader(ApplicationInfo, ClassLoader)`，然后使用返回的插件 loader。音量和控制中心外观、网格、状态栏手势的插件入口都使用这一个签名，仅在匹配包名且成功取到 loader 后各安装一次。 |
| 音量面板自动隐藏 | 插件的 `rescheduleTimeoutH()V` 调用 `computeTimeoutH()I`。展开和折叠自定义时长走原生无障碍换算，悬停及未配置时沿用原生结果。关闭静音震动绑定插件的 `vibrateH()V`。 |
| 独立通知滑杆 | xaga `MiuiVolumeDialogImpl` 构造及设置变更走 `Util.isNotificationSingle(Context, int)`，字段 `sIsNotificationSingle:Z` 存在。原生 `addColumn` 有四参和五参两种，现有旧版分支限定四参入口。 |
| 控制中心网格与手势 | xaga 插件定义 `QSPager(Context, AttributeSet)`、`distributeTiles()V`、`StandardTileView.createLabel(Z)V` 和 `ControlCenterWindowViewImpl.handleMotionEvent(MotionEvent, boolean)Z`。 |
| 控制中心样式 | xaga 插件定义 `MainPanelHeaderController` 的可见性、约束和时钟更新方法。双参 `PluginComponentFactory.create` 指向插件启动路径，委托单参重载并绑定宿主 Context。 |

样本来源、时长分支和复查查询见 [插件加载和音量计时](A13_SAMPLES.md#插件加载和音量计时)。后续覆盖其余插件资源、动态目标和实机回调。

## 第 3 轮 Launcher 与独立应用

对 Launcher 的 96 组字面类名和方法名初筛。xaga Home 命中 90 组，veux Global Launcher 89 组，veux Home 90 组。签名和真实调用另做定向核对。

| 功能 | 静态复查与处理 |
| --- | --- |
| 桌面长按打开应用信息 | 三份样本都从长按菜单调用 `ShortcutMenuManager.startAppDetailsActivity(ShortcutMenuItem, View)`。国际版优先匹配与国行相同的入口，旧 Utilities 路径作为已有国际版兼容回退。 |
| 文件夹宽度 | 三份样本的 `Folder.bind(FolderInfo)` 和 `onScreenSizeChanged()` 调用 `updateViewsLayout()` 并重写内容宽度。模块在原生更新后重新应用宽度设置。 |
| Dock、手势和最近任务 | 三份样本的 Dock 高度及上下间距、`Workspace.onVerticalGesture`、`HotSeats.dispatchTouchEvent` 和最近任务相关样本入口已定向核对。 |
| 安全中心与安装器 | 两份安全中心样本定义应用详情 Activity 的初始化、点击和菜单入口，也定义应用信息 Fragment 的偏好入口。xaga 安装器 Manifest 包名与现有路由匹配。 |

样本范围和查询见 [桌面与应用详情入口](A13_SAMPLES.md#桌面与应用详情入口)。桌面资源覆盖、其余动态目标和设备行为仍待验证。

## 第 4 轮系统服务与 boot framework

| 功能 | 静态复查与处理 |
| --- | --- |
| 旋转动画 | 两份 MIUI 扩展定义四个实际动画工厂。门禁和安装改到同一组精确签名，保留宿主的状态更新和 Surface 处理。 |
| 充电时不点亮屏幕 | 两份 services 的 `wakePowerGroupLocked` 均返回 `void`，充电原因位于第 4 个参数。安装前核对完整签名。按设置拦截充电唤醒时返回 null。 |
| 输入、显示、音频和 Toast | 两份 ROM 的 MIUI 按键入口、显示初始化和变暗计算、音量别名、音量等级初始化、音频焦点、Toast 调度及振动服务入口均已做定义复查。 |
| boot framework | xaga 已复核无障碍时长和通知用户的 framework 方法。veux framework 的相关验证缺口见 [样本限制](A13_SAMPLES.md)。 |

调用链与 SQL 见 [系统服务旋转与充电唤醒](A13_SAMPLES.md#系统服务旋转与充电唤醒)。实机旋转、限时动画和充电模式仍待验证。

## 第 5 轮状态栏高度与跨进程几何

| 路径 | 静态复查与处理 |
| --- | --- |
| 系统窗口配置 | 两份 services 样本的 `DisplayPolicy.getStatusBarHeightForRotation(int):int` 都为各旋转方向填充状态栏高度缓存。模块在 `system_server` 启动时安装精确入口，按显示密度和旋转后的挖孔及瀑布屏边缘计算高度。原生配置调用先完成。 |
| 可见状态栏及实时 Insets | xaga 的 SystemUI 用 framework 高度资源生成各方向的状态栏窗口属性。两份 services 的原生 Insets 提供方消费这些属性，仍按系统规则处理挖孔、可见性和过渡。 |

本轮复用既有样本。应用使用系统窗口配置或原生 Insets 时无需额外加入模块作用域。直接读取未覆盖的进程资源或使用固定布局值的应用仍可能显示不同高度。需实机核对重启后系统尺寸、普通应用、沉浸式、横竖屏及桌面切换动画。五轮涵盖各自复查记录列出的入口；其它 ROM 变体和未列入样本的动态目标仍使用原有证据等级。见 [状态栏窗口与系统高度](A13_SAMPLES.md#状态栏窗口与系统高度)。

## 第 6 轮浮动通知自动隐藏时长

- xaga 的自动计时使用 `HeadsUpManager$HeadsUpEntry.getRecommendedHeadsUpTimeoutMs(int)`。自定义的 1–60 秒复用同一个无障碍换算器。已安装后切换回默认值或打开“不自动隐藏”，下一次计时走原生分支。原生最短显示窗口、手势扩展和显式移除仍由各自入口管理。
- xaga 的旧隐藏溢出提示入口及其字段缺失。当前原生布局通过图标容器的点状态生成提示，容器也用于通知搁板。本轮记录实际布局入口及共享范围，状态栏专用改动待单独校准。

本轮沿用 xaga SystemUI 静态证据，veux 对应 DEX 和两份样本的设备行为仍待验证。见 [浮动通知时长与溢出提示](A13_SAMPLES.md#浮动通知时长与溢出提示)。

## 第 7 轮状态栏三点溢出提示

xaga 的设置观察者在控制器初始化图标容器后立即派发一次可见性回调。模块在 `NotificationIconAreaController.onNotificationIconChanged(boolean)` 成功返回后，把该控制器持有的状态栏容器点数上限改为 0。原生回调和“通知图标数量限制”仍决定图标上限。只有当前点数上限大于 0 时才刷新一次状态栏容器，通知搁板和 AOD 容器继续由各自宿主管理。

“系统 → 状态栏”重新显示旧开关，沿用原偏好键、默认关闭及 SystemUI 重启规则。旧实现所需的入口和字段齐全时仍可回退。veux 对应 DEX 及实机溢出、锁屏过渡待验。见 [浮动通知时长与溢出提示](A13_SAMPLES.md#浮动通知时长与溢出提示)。

## 第 8 轮静音时禁止浮动通知

xaga 的双参 `shouldPeek(NotificationEntry, ExpandedNotification)` 参与普通浮动和全屏通知的取舍。模块在原生成功后过滤静音或振动模式下原本会浮动的普通通知。带有 `fullScreenIntent` 的来电、闹钟等候选由 SystemUI 用原判定处理。

复用 `MiuiPhoneStatusBarPolicy.updateVolumeZen()` 从铃声模式计算的状态，按两个精确签名安装。状态未知时保留原生判定，任一入口安装失败时不保留半组 Hook。veux 对应 DEX 及实机铃声模式切换待验。见 [静音与全屏通知判定](A13_SAMPLES.md#静音与全屏通知判定)。

## 第 9 轮展开后自动关闭

原生 `setExpanded(boolean)` 继续写入展开状态。普通浮动通知首次进入固定且展开的状态后，用 `updateEntry(false)` 恢复原生计时。每个条目只在自己的计时更新期间调整 `isSticky()` 的结果，计时时长接续原生时长和自定义延时，通知生命周期与锁屏切换使用原生的常驻判定。

正在回复、显示菜单或带全屏 Intent 的条目由原生逻辑管理。“不自动隐藏”优先。veux 对应 DEX 和实机展开、收起、回复、菜单交互待验证。见 [展开通知与原生计时](A13_SAMPLES.md#展开通知与原生计时)。

## 第 10 轮下滑与小窗

“允许小窗”通过 `NotificationSettingsManager.canSlide(String)` 应用每个包的名单设置，原生继续判断 Activity 类型、分屏、固定模式和窗口冲突。单独开启该设置时，由 SystemUI 原生启动小窗。两个开关同时开启时，“启用下滑手势”优先，触摸事件交由原生通知抽屉路径处理。

复用原有偏好键和名单顺序。veux 对应 DEX 及实机下滑、分屏、小窗交互待验证。见 [浮动通知下滑与小窗资格](A13_SAMPLES.md#浮动通知下滑与小窗资格)。

## 第 11 轮浮动通知居中

在 xaga 的 `HeadsUpManagerInjector.miuiHeadsUpInset(Context)` 原生计算成功后，从当前资源读取屏高、顶部留白和浮动通知高度。竖屏用原有居中公式，并以原生顶部偏移为下界。横屏、缺资源或空间不足时沿用原生值。密度和配置变化由 SystemUI 自己的尺寸刷新路径触发。

自动展开补查确认 `setHeadsUp(true)` 同时用于通知展示和层级刷新，`expandNotification()` 的原生点击会切换状态。自动展开时序在第 12 轮校准。veux 对应 DEX 及实机几何待验证。见 [浮动通知位置与展开入口](A13_SAMPLES.md#浮动通知位置与展开入口)。

## 第 12 轮浮动通知自动展开

自动展开在 `HeadsUpManager.onAlertEntryAdded(AlertEntry)` 成功返回后调度一次，保留 60ms 延迟、原生展开点击和两种应用名单模式。到时核对当前活动条目的原生移除任务，以及同一通知绑定的行，防止旧任务作用于复用条目。

展开前按原生点击会走的普通、固定或组通知分支读取当前状态。已展开、行已移除、未附着或处于锁屏时跳过。xaga 静态核对已完成，veux 相关方法体可读且吻合，样本完整性和实机行为待验证。见 [浮动通知位置与展开入口](A13_SAMPLES.md#浮动通知位置与展开入口)。

## 第 13 轮通知抽屉自动展开

xaga 的行状态更新先写入系统展开位，再刷新反馈图标。旧 Hook 在反馈图标回调中再次写入展开位，原生传入 false 时可能在同一轮布局触发先收起、再展开的状态变化。模块在精确的 `ExpandableNotificationRow.setSystemExpanded(boolean):void` 入口按原有应用名单调整入参，原生方法只执行一次。

锁屏或应用不符合展开规则时保留传入值。手动展开或收起仍由原生优先处理。veux 的同一段调用顺序可局部解码，样本完整性和实机布局仍待验证。见 [通知抽屉展开状态](A13_SAMPLES.md#通知抽屉展开状态)。

## 第 14 轮解锁后保留通知

使用 `NotificationSettingsHelper.alwaysShowKeyguardNotifications():boolean` 调整解锁后的保留决策。系统继续写入已显示标记，锁屏通知资格与解锁清理后的回填也继续走原生规则。缺少策略入口的旧版可通过原有 `hasShownAfterUnlock():boolean` 读取入口实现相同的保留意图。

xaga 已核对过滤、状态写入和清理回填三条路径。veux 的相关方法体可局部解码，样本完整性与实机重新锁屏结果仍待验证。见 [解锁后锁屏通知保留](A13_SAMPLES.md#解锁后锁屏通知保留)。
