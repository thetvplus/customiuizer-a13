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

## 已采用的参考

| 范围 | 分类与 A13 结论 |
| --- | --- |
| 构建与 API 102 安装 | 工程参考 [A14 工具链](https://github.com/thetvplus/customiuizer-a14/tree/9feacdc6736de98232958ea24b8f722cfb3eff85) 与 [API 102 迁移](https://github.com/thetvplus/customiuizer-a14/tree/cb89042b0ff9e7ce1e9a8efe31ee696fa0694e06)。A13 入口、作用域与回调合同见 [架构](../ARCHITECTURE.md)。 |
| 系统与应用 Hook | 来电过滤、通知渠道、熄屏动画、Toast、焦点及安全中心电池温度为 `A13_COMPAT_VARIANT`。按应用振动、即时音量、Dock 取消及滑杆保持 `UPSTREAM_INTENT_EQUIVALENT`。振幅 / 定时调整为 `DEAD_UPSTREAM_PATH`，限两份 services 样本，旧备份仍兼容。 |
| 状态栏高度 | `A13_COMPAT_VARIANT`。参考 [A14 状态栏高度](https://github.com/thetvplus/customiuizer-a14/tree/06ba61e7040b4f275af9c678c82fa62793acaadf/app/src/main/java/tv/withaibuild/customiuizer/mods/statusbarheight) 对齐可见栏与系统窗口几何的行为。A13 以自己的 DisplayPolicy ABI 更新系统窗口配置，复用原生实时 Insets 链。保留 19 默认值和 20–80 dp，重启设备应用。[ROM 证据](rom-intelligence/A13_SAMPLES.md#状态栏窗口与系统高度) |
| 设置页 | 参考 [A14 交互](https://github.com/thetvplus/customiuizer-a14/tree/eae2529d1af5e513c8a58c8ce1d198d371e63516)，保留 A13 XML preference 和现有设置模型。 |
| 电源菜单与双排信号 | `A13_COMPAT_VARIANT`。保留 A13 MAML 和目标 SystemUI 视图链。样本与未确认的目标见 [ROM 记录](rom-intelligence/A13_SAMPLES.md)。 |
| 工程治理 | 参考 [A14 当时主线](https://github.com/thetvplus/customiuizer-a14/tree/87118e5d76833ae43a239b5832b0194ec014649f) 的工程判断、只读 Review 和按影响选测。运行时与正式发行边界由 A13 维护。 |

旧的逐阶段论证可追溯至固定提交的 [核心维护](https://github.com/thetvplus/customiuizer-a13/blob/78fe48b3ea1f7fa8637215a26025ff464ad25708/docs/A13_CORE_MAINTENANCE.md) 和 [RC 记录](https://github.com/thetvplus/customiuizer-a13/blob/78fe48b3ea1f7fa8637215a26025ff464ad25708/docs/A13_RC.md)。
