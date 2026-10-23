# A13 精修台账

更新：2026-10-24。按本轮用户要求逐项修复明显问题，使用 DEXSQL，优先提高 Hook 准确性，并退役有明确依据的失效功能。

## 第一批边界

- Base SHA：`1821dbb0c3e0b4dbf1df73cc0ffecaa6f2ea1f24`；工作分支：`devin/a13-hook-accuracy-20261024`。
- 共享源码目录没有 `.git`。已核对它与该 SHA 的所有已跟踪文件：生产源码、测试、构建配置一致；仅 README 中英文和两个 Agent skill 有既存差异。本批在独立分支实现，仅同步本批改动，不覆盖这些差异。
- 当前用户提供的 API 101/102 边界继续有效；旁边较新 A13 副本仅作只读参考，不整体覆盖本目录。
- 不迁移基础架构，不改变签名、版本、依赖，不发布或推送。没有设备运行结果。

| 编号 | 问题及决定 | 分类 | 状态 |
| --- | --- | --- | --- |
| H01 | 来电不中断使用旧 `events:int`、`notifyCallState(int,String)` 和 `notifyCallStateForPhoneId`；改用 A13 `Record.matchTelephonyCallbackEvent(int)`，仅过滤选定应用的事件 6/36，不修改订阅集合 | `A13_COMPAT_VARIANT` | 已实施，针对性及完整 JVM 测试通过 |
| H02 | 通知渠道设置的 `NotificationUtil` 包路径错误，私有 Settings fragment 过时，启动用户取自 SystemUI；改用 A13 精确点击签名和公开渠道设置 action，用户由通知 UID 确定 | `A13_COMPAT_VARIANT` | 已实施，针对性及完整 JVM 测试通过 |
| R01 | 退役旧“振幅调节”功能：唯一目标 `VibratorService.doVibratorOn` 在两个完整 A13 services 输入中均不存在。移除设置入口、子页面、调度、合同及唯一实现；旧偏好留存但不再触发安装 | `DEAD_UPSTREAM_PATH`（限本表样本） | 已退役，旧偏好无安装及 UI/运行时无消费者检查通过 |

## DEXSQL 证据与复查

输入留在仓库外 `C:/Home/Project/forA13-ROM-ext`。使用 `classes` / `class_fields` / 定义标志筛选后，才判断成员是否存在；不能把仅有引用当成定义。跨 DEX 连接必须包括 `file_id`。可复查查询见 [hook_refinement.sql](../tools/dexsql/hook_refinement.sql)。

