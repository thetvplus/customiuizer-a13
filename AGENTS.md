# AGENTS.md — 米客 A13｜CustoMIUIzer A13

适用于本仓库所有 Agent。当前用户要求和已授权的任务合同优先。用现行源码、调用链和 ROM 证据确认行为，历史报告仅作线索。

## 产品边界

- Android 13 / API 33，主支持 MIUI 14，HyperOS 1 / Android 13 实验兼容。Android 14+ 和灵动额头不进入 A13。
- `tv.withaibuild.customiuizer.r13`、`arm64-v8a`。当前 libxposed min / target API 均为 102，以构建配置和模块元数据为准。
- A13 与 A14 独立维护生产分支、APK、签名和运行时库。默认保留 Java 入口及现有 libxposed 架构。

## 工程判断

**Judgment over Procedure**。先确定触发、实际失败与完成条件。沿调用链做能区分根因的最小检查，只有证据缺口才扩大检索。已确认的来源和同一版本无需反复核对。附加测试应能捕获真实回归。

**Performance-First**。在正确性、兼容性、生命周期和宿主稳定有保障时，优先减少关键路径和持续运行的工作，尤其是反射、分配、Binder、绘制、唤醒和长期持有。复用原生能力和已有状态。保留 PrefMap、ResourceHooks、ProcessRouter、PreferenceBootstrap 的职责，避免引入框架或大规模语言迁移。

- **提前准备**。所属进程的实际 ClassLoader 和依赖就绪后解析稳定成员，避开首帧阻塞与全量预载。热路径使用准备好的有界状态。
- **按需激活**。功能关闭时不安装业务 Hook、监听或任务。按进程和 loader 单次安装，偏好变动不重置安装状态。所有者负责替换、失效和释放，避免静态强持有短生命周期对象。部分安装失败必须有界。
- **增量执行**。相关输入变化时才更新状态、资源和布局；保留原方法调用次数、回调时序、参数和异常语义。沿用既有回调安全边界，传播致命 JVM 错误及其包装原因。原生 Hook ID 仍限已有基础设施，不启用热重载或 Hook 替换。

## 上游与 ROM

已有功能改语义前按相关范围查 MonwF 上游、A13 历史和当前实现，再参考 A14。功能分类与差异记录在 [A13_PARITY](docs/A13_PARITY.md)。进入 A13 的功能需要独立的 Android 13 证据。

宽发现、严绑定、按能力兼容。核对目标的完整签名、调用关系、实际 ClassLoader、生命周期、资源、偏好及失败语义。候选无法确认时沿用原生行为。删除或改名前核对 Manifest、R8、反射、DexKit、资源和旧偏好。DexSQL 复用 [现有查询](tools/dexsql/README.md)，仅用于按需离线取证；残缺 DEX 的零结果不能作为目标缺失的证据。

分别记录 `STATIC_VERIFIED` 与 `BUILD_VERIFIED`。只有相应的 A13 日志支持 `LOG_VERIFIED`，A13 实机直接验收支持 `DEVICE_VERIFIED`；未测行为和性能保持待验证。完整定义见 [COMPATIBILITY](COMPATIBILITY.md)。

## 验证与协作

- 按影响选测。开发中用 `python tools/verify.py fast --changed`，定向测试加 `--tests <TestClassName>`。最终代码一次 `python tools/verify.py full`，另运行 `python -m compileall tools`、`python -m unittest discover -s tools/tests -p "test_*.py"` 与 `git diff --check`。资源压缩、优化器或 Hook API 改动按影响核对 R8 和实际产物。纯文档改动走相关轻量检查。保留有效门禁和行为断言。
- 从确定基线建 `devin/a13-*` 工作分支，保护未知修改。开发授权包含 commit、push、PR 和 Fast / Full CI 跟进。仅在用户要求时合并 `main`。禁止 force push、破坏性 reset、改写公开历史和无差别 clean。只读、本地保留和用户指定的停止点优先。交付记录 Base SHA、Final SHA、提交、验证与待验项。
- 正式版本、签名、tag 和发布遵循 [A13_RELEASE](docs/A13_RELEASE.md) 与现有受保护流水线。普通验证使用无正式签名的构建。修改正式版本须经授权，版本名、单调递增的 versionCode 和 CHANGELOG 同步。APK、签名材料、本地配置、ROM、DEX、trace、mapping 和原始日志留在仓库外。
