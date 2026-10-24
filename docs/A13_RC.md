# r13.14.1 封版与交接

日期：2026-10-24。正式目标：`r13.14.1` / versionCode `142`，`r13.13.0 → r13.14.1`。
`r13.14.0` 仅为此前未公开发布的开发版本，不是正式发行历史。
Base main：`d017f4fbdeb23c40a446ee64444726be0e2d4030`（PR #15）。
本轮起点：`6f08c4738ab5623269515cfccee7fdc856c2f4e1`（PR #16 原有四个提交）。
分支：`devin/a13-rc-polish-20261024`；本轮最多追加两个提交，继续更新同一 PR #16。

用户已明确授权在本轮必要门禁通过后 Squash merge main，签名并发布本版 Stable Release 到
`thetvplus/customiuizer-a13` 和 `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r13`。
两个 GitHub Environment 的原生人工审批继续保留。实际 final main SHA、CI/签名/晋升 Run ID、
Artifact ID、APK hash、Release URL 和双仓读回结果记录在最终交接回执；不预填成功。

## 实际修复与定向复核

| 范围 | 最小修复与边界 |
| --- | --- |
| 扩展电源菜单 | RC 已完成完整复制、ZIP/CRC/manifest 验证、一次安装及终端失败；本轮补上 GlobalActions 插件抑制前的资源可用性检查。资源改放 SystemUI 私有 filesDir，避免原生插件抑制后被系统清理缓存；失效时不重新安装或轮询。保持 RETIRE_CANDIDATE，不增加 ROM 兼容层 |
| SeekBar | RC 已将数值与回收行分离，并弱持有/校验 View 所有者；本轮比较 SharedPreferences 内存快照，使外部写入、恢复替换/清空失效旧值。保留尚未保存的程序设值，保存完成或 writer 失败后不继续假冒已存配置。min/max/step 先捕获旧值再更新范围，非法步长与构造期一致归一为 1 |
| 单元测试 | 保留 PreferenceCardDecorationTest、SettingsNavigationGuardTest、SeekBarPreferenceStateTest 的普通 JVM 路径。它们依赖 Unsafe 和 AndroidX 私有字段，升级依赖时需复核；当前没有 Robolectric，引入它会扩大依赖和运行环境，不为测试形式改造生产代码 |
| 已有 RC 修复 | Dock assistant 区域按 controller/手势刷新；设置 decoration 缓存、observer 释放、导航拒绝、异常备份和回滚继续复用原回归，没有重复开发 |
| 版本与发布合同 | 统一 versionName、双语日志/README、操作文档和合同 fixture 为 r13.14.1，保留未发行的 code 142。发布脚本从精确源码读取版本，policy 的 r13.13.0 证书/文件基线保持不变 |

历史 preference key、默认值、保存/备份格式、包名、支持范围和证书不变。
最终限定复核未保留已证明且未修复的 P0/P1。静态/构建结论不能提升为设备验收。

## 行为、生命周期与测试边界

- 未创建 View、连续程序设值/保存、Preference 重建、外部写入、恢复替换与清空、动态范围/步长、异常/缺失 writer 由生产值路径 JVM 测试覆盖。
- View 回收继续以弱引用和行 tag 隔离；同一 adapter 缓存失效、动态隐藏/过滤、adapter 替换及 observer 释放由既有 JVM 回归覆盖。
- Activity/Fragment 重建的配置读取得到新实例验证；真实 Android View 渲染、旋转、转场、滚动/高亮仍需要 A13 设备。
- 电源资源缺失、损坏、短读、安装失败、删除/截空及致命异常保持本地回归；ROM 插件时序、真实电源菜单与 SystemUI 重建仍需日志/设备证据。
- 没有新增 Receiver、Observer、后台线程、任务、轮询、全局 View/Activity 引用或无界缓存。滑杆只读 SharedPreferences 内存快照；文件复制与可用性检查仅在初始化、GlobalActions 插件激活和资源加载冷路径。
- Dock MOVE 不增加反射或区域计算；Hook 时序、参数、原始调用次数和命令失败恢复语义保持。
- 没有 CPU、内存、功耗或流畅度实测，不宣称性能百分比收益。

已有上游与样本核对范围保留在 [A13_PARITY](A13_PARITY.md)：MonwF 当前 SHA
`dfd3a4de5201d058357c692250e35b1cb74edfb6`、A13 初始 `c696d33`、Base main 和本轮实现，
最后只读参考 A14 `3e0709f3234ed942d906487370547ec0136c7531`；不移植 A14 Hook。
滑杆为 UPSTREAM_INTENT_EQUIVALENT；电源资源/原生回退为 A13_COMPAT_VARIANT。

