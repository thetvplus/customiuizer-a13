# r13.14.0 RC 验收记录

日期：2026-10-24。版本：`r13.14.0` / versionCode `142`。
Base：`d017f4fbdeb23c40a446ee64444726be0e2d4030`（已合并 PR #15）。
分支：`devin/a13-rc-polish-20261024`。
代码及发布合同候选：`69753b03e003ad6f81e15e29f3535e13b361fd6e`。
RC 精校原为 3 个提交，追加自动化规则更新后共 4 个提交；最终完整 SHA、提交清单及证据由交接回执记录。

**结论：本地工程门禁通过，停在送审/准备发布阶段。已确认且尚未修复的 P0 = 0、P1 = 0。**
这不是 A13 实机验收或发行批准。用户追加要求自动完成分支推送、PR 和 CI，常规步骤不再逐项确认。
候选分支的远端结果由 PR / Actions 及交接回执记录；当前仍停止在 main 合并、正式签名和公开发布之前。
没有触发 Signed Build、创建正式 Tag、公开 Release 或同步 Xposed。main 保持 Base。

## 实际修复与边界

| 问题 | 最小修复与验证 |
| --- | --- |
| 未绑定滑杆从 View 取值，读到默认值，程序设值保存错误；回收后仍指向其他行 | 值独立于 View，保留分步/范围/显示单位；弱持有 View 并校验行所有者。生产值路径的未绑定读写、连续保存、范围变化用例通过 |
| Dock assistant 的 Rect 被同一全局回调永久复用 | controller 各自持有无 View 引用的坐标；每个手势第一次允许拦截时取当前 HotSeats。布局/密度/View 替换在下一手势生效；不每帧反射或计算、不新增监听 |
| 自定义电源菜单资源读取/安装失败仍禁用原生插件；单次 read 可截断且异常不释放文件 | 一次冷路径完整复制、ZIP/CRC/manifest 检查，所有依赖 Hook 成功后才启用替换。失败进入终端状态；缓存被清理时恢复原生路径。普通命令监听异常不重复执行已发出的系统操作 |
| 公开格式与目标不一致 | schema 2、固定 APK 文件名、版本标题、APK/元数据两个公开附件；原始 Artifact 仍严格保留五件校验材料。双仓读回、缺件拒绝及 draft 恢复同步更新 |

历史 preference key、默认值、备份格式、包名、兼容范围、证书及版本号未改。
不移植 A14 Hook；语义分类和固定参考见 [A13_PARITY](A13_PARITY.md)。

## 针对性验证

| 范围 | 已取得的证据 | 仍未取得的证据 |
| --- | --- | --- |
| 设置/搜索/备份 | 32 页面、626 叶入口、584 可搜索入口、23 详情路由及保存语义门禁通过；实际 decoration 的缓存失效、隐藏分类、adapter 替换/过滤恢复与 observer 释放测试；状态已保存/未附加/重复导航拒绝、恢复 resume 后解锁；长键、旧配置、异常备份不清空现值及提交失败回滚回归 | Android UI 渲染、完整转场/多级返回栈、旋转/Activity 重建、主题/输入法、滚动恢复、动态隐藏设置的实际高亮 |
| Launcher | Dock 区域变化、controller 隔离、空/失败区域、边缘坐标；既有取消、多指取消、重复 UP、距离/速度边界测试；原始 dispatch 未拦截，native canInterceptTouch 只在成功 true 后局部改为 false | 真实 Launcher 动画、文件夹/最近任务返回、运行中布局/密度变化；手势中的布局变化交由原生取消/重建处理，未声称实机覆盖 |
| SystemUI/系统服务 | 时钟、通知渠道、熄屏、Toast、音量、音频焦点和按应用振动既有纯逻辑/合成回调回归；电源资源缺失/损坏、短读、缺 Hook、致命异常、缓存清理边界 | SystemUI 进程重建、用户切换、真实音频输出/屏幕动画、电源菜单操作 |

两次 `adb devices -l` 均无设备。没有安装、重启或修改任何手机设置。
JVM 测试直接运行生产值/缓存/守卫代码，但没有 Android View 渲染或 ART Hook，不能升级为设备证据。

静态样本复核：xaga MIUI 14 / Android 13 Home 的 assistant 方法及 SystemUI 的
`ZipResourceLoader(String)`、`issueExternCommand(String, Double, String)`、`PluginEnablerImpl.isEnabled(ComponentName)`。
Home SHA-256：`4a777f4e6bad31f892a45c6565f04159db503a1177e8930591d7c1223d52eed8`；
SystemUI SHA-256：`c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98`。
样本定义不是通用 ROM 支持声明。

## 性能与生命周期

- 没有新增 Receiver、Observer、SensorListener、后台线程、Handler/Runnable 或长期轮询。
- 新增状态有明确所有者：每个 slider 一个数值、每个 assistant controller 一份手势坐标、每个电源功能一次资源准备；没有全局 View/Activity 强引用或无界 Map。
- Dock MOVE 不重复解析目标或计算 Rect；配置/布局变化后下一手势刷新。资源复制、ZIP 校验与文件可用性检查留在初始化/资源加载冷路径，关闭时不执行这些工作。
- decoration observer 替换和销毁闭环测试通过；普通异常保留原生行为，致命错误传播。
- 没有 CPU、内存、功耗或流畅度测量，不声明百分比收益。