| 输入 | SHA-256 | 输入完整性与范围 |
| --- | --- | --- |
| xaga MIUI 14 `system/system/framework/services.jar` | `66cceb59614c69f87ad8cb10ac7c357c8f27875aa75eb6118457807e075d7a7f` | 两个 DEX CRC 通过 |
| veux HyperOS 1 `system/system/framework/services.jar` | `4cc071acdf3bb53747b4ddbd1d1c699d6f84fd88f7588b0221c3e93cc36f3fb0` | 两个 DEX CRC 通过 |
| xaga `system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | `c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98` | 三个 DEX CRC 通过 |
| xaga `system_ext/priv-app/Settings/Settings.apk` | `c676552ec539e492c47b25294e05085176f75ce0c8eb829fab040fdb2d96e435` | 全 ZIP CRC 通过 |
| xaga `system/system/framework/framework.jar` | `093f53a068eaaabef145e7580825cac1c6dc681e835cd865e078b4a77c327b7a` | 全 ZIP CRC 通过 |
| veux SystemUI | `f0b15216121a8ba92b28043313e90ee96fa640724d190748a28dea95882c0768` | `classes2.dex` / `classes3.dex` CRC 错误，排除；零行不能用于证明目标不存在 |

两份 services 都定义 `TelephonyRegistry$Record.callingPackage:String`、`eventList:Set`，没有 `events:int`。
`notifyCallState(int,int,int,String)` 和 `notifyCallStateForAllSubs(int,String)` 调用同一匹配器；Smali 确认前者分别以 36、6 分发旧/新来电状态。匹配器保留原有 callback 和订阅检查；模块只在命中筛选时返回 false，其他路径执行原方法一次。

xaga 定义 `NotificationUtil.isHybrid(ExpandedNotification):boolean`，位于 `com.android.systemui.statusbar.notification`。
点击入口是 `MiuiNotificationMenuRow.onClickInfoItem(Context,NotificationMenuRowPlugin$MenuItem):void`；原生小程序处理必须保留。
Settings Manifest 的公开 `android.settings.CHANNEL_NOTIFICATION_SETTINGS` action 指向导出的 `com.android.settings.notification.app.ChannelPanelActivity`，由 Settings 选择自己的 fragment。
framework 定义 `UserHandle.getUserHandleForUid(int):UserHandle`。这些证据不代表其他 ROM 或设备已验证。

## 语义来源

核对顺序：

1. [MonwF 当前 System.java](https://github.com/MonwF/customiuizer/blob/dfd3a4de5201d058357c692250e35b1cb74edfb6/app/src/main/java/name/monwf/customiuizer/mods/System.java)：保留按应用抑制来电干扰和直达通知渠道设置的用户意图；当前上游仍有旧 telephony 字段，不能作为 A13 ABI 证据。
2. A13 初始提交 `c696d33` 的 `System.java`：确认旧目标与入口来源。
3. 当前 A13 Base SHA：结合真实签名修正旧路径；旧测试夹具与错误旧 ABI 一致，产生假通过。
4. A14 只读观察 SHA `447aeb175f18745ae7cddd05c89af66ec0dd510d`：仅参考公开 Intent、失败回退和精确匹配思路。其 `listen(...)` 注册过滤、单 boolean 菜单及振动实现不移植至 A13。

## 退役闭环

R01 仅针对振幅缩放与定时调节；选择应用振动、静音振动和指纹反馈是独立功能。
删除前核对 Manifest、R8、反射字符串和 preference 路径。保留已有备份中的 `system_vibration_amp*` / `pref_key_system_vibration_amp*` 值，不清空用户数据；删除唯一消费者，恢复旧备份不会重新安装该功能。翻译字符串和历史审计保留历史含义，不代表仍有可达设置。

## 验证记录

- 基线 fast + `NoCallInterruptionCallbackTest`：通过；仅将夹具更正为 ROM 中的 A13 签名后，同一 5 项测试全部失败，确认旧合同阻止安装。
- H01 针对性 9 项，H02 针对性 6 项通过。覆盖两类来电事件、应用取消勾选、关闭功能后恢复、原生未订阅结果、精确点击重载、通知所属用户、小程序/默认渠道回退、启动失败回退、折叠失败不重复启动、包装的致命异常继续抛出。
- 完整 JVM：1233 项，0 失败/错误，1 skipped；Python：1340 项，0 失败，4 skipped。编译、fast、full（包含 lint）、compileall、差异检查通过。所有 Android 验证均在本批独立分支目录运行；未生成 APK。
- 退役后活动 catalog 68→67、注册表 8→7、直接 Hook 调用点 634→632；运行时断言继续保留，并增加退役回归。过程矩阵和审计索引按源文件刷新，legacy census 仍为 522/212。两个历史任务范围检查仅为本分支增加明确文件清单，未取消对其他文件的保护。
- `STATIC_VERIFIED = YES`（上述实际查询范围）；`BUILD_VERIFIED = YES`。
- 未取得 `LOG_VERIFIED` / `DEVICE_VERIFIED`，不声明运行时性能收益。

后续每批先确认目标、消费者和失败语义，再修改并记录结果。其他功能不能仅凭单个样本查询零行就批量删除。

## 第二批 H03：熄屏动画时长

- 本批 Base SHA：`67b04a796c23eb140be60dd2428c3ece961319a5`；继续使用同一独立工作分支。共享源码与该提交核对一致，README/skill 既存差异继续保留。
- 范围：只修复 `ScreenAnimHook` 的 A13 目标与必要依赖，分类 `A13_COMPAT_VARIANT`。不改变设置键、时长含义、默认值、版本、依赖或其他显示功能；不推送、不发布。
- 再次校验两份 services 的 SHA-256 与全 ZIP CRC，结果与上表一致。DEXSQL 在两份输入中都只找到 `DisplayPowerController.initialize(I)V`（private），没有无参方法。
- 同类字段均为 `mColorFadeEnabled:Z`、`mColorFadeFadesConfig:Z`、`mColorFadeOffAnimator:ObjectAnimator`。两个 ROM 分别解码，均先读取渐隐标志，再创建原生亮屏/熄屏 animator；原生默认亮屏 250ms、熄屏 100ms。模块只修改熄屏 animator，保留原方法一次调用和初始屏幕状态参数。
- 语义核对顺序：MonwF 当前 `dfd3a4de5201d058357c692250e35b1cb74edfb6` 的 `System.java::ScreenAnimHook` → A13 初始 `c696d33` → 本批 Base SHA → A14 只读观察 `447aeb175f18745ae7cddd05c89af66ec0dd510d::SystemDisplayHooks.ScreenAnimHook`。三者仍沿用无参入口，不能代替 A13 ABI 证据；保留先开启渐隐、原生创建动画、再设置时长的既有意图，不移植 A14 的实现。
- 合同将精确 `initialize(int)` 与三个字段作为同一必要集合；安装前校验字段类型和 void 返回值。缺少依赖时整项跳过，类型不符时在创建 Hook 前失败。字段只在安装冷路径解析，回调使用已经解析的字段。
- 生命周期：保持按 controller 所有者注册偏好回调，同一所有者替换注册，重新初始化后读取当前 animator；原生初始化失败时不注册 observer，原始异常继续抛出，包装的 JVM 致命错误继续传播。
- JVM 夹具按实际 A13 ABI 建立；修复前 8 项中 7 项在安装断言失败，关闭功能用例通过。修复后针对性 9 项通过，新增逐个必要字段缺失检查，并覆盖前后顺序、参数传递、亮屏时长不变、250ms 回退、所有者替换、偏好更新不重装、普通/致命异常传播。测试 animator 仅存储时长，不模拟渲染或证明设备动画效果。
- 已同步过程矩阵、parity 记录和 legacy 索引摘要；直接 Hook 数量仍为 632。历史 launcher 任务范围检查仅为当前分支增加本批的 `SystemDisplayAndWindowHooks.kt`，不取消其他文件的保护。
- 完整门禁通过：full 包含编译、1242 项 JVM 测试（0 失败/错误，1 skipped）和 lint；1340 项 Python 测试通过（4 skipped），compileall 与差异检查通过。`STATIC_VERIFIED = YES`；`BUILD_VERIFIED = YES`。无日志或设备验证，未生成 APK。

下一候选 H04：Toast 时长的旧无参 `showNextToastLocked()` 在两份 services 中都不存在，实际为 `showNextToastLocked(boolean)`；后续需同时审计原生时长调度、辅助功能建议时长和 DisplayPolicy 路径。本批只登记，不将改签名当作完整修复，也不退役仍有有效 A13 目标的功能。
