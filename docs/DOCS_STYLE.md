# A13 文档维护规范

先核对当前源码、资源与构建配置。文档记录用户操作、工程约定、可复核的证据以及待验证事项。开发过程由 Git 保留。

## 事实来源

| 内容 | 来源 |
| --- | --- |
| 用户名称、安装与反馈 | [中文 README](../README.md) 与 [英文 README](../README_EN.md)。应用标签统一从 [默认资源](../app/src/main/res/values/strings.xml) 和 zh-CN、zh-TW 的 `app_name` 读取。 |
| 设置入口、名称和可见条件 | [Preference XML](../app/src/main/res/xml)、[字符串资源](../app/src/main/res/values/strings.xml)、`SettingsPages` 和实际控制器。选项名变更时核对菜单、搜索和 About。 |
| 进程、Hook 和所有权 | [架构](../ARCHITECTURE.md)、当前 Installer 和 Feature Catalog。[进程矩阵](rom-intelligence/A13_PROCESS_MATRIX.md)用于定位。 |
| 平台、ROM 与移植 | [兼容矩阵](../COMPATIBILITY.md)、[ROM 样本](rom-intelligence/A13_SAMPLES.md)、[A13 与 A14 边界](A13_PARITY.md)。 |
| 工程规则与操作 | [AGENTS](../AGENTS.md)、[项目 Skills](../.agents/skills/README.md)、[开发与构建](DEVELOPMENT.md)、[验证](TESTING.md)。 |
| 版本、发布与恢复 | [构建配置](../app/build.gradle.kts)定义源码版本，[Releases](https://github.com/thetvplus/customiuizer-a13/releases/latest)定义正式版。变更记录写入双语 CHANGELOG，操作见[发行流程](A13_RELEASE.md)。 |

[文档目录](README.md)负责入口导航。在对应页面更新约定，其他页面直接链接来源。静态或实机结论按[证据等级](../COMPATIBILITY.md#证据等级)标注，并注明适用版本与样本。

## 名称与语言

- 中文用户界面、中文 README 与仓库介绍使用 **米客 A13**。英文及其他语言的默认标签使用 **Customiuizer A13**。引用上游时保留项目原名。
- App 图标标签、设置标题和 About 使用同一个 `app_name`。新文案进入 Android 字符串资源，同步核对简体中文、繁体中文及默认英文。App 内语言可单独选择，桌面标签跟随系统语言。
- 中文 README 与英文 README 保持安装步骤、兼容范围和反馈入口一致。双语 CHANGELOG 对应记录用户可见的改动，等待发布的改动放在未发布节。
- 包名、版本、APK 文件名、签名和其他机器可读标识遵循各自的发布合同。

## 更新与检查

1. 为新增内容选一个权威位置，更新使用入口。如果记录 ROM 证据，标明样本、宿主、证据等级和仍待验证的范围。
2. 移动或删除前，用 `rg` 检查文档、测试、脚本、工作流和资源引用。可追溯的历史指向固定提交，原始日志、ROM 和私有路径留在仓库外。
3. 核对本地相对路径、标题锚点和图片路径。按 [TESTING](TESTING.md#执行分级) 验证实际改动。改了资源或设置页时，验证对应构建和交互。