## 工程门禁与 CI

| 门禁 | 结果 |
| --- | --- |
| 本地 Fast / Full | PASS；最终运行覆盖 Kotlin/Java 编译、JVM 和 lint |
| JVM | 155 类、1345 项；0 failure/error，1 项既有 skipped；本轮新增 20 项 |
| Python | 938 项；0 failure，4 项既有 skipped；compileall PASS |
| Lint | 0 error，696 warnings（与 Base 已有记录相同；未降低门禁） |
| 无密钥 R8 | PASS；只优化，没有组装 APK；DEX 1,517,036 字节，SHA-256 `68713f6e33da3b9ee60b040c3a02181550e763e77fd4f3cda6a5605f89213989` |
| 静态治理 | UTF-8、页面/保存、catalog 67、兼容合同、CI 可移植性、4 工作流合同、source hazard 及 diff-check PASS；633 Hook 点，UNKNOWN 0 |
| 已合并 Base 的远端 CI | [Fast 40048688551](https://github.com/thetvplus/customiuizer-a13/actions/runs/40048688551)、[Full 40048688603](https://github.com/thetvplus/customiuizer-a13/actions/runs/40048688603) 均 success；原生 main SHA 与 Base 一致 |
| 新 RC 的远端 CI | 专项分支通过 PR 触发 Fast，手动触发 Full，由 Agent 自主跟进实际结果；本地 Full 不能冒充 GitHub CI，分支 CI 也不能替代合并后最终 main 的原生 Fast/Full |

STATIC_VERIFIED = YES（限以上源码、单元测试及样本）；BUILD_VERIFIED = YES。
LOG_VERIFIED / DEVICE_VERIFIED 未取得。
R8 DEX 另经 DexSQL 核对：Hook API 定义 0；MainApplication、MainActivity、PrefsProvider、
GateWayLauncher 定义仍在。此检查不证明传递类加载或设置进程的设备行为。

## 发布保护、风险和维护者操作

两个 Environment 重新读回：reviewer `thetvplus`、禁止管理员绕过、仅允许 branch 类型精确 `main`。
`release-signing` 四个 Secret 名称齐全；`release-publishing` 仍缺 `A13_XPOSED_RELEASE_TOKEN`。
没有读取 Secret 明文，也未在本地加载正式签名配置。名称存在不证明私钥或密码有效。

两仓当前正式版仍为 r13.13.0。重新下载的 APK 均为 2,726,322 字节，hash 为
`b064712965098b9013a27dc448478afdf208c150aea00654960834b87a5dc47c`；v2、单签名者及证书
`c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70` 已验证。
新候选尚未签名，不能声称已完成新 APK 的证书连续性/attestation 验收。

错误 source SHA、未完成/跳过 CI、缺审批规则/Secret、错误证书、缺失/替换 Artifact、错误 tag、
重复版本、双仓部分成功和恢复来源不一致均由发布相关 42 项测试覆盖。GitHub 实际审批、正式组装、
新 APK 证书与 attestation 仍需受保护流水线执行，不能用这些 mock 预演冒充成功。

当前 main 没有 Ruleset/分支保护。最小提案见 [规则 JSON](a13-main-ruleset.proposed.json)，没有自动应用。
其含义：精确 main、禁止删除/force push、只允许 PR、无 bypass、要求 GitHub Actions（15368）的
`fast`，并要求分支基于最新 main；不把只在 main 运行的 `full` 设为 PR 必需项。个人仓库不额外
强制另一个批准者；正式签名/发布的人工 Environment 审核继续保留。
参数按 [GitHub Rulesets API](https://docs.github.com/en/rest/repos/rules#create-a-repository-ruleset) 核对。

后续维护者步骤：

1. 自动推送 RC 分支、创建草稿 PR、运行并跟进候选 Fast/Full。用户要求合并 main 时，在必要检查通过后自主通过 PR 合并，无需逐步追加确认；本次 RC 停止点仍保留。
2. 在仓库 Settings → Rules → Rulesets 审阅/导入提案，启用 Active，确认 bypass 为空及 `fast` 来源；或以 Administration write 权限 POST 该 JSON 到 `repos/thetvplus/customiuizer-a13/rulesets`。完成后读回验证。
3. 在 `release-publishing` 配置跨仓 Token（目标 Xposed 仓库 Contents/Workflows write），不复制未知旧 Secret。
4. 对最终 main 的完整 SHA 等待原生 Fast/Full success。当前 RC 尚不满足这个条件，**现在不启动正式 Signed Build**。
5. 明确批准签名候选后，手动运行 A13 Signed Build 并人工批准 `release-signing`；核验证书、APK、来源及原始 Run ID。签名候选本身不会公开发行。
6. 收到正式发布指令后，Promote 使用原始 Run ID、精确版本确认语句与 `release-publishing` 人工审批，双仓使用同一原始 APK；部分成功只恢复原始 Artifact，绝不重建/重签或覆盖 Tag。

SecurityCenter 11.1.6 缺目标样本；锁屏音乐可视化、扩展电源菜单维持 RETIRE_CANDIDATE。
Issue #12、#14 重新读取均为 OPEN，本轮没有修改 Issue 状态。上述设备/样本与发布准备缺口明确保留，
不以它们冒充 P0/P1 已完成的设备修复。完整发行操作见 [A13_RELEASE](A13_RELEASE.md)。
