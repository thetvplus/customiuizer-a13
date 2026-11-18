# A13 测试与验证

## 执行分级

根据实际失败影响选择范围。受影响的生产调用、数据和门禁决定验证深度。

| 级别 | 选择与完成条件 |
| --- | --- |
| Quick | 文档检查链接、锚点、编码和消费者。叶子工具运行相关 Python 测试。局部 Android 变更用 `fast --changed` 编译，显式选受影响的 JVM 行为测试。 |
| Standard | 跨模块、持久化、配置、生成或验证入口变化，检查相关调用、旧输入、失败路径及下游消费者。定向证据难以界定影响时扩大到全量 JVM 或 Python。 |
| Deep | Hook 基础设施、ClassLoader、共享状态、ROM ABI、R8、资源打包和重要安全边界，检查实际受影响的时序、回退和释放。公共 Android 边界用 `full`。涉及优化器用既有 R8 门禁，涉及资源保留检查实际资源表。ROM 和设备结论依照 [证据等级](../COMPATIBILITY.md#证据等级)。 |
| Release | 遵循 [A13_RELEASE](A13_RELEASE.md) 的精确 main Fast / Full、签名、来源与晋升门禁。 |

```powershell
python tools/verify.py fast --changed --base origin/main
python tools/verify.py fast --tests PowerMenuResourceTest
python -m unittest discover -s tools/tests -p "test_verify.py"
python tools/verify.py full
git diff --check
```

本地 `--changed` 显式基线用于依赖 PR 或任务固定起点，省略时用 `origin/main`。选择器比较基线与 HEAD 的共同祖先，合并已提交、暂存、未暂存和未忽略的新文件。仅全为根目录、`docs/`、`.agents/skills/` 或 `tools/dexsql/` 中的 Markdown 时跳过 Android。无法比较、空差异或其他文件类型保留 Android 检查。显式 `fast --tests` 始终运行选定测试。只改 Python 工具时可直接运行对应 unittest 和消费者。

`fast` 默认执行静态门禁及 Kotlin / Java 编译，指定测试时由 Gradle 测试依赖触发编译。`full` 在一次 Gradle 调用中执行 debug JVM 和 lint。新增或修改 Python 工具时检查对应测试与语法。两个模式都执行编码、架构、兼容、Hook 合同和设置覆盖检查；默认不构建 APK、不签名、不连接设备。

## 测试边界

- 优先断言真实行为、资源输入、回退、取消和所有者释放。静态门禁用于宿主不可直接执行的 ABI、打包输入或热路径约束。删除重复检查前保留其独立断言。测试数量、源码匹配和 Host JVM 耗时各自有适用范围。
- #19 的 Python 检查核对两行信号的等级、样式、颜色资源及 shrinker keep 规则。原生信号还原和最终资源表需补验证。#14 的 `PowerMenuResourceTest` 覆盖短读、资源损坏、安装失败与失效，`AudioVisualizerLifecycleTest` 覆盖显示条件和迟到结果，`SecurityCenterB2BCorrectiveTest` 覆盖目标缺失隔离与普通/致命失败。各自的 ROM 缺口见 [样本记录](rom-intelligence/A13_SAMPLES.md#双排信号与-issue-14)。
- 外部 ROM 的两个集成测试默认跳过。提供对应的 veux `super.img`、解包依赖和足够临时空间时，用 `A13_VEUX_SUPER_IMG` 指向样本后定向运行 `test_rom_intelligence.py`。缺少仓库内必需的源码、工具或测试输入应直接失败。

## 关于页面的验收场景

当应用名称、语言选项或关于页面改动时，在 Android 13 的设置页核对以下场景：

1. 分别使用简体中文、繁体中文和英文，打开关于页面，检查应用名称、版本、来源归属和使用说明。系统语言一致时核对桌面标签；仅通过 App 内菜单切换语言时核对 App 内标题。
2. 从横屏进入关于页面，再切换方向。检查图标、文字和列表布局，并在较大字体设置下滚动到最后一条说明。
3. 依次打开源代码、正式版本和 PayPal，核对目标。无可处理链接的应用时应看到提示。返回关于页面后，语言选项仍可用。

## 证据复用与 CI

复用已核实的同输入、环境和范围结果。通过记录至少能定位输入、命令和结果。提交本身不改变输入。新增差异、失败修复、相关环境变化和已知缺口才补验；正式 CI 仍按精确 main SHA。完整审阅 diff、检查受影响合同和 `git diff --check` 后收口，记录各证据的范围及待验。

- [Fast CI](../.github/workflows/a13-fast-ci.yml) 对 main 和任务分支的 PR 检查实际合并树，以 PR base SHA 选择影响。纯文档 PR 保留 Python 和静态门禁，跳过 Android 环境、Gradle 及正式构建图。其他 PR、main push 和手动 Fast 运行 Full 本地验证器与独立正式构建图。
- [Full CI](../.github/workflows/a13-full-ci.yml) 在 main push、发行 tag、定时或手动触发时独立验证，补正式元数据和 R8。签名预检要求独立的 Fast、Full 和成功步骤回执。
