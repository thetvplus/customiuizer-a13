# A13 / A14 功能与证据边界

A13 面向 MIUI 14 / Android 13，HyperOS 1 / Android 13 为实验兼容。
Android 14+ 与灵动额头 / Dynamic Island 明确不进入 A13。

已有 A13 功能先核对 MonwF/customiuizer 的用户意图和 A13 历史工作实现，再参考 A14。
全新 A14 功能以其用户可见合约为产品参考，A13 目标仍需独立证明。
FEATURE PARITY != CODE PARITY；禁止整文件盲拷或混用 ROM target。

## 分类与移植记录

- `UPSTREAM_EXACT`：保持 A13 上游原实现语义。
- `UPSTREAM_INTENT_EQUIVALENT`：实现不同，保持上游用户意图。
- `A13_COMPAT_VARIANT`：由 A13 API、ROM ABI 或生命周期差异要求的变体。
- `SEMANTIC_DRIFT`：语义已偏离，应说明或修正。
- `A14_NEW_FEATURE`：新功能，单独证明 A13 可用性。
- `DEAD_UPSTREAM_PATH`：A13 目标中不存在的上游路径。

移植只记录功能、固定 A14 来源 SHA、A13 状态、平台差异与证据。
审查 Android 13 API、MIUI 14 ABI、HyperOS 1 / Android 13 样本、进程 / ClassLoader、
生命周期、资源 / preference 映射、失败语义。缺少 ROM 或设备证据的高风险变更保持 HOLD。

构建能力参考 A14 `9feacdc6736de98232958ea24b8f722cfb3eff85`：
采用其 AGP / Gradle 版本与内置 Kotlin 配置思路，复用适合 A13 的 DexSQL 查询。
API 102 工程迁移参考 A14 `cb89042b0ff9e7ce1e9a8efe31ee696fa0694e06`：
原生安装策略、Service 的应用级所有者、真实提交结果和作用域快照。
分类为 `UPSTREAM_INTENT_EQUIVALENT`；不新增 ROM 功能，不导入其 SystemUI / 插件 ABI。
不保留 API 101 路径，不启用热重载；既有 Hook 的参数、时序和 `chain.proceed()` 次数保持。

## 当前证据

2026-10-24 集中维护以 A14 `eae2529d1af5e513c8a58c8ce1d198d371e63516` 作页面交互参考，
没有新增 A14 ROM 功能或导入其页面生成器。旧精修分支的 H03-H07、振幅退役、Dock 取消语义、
安全中心温度与全量页面/搜索变更的分类和证据见 [维护记录](A13_CORE_MAINTENANCE.md)。

当前 ROM 修正见 [A13 样本](rom-intelligence/A13_SAMPLES.md)：来电事件过滤、通知渠道设置均为
`A13_COMPAT_VARIANT`，保留上游意图；A14 仅参考实现方法，不复用其 ROM ABI。

2026-10-24 RC 定向精校继续核对 MonwF `dfd3a4de5201d058357c692250e35b1cb74edfb6`、
A13 初始 `c696d33` 及合并后的 `d017f4f`。滑杆值独立于回收行、Dock assistant 按手势与 controller
更新区域，均为 `UPSTREAM_INTENT_EQUIVALENT`。扩展电源菜单的完整资源复制、完整性检查、
终端失败及原生插件保护为 `A13_COMPAT_VARIANT`；保留 A13 MAML 路径，不移植当前上游/A14
的 GlobalActionsDialogLite 方案，也不提升其 RETIRE_CANDIDATE 状态。
本轮没有新增 A14 功能。静态样本、失败边界及未取得的设备证据见 [RC 记录](A13_RC.md)。
RC 最后只读参考 A14 `3e0709f3234ed942d906487370547ec0136c7531` 的相关文件；未整文件移植。

- [A13 进程矩阵](rom-intelligence/A13_PROCESS_MATRIX.md)：目标路由与阶段。
- [构建说明](ENGINEERING_UPGRADE.md)：工具链、验证和签名边界。

阶段性审计表、批量生成器与快照可从 Git 历史查阅，不再作为当前维护门禁。
证据等级以 [COMPATIBILITY](../COMPATIBILITY.md) 为准；A14 设备结果不能提升 A13 结论。
