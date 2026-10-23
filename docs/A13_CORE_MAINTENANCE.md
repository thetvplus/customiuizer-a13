# A13 核心精修与设置页维护

日期：2026-10-24。Base：`09b63af9cc835137fab7d22899a1dfe51d218955`。
分支：`devin/a13-core-pages-20261024`。A14 只读参考：`eae2529d1af5e513c8a58c8ce1d198d371e63516`。

## 范围与旧分支整合

重新获取远端后，main 与精修分支仍分别为 Base 和 `fdea72ba4825cf2ca3e99a0da4892c0627dc3011`。
从 exact Base 创建独立工作区；共享源码目录没有 `.git`，其 README/技能差异及 A14 未提交修改均保留。
旧分支仅提取经过审查的行为、合同与测试，未合并其工程架构、API 101、版本、依赖或历史清理。
保留 main 的 API 102、同步 lazy、按 ClassLoader 安装状态及部分失败终端处理。
旧分支原始证据见其固定提交的 [精修台账](https://github.com/thetvplus/customiuizer-a13/blob/fdea72ba4825cf2ca3e99a0da4892c0627dc3011/docs/A13_HOOK_REFINEMENT.md)。

| 功能 | 本次实际处理 | 分类 |
| --- | --- | --- |
| 来电不中断 | main 已有事件 6/36 过滤，保留，不重复开发 | A13_COMPAT_VARIANT |
| 通知渠道设置 | 保留公开 Intent、通知用户和失败回退；补精确点击重载及 boolean 返回校验 | A13_COMPAT_VARIANT |
| 旧振幅/定时调节 | 提取 R01 退役，移除 UI、唯一调度、合同与实现；旧偏好及备份值保留 | DEAD_UPSTREAM_PATH，限两份 services 样本 |
| 熄屏动画 | 提取 H03：`initialize(int)`、三个必要字段，保留先开启渐隐、原生初始化、后调时长 | A13_COMPAT_VARIANT |
| Toast | 提取 H04：在原生同步时长建议前调整基础值，保留无障碍建议、动画补时与一次调度 | A13_COMPAT_VARIANT |
| 按应用振动 | 提取 H05：A13 六参数精确入口；初始化失败不监听，所有者替换和关闭语义保留 | UPSTREAM_INTENT_EQUIVALENT |
| 音频焦点 | 提取 H06：只抑制 CAN_DUCK helper，保留外层焦点记账、策略及 MIUI 分支 | A13_COMPAT_VARIANT |
| 即时音量 | 提取 H07：只改成功的媒体流结果，原始异常和长按状态保留，字段冷路径解析 | UPSTREAM_INTENT_EQUIVALENT |
| 桌面 Dock 手势 | 取消/多指后清理；重复 UP 不再次执行；距离、速度及原生事件分发保持 | UPSTREAM_INTENT_EQUIVALENT |
| 安全中心电池温度 | 修正 Handler 子类识别，缓存弱所有者字段，原生失败/所有者失效/温度未知时跳过 | A13_COMPAT_VARIANT |

语义核对使用 MonwF `dfd3a4de5201d058357c692250e35b1cb74edfb6`，A13 初始 `c696d33`、main 与旧精修分支，最后参考固定 A14。
电池温度保留 A13 的 BatteryFragment；未采用上游较新的 ChargeProtectFragment 或 A14 的 SmartChargeFragment。

## 核心审查与性能边界

桌面检查文件夹宽度/布局所有权、图标缩放、动画与 Dock 手势；本轮只修复能由事件状态证明的问题。
状态栏检查时钟格式/失效、网速 View 缓存、电池入口、插件与主进程分流；保留已有成熟路径和回归测试。
手势/MultiAction 保留近期选择器结果、Back Stack、用户/动作归一化及保存修复。
系统修改限上表声音、振动、显示、通知路径，不扩展权限或完整性绕过范围。

可由源码核对的变化：

- 即时音量：从每次按键查找控制器字段、复制参数数组，改为安装时解析一次 Field、回调直接读参数。
- Toast：不再遍历队列、撤销消息或重设已有 Toast 的计时；原生调度执行一次，无新增监听/任务。
- 旧振幅：移除无效安装调度与独有时段 formatter；活动 catalog 68→67。
- Dock：使用事件时间，取消后不再执行动作；不新增线程、监听或缓存容器。
- 搜索：在局部列表建立完整索引后原子发布，过滤器不再遍历正在 clear/add/sort 的共享列表。
- 页面分组：每个 Fragment View 一个 decoration；只在 adapter 通知或 screen 更换后重建行模型，销毁 View 时释放观察者。

这些是静态操作与生命周期变化，没有 CPU、内存、功耗或流畅度实测收益声明。
Dock assistant 的区域缓存在配置/Launcher 重建后的行为仍值得实机复核，未以推测性优化改写。

## DEXSQL 与输入

复用现有 MCP，先读取实际表结构，查询只取定义，跨 DEX 保留 file_id；样本留在仓库外。
重新校验下列输入 SHA-256 与 DEX CRC；xaga Home/SecurityCenter 另做全 ZIP CRC，均通过。

| 输入 | SHA-256 | 本轮结论 |
| --- | --- | --- |
| xaga services | `66cceb59614c69f87ad8cb10ac7c357c8f27875aa75eb6118457807e075d7a7f` | initialize(int)、Toast 调度、焦点 helper、六参数振动、音量签名/字段 |
| veux services | `4cc071acdf3bb53747b4ddbd1d1c699d6f84fd88f7588b0221c3e93cc36f3fb0` | 同一组 A13 定义独立确认，不混用局部索引 |
| xaga Home | `4a777f4e6bad31f892a45c6565f04159db503a1177e8930591d7c1223d52eed8` | HotSeats.dispatchTouchEvent(MotionEvent):boolean、assistant canInterceptTouch 及 getHotSeats 定义 |
| xaga SecurityCenter 8.0.4-230729.0.2 | `00d0ae515d36c7b152914d5e8a8e16ef02e116a542c241eafb8e418d7dc381a2` | BatteryFragment$d extends Handler，内部持 WeakReference，handleMessage(Message)；应用详情现有入口定义仍在 |
| xaga SystemUI | `c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98` | 通知点击、CentralSurfaces、MAML/插件入口定义仍在，不据此声明相关功能完整可用 |

Smali 核对焦点外层先记账再调用 helper，以及 BatteryFragment handler 先调用原生、检查弱所有者/Activity，再按消息 1 更新显示。
veux SystemUI 的 classes2/3 CRC 错误已重新确认并排除，不能用零行证明目标不存在。
ROM 版本/地区及其余已知损坏范围见 [A13_SAMPLES](rom-intelligence/A13_SAMPLES.md)。

## Issue 存续判定

保留状态与特定版本的兼容证据分别判断；源码有入口不等于设备可用。

| Issue #14 功能 | 状态 | 本轮决定 |
| --- | --- | --- |
| 锁屏音乐可视化 | RETIRE_CANDIDATE | Installer/实现/相关 ABI 尚在；存在静态 View/媒体所有权及多入口维护成本。缺 A13 运行证据，保留现状，不重建音频框架、不擅自删除 |
| 扩展电源菜单 | RETIRE_CANDIDATE | MAML 资源替换、外部命令与禁用原生插件链仍在；验证成本较高，保留，不增加新的旧版兼容层 |
| 安全中心电池温度 | ACTIVE | 修复确定的类型识别错误；8.0.4 样本静态支持，11.1.6 仍待确认 |
| 应用状态控制/禁用 | ACTIVE | 当前 Installer、偏好、菜单入口均保留；不扩展绕过范围；11.1.6 ABI UNCERTAIN |
| 默认打开方式替换 | ACTIVE | initView/onClick 定义仍在，原有功能保留；11.1.6 ABI UNCERTAIN |
| Play Store 跳转 | ACTIVE | AppInfo 路径及按当前 Fragment 取包信息的既有回归保留；11.1.6 ABI UNCERTAIN |
| 启动应用 | ACTIVE | 保留当前用户/双开与无启动入口回退；11.1.6 ABI UNCERTAIN |

未取得 Issue #14 报告的安全中心 11.1.6 输入或 A13 设备，本轮不将其标记为 Fixed。
Issue #12 只回归 main 已有的 142 字节长键备份修复、旧备份恢复、提交失败回滚；未重新开发备份格式。
不处理新功能请求，不更新/关闭 Issue。

## 全量页面与配置兼容

- 32 份现有偏好 XML 全部使用统一分组外观；23 个详情页增加搜索路由，共扫描 626 个叶入口，584 个可见且有标题的可搜索入口。静态隐藏的旧入口不加入搜索，其历史值保留。
- 首页四入口保持。系统选择器分为界面通知、锁屏安全、显示动画、音频振动、其他行为；不改变原有子分类键。
- 桌面增加布局分组；图标缩放归到图标/文字，动画归到动画/修正；修改控制器分支，保留 Launcher 变体显隐/禁用规则。
- 12dp 边距/圆角、浅深色 surface、统一分类留白。复用现有 Preference 与编辑页 FrameLayout，无 A14 生成器/Compose/新框架。
- 首页菜单保留唯一重启入口，其子菜单继续提供 Launcher、SystemUI、安全中心和软重启。
- 导航按次创建子页面，保存状态后不再提交，快速重复点击不重复入栈；返回继续使用既有 Fragment Back Stack。
- 搜索详情使用原页面控制器及 standalone/dynamic 参数；飞行模式页面保留权限前提；高亮沿用现有生命周期清理。
- 滑杆保留现有分步、默认值、偏移/小数显示及自定义回调；修复回收行残留监听和摘要/说明显隐。直接输入涉及不同显示单位与特殊回调，本轮未增加新的数值转换/迁移状态。
- 应用、多动作、颜色选择继续使用现有组件与保存路径；未修改选择器结果回传协议。
- `tools/check_settings_coverage.py` 已进入 fast/full：全部页面、详情入口、分类可达性、重复键、依赖、标题/数组及历史保存语义指纹均检查。新增孤页/改默认值/移除键/错依赖/错路由的失败用例。
- 指纹基于 Base，忽略顺序和外观；只明确排除退役振幅入口与页面。旧振幅键和移动的桌面值通过导出/解码/恢复测试继续留存。

## 验证与交接

最终源码通过以下门禁；先前发现的夹具旧签名、旧页面字段形状断言及隐藏搜索入口均已修正并重新验证。

- `python tools/verify.py fast --changed` / `full`：通过；Kotlin、Java 编译通过，150 个测试类共 1325 项 JVM 测试，0 失败/错误、1 skipped；lint 0 errors、696 warnings。
- `python -m compileall tools`：通过；Python 工具测试 896 项，0 失败、4 skipped。
- `:app:minifyReleaseWithR8`：通过，只运行优化任务，不组装 APK。最终 DEX 1,514,912 字节，SHA-256 `73bf38403eb57f7e41f665e02b6237563ab1eceacb0f565a3f51b4744b8cbe64`。
- DEXSQL 检查该 R8 DEX：Hook API 定义 0，所查询 Manifest 入口对 Hook API 的直接方法调用 0；明确核对 MainApplication/MainActivity/PrefsProvider/GateWayLauncher 定义仍在。该查询不等于传递类加载或设备证明。
- 页面/保存语义覆盖、UTF-8、catalog、Hook 所有权、source hazard、CI 可移植性/工作流合同与 `git diff --check`：通过。67 个活动 catalog、67 个字面分发、633 个 Hook 调用点，无 UNKNOWN 所有权。
- 最终差异复核保留 main 并发/生命周期基础设施，确认无版本/依赖/签名变更，无新增长期后台行为；没有以关闭检查或删除回归制造通过。

尚无已连接 A13 设备；不生成 APK，不改版本/签名，不合并 main、不创建 tag/release。
STATIC_VERIFIED = YES（限以上源码/测试与样本范围）；BUILD_VERIFIED = YES。
LOG_VERIFIED / DEVICE_VERIFIED 未取得。实际浅深色页面、旋转/重建、原生 Launcher 动画、Toast/音频输出仍待 A13 实机验收。

后续只建议补取安全中心 11.1.6 的实际输入、完成 A13 设备验收、决定两个 RETIRE_CANDIDATE 的存续，及复查 Dock assistant 区域缓存；不扩展为新一轮功能追赶。
