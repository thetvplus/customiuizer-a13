# AGENTS.md — 米客 A13｜CustoMIUIzer A13

适用于本仓库所有 Agent。用户当前明确要求优先；源码和本轮证据高于历史报告。

## 产品边界

- Android 13 / API 33；主支持 MIUI 14，HyperOS 1 / Android 13 实验兼容。
- Android 14+ 和灵动额头 / Dynamic Island 不进入 A13。
- applicationId：`tv.withaibuild.customiuizer.r13`；ABI：`arm64-v8a`。
- 纯 libxposed API 102：API / Service 102.0.0，min / target API 均为 102。
- A13 / A14 不共享生产分支、APK或运行时库。
- 签名以各自发布配置为准；更换发布证书须用户明确授权，并说明升级影响。

## 实现原则

正确性 > 兼容性 > 稳定性 > 生命周期 > 可维护性 > 内存与性能 > 结构整洁。
少状态、少层、少重复、少后台行为。为实际问题服务，不为抽象或行数重构。

- 不整体重做 PrefMap、ResourceHooks、ProcessRouter、PreferenceBootstrap。
- 不引入 Compose、新 DI 或大型框架；不把整个 Java 树 Kotlin 化。
- 默认保留 Java 的 MainModule、XposedHelpers、MemberUtilsX。
- 无关进程不初始化功能；关闭功能不创建业务 Hook、监听或任务。
- 每进程 / ClassLoader / Feature 只安装一次，偏好变化不重置安装状态。
- 部分安装失败进入终端状态；不重试无法证明无副作用的安装。
- 保持参数改写、回调时序、原方法调用次数与普通异常恢复语义。
- 致命 JVM 错误及其包装原因必须传播，不可用 catch 掩盖根因。
- Receiver / Observer / listener / controller 有所有者和替换、失效、释放闭环。
- 不静态强持有 Activity、View 或短生命周期 controller；缓存按 ClassLoader 隔离且有界。
- 热路径不做磁盘 I/O、同步 Binder、DexKit、重复反射、Regex 重建或日志洪泛。
- 原生 Hook ID 只用于已定义的基础设施；不启用热重载、replaceHook 或 detach。
- 设置进程可使用 Service SDK，不得依赖 compileOnly Hook API 的加载。

## A14 与 ROM

A14 参考产品意图和工程方法，不是整文件移植来源。已有功能先核对 MonwF 当前上游、
A13 历史与当前实现，再看 A14。变更分类及必要差异记录在 `docs/A13_PARITY.md`。

确认 Android 13 API、目标成员完整签名、进程 / ClassLoader、生命周期、资源 / preference
和失败语义。不能拼接两个 ROM 的目标成员。缺少证据的高风险 ROM 热路径保持成熟实现。
删除或改名先检查 Manifest、R8、反射、DexKit、资源和旧 preference key。

ROM 与构建证据分别记录：STATIC_VERIFIED / BUILD_VERIFIED。A13 日志支持才算
LOG_VERIFIED，A13 实机直接验收才算 DEVICE_VERIFIED；A14 设备和 ROM 样本不能替代。
完整定义见 `COMPATIBILITY.md`。ROM、trace、mapping、profiler 数据留在仓库外。
DexSQL 只作按需开发工具，不增加 APK 或默认 CI 依赖。

## 验证与 Git

开始核对工作区和远端，保护未知修改。Agent 分支使用 `devin/a13-*`，从 exact SHA 创建。
不 force push、不破坏性 reset、不改写公开历史、不无差别 clean。
接受开发任务后，默认自动完成该任务所需的修改、验证、commit、Agent 分支 push、PR 创建/更新和
Fast / Full CI 的触发、跟进及失败修复，无需逐项向用户重复申请授权。工程检查由 Agent 自行执行、
核对真实结果并收口，不把例行核对转交用户。
用户要求合并 main 时，在 PR 和必要 CI 通过后自主完成，不再追加确认；未要求合并时保持专项分支。
当前任务明确的只读、本地保留或停止点优先。正式签名、tag 和公开 release 仍遵循当前发行任务合同
及既有受保护流水线；自动化不得跳过失败门禁或把未执行的检查写成通过。

开发中按改动运行 `python tools/verify.py fast --changed`，必要时加 `--tests <TestClassName>`。
收口一次运行 `python tools/verify.py full`、`python -m compileall tools`、
`python -m unittest discover -s tools/tests -p "test_*.py"`、`git diff --check`。
改动优化器或 Hook API 时另做 R8 验证，不重复 clean，不通过删测试、吞异常或关检查造通过。
文档任务不编译 Android。最终报告 Base SHA、Final SHA、验证范围和遗留风险。

正式发布只在明确授权时使用仓库外 A13 签名；普通验证不加载正式签名。
不得提交 APK、keystore、密码或本地配置。Fast / Full CI 不使用正式 keystore；
只有 main 精确 SHA、完整 CI 与 release-signing 审批通过的独立 A13 Signed Build 可临时加载正式签名。
公开发布另经 release-publishing 审批和版本确认，恢复发布复用原始 Artifact。详见 `docs/A13_RELEASE.md`。
不主动递增版本或伪造发行记录；如任务授权改版本，版本名、code、CHANGELOG 必须一致。