历史 RC 静态样本为 xaga MIUI 14 / Android 13 Home 与 SystemUI，包含 assistant、
ZipResourceLoader(String)、issueExternCommand(String, Double, String)、PluginEnablerImpl.isEnabled(ComponentName)。
Home SHA-256：`4a777f4e6bad31f892a45c6565f04159db503a1177e8930591d7c1223d52eed8`；
SystemUI SHA-256：`c0d9bf1d5b985ca84846cd9e597b2ed694289890c227d8c15100b6168d4eed98`。
这些历史样本定义不是通用 ROM 支持或新设备验收。

## 工程门禁

历史 6f08c47：本地 Fast/Full、155 类 1345 项 JVM（1 skipped）、938 项 Python（4 skipped）、
lint 0 error / 696 warnings、无密钥 R8 通过；远端
[Fast 40052818084](https://github.com/thetvplus/customiuizer-a13/actions/runs/40052818084)、
[Full 40052824015](https://github.com/thetvplus/customiuizer-a13/actions/runs/40052824015) 均 success。
这些结果不代替本轮最终源码和最终 main 的检查。

本轮最后修复的 Fast、四类定向 JVM 与最终本地 Full 均通过：155 类、1350 项 JVM（0 failures/errors、1 既有 skipped）；lint 0 error / 696 warnings。
Python 938 项通过（4 既有 skipped），compileall、UTF-8、源码危险模式、Hook 所有权、CI 可移植性、4 工作流合同、发行元数据和 diff-check 均通过。
工作流显示名称按维护者要求改为 A13 Stable Release；42 项发行合同回归在改名后再次通过。
最终分支需 Fast/Full；合并后的完整 main SHA 必须另取得原生 Fast/Full success，不能使用分支结果代替。
Signed Build 对 main、workflow、attempt、job 和必要步骤再次校验；没有签名/来源验证不能声称 SIGNED_VERIFIED。

STATIC_VERIFIED = YES（限源码、纯逻辑回归和已注明的历史样本）。
BUILD_VERIFIED = YES（本地 Full 编译、JVM 与 lint；不代表 APK 或设备验收）。
LOG_VERIFIED / DEVICE_VERIFIED 未取得；本轮 adb devices 读回无设备。

## 远端保护与发行交接

封版前读回两个 Environment：reviewer `thetvplus`、禁止管理员绕过，仅允许 branch 类型精确 main。
release-signing 四个 Secret 名称齐全；首次复核 release-publishing Secret 列表为空，发布前须再次核对
A13_XPOSED_RELEASE_TOKEN。没有读取密钥明文，存在名称不能证明密钥/权限有效。

已经核对管理权限、Squash 能力、GitHub Actions app ID 15368 的 fast 来源及基线包含关系，
应用并读回 [main safety Ruleset](https://github.com/thetvplus/customiuizer-a13/rules/25766547) 为 Active。
规则无 bypass，禁止删除/force push、必须 PR 且 Squash、严格要求 fast；不把 main-only Full 设为 PR 必需项。
规则只约束 main，不影响发行 tag 和独立 Environment 审批。

发行映射：源码 tag `r13.14.1`，Xposed tag `142-r13.14.1`，标题均为 `r13.14.1`，
APK 均为 `CustoMIUIzer-A13-r13.14.1.apk`。公开只附 APK 与 build-metadata.json；
原始 Artifact 保留 APK、SHA256SUMS、build-metadata.json、双语 release-notes.md、attestation.sigstore.json。
同一候选只正式签名一次；部分成功保留已发布仓库，使用原始 Build Run ID/Artifact 恢复，不重签或覆盖 tag。

签名必须延续 r13.13.0 证书 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`；
旧 APK 的文件 hash 为 `b064712965098b9013a27dc448478afdf208c150aea00654960834b87a5dc47c`，两者不能混用。

详细执行和故障恢复见 [A13_RELEASE](A13_RELEASE.md)。最终回执分别记录 CODE_COMPLETE、CI_VERIFIED、
SIGNED_VERIFIED、SOURCE_RELEASED、XPOSED_RELEASED、DUAL_RELEASE_VERIFIED、DEVICE_VERIFIED。
成功双仓发行后停止本阶段集中开发，进入稳定维护，不追加新一轮精修或版本。

## 尚未完成

本轮未取得 A13 实机或日志证据。SecurityCenter 11.1.6 仍缺实际目标样本；Issue #14 的外部兼容问题
不宣称全部解决。锁屏音乐可视化、扩展电源菜单维持 RETIRE_CANDIDATE；Issue #12 的既有备份修复
仅做回归，不因此自行关闭历史 Issue。Android UI/Launcher 动画、真实音频/熄屏、电源菜单操作、
SystemUI 重建和用户切换仍需要相关 A13 环境验证。
