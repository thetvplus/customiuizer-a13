# A13 开发与构建

| 工具 | 当前配置 |
| --- | --- |
| Gradle / AGP | 9.9.1 / 9.5.0 |
| CI JDK / Java target | Temurin 25.0.5 LTS / 25 |
| Kotlin compiler / language / API / JVM target | 2.4.21 / 2.4 / 2.4 / 25 |
| Android compile / min / target SDK | 37 / 33 / 34 |
| SDK Build Tools | 36.0.0 |
| libxposed API / service | 102.0.0 / 102.0.0 |

AGP 管理内置 Kotlin 集成。根构建脚本显式提供 Kotlin 编译器 2.4.21，以保留 JVM 25 支持；
不再应用 `org.jetbrains.kotlin.android`，不使用 `android.builtInKotlin=false` 或
`android.newDsl=false`。Kotlin runtime 仍由版本目录与 BOM 管理。

本地使用仓库 Gradle Wrapper 与 JDK 25.0.5 LTS，将 `ANDROID_HOME` 指向含所需 SDK 的目录。
构建配置、版本目录和 [CI 安装脚本](../tools/ci_install_android_sdk.sh) 是版本与 SDK 包标识的依据。
检查选择、定向命令和复用条件见 [TESTING](TESTING.md)。

Hook API 为 `compileOnly`，Service 为设置端运行依赖。产品支持范围见 [COMPATIBILITY](../COMPATIBILITY.md)。
`compileSdk` 决定编译可见的 API。`minSdk=33` 保留 Android 13 安装能力，`targetSdk=34` 与发行元数据及兼容合同一同维护。

构建脚本的 classpath 和 `androidLintTool` 分别约束宿主依赖版本。应用依赖由版本目录和应用构建脚本管理。
升级依赖后为实际解析的构件更新 `gradle/verification-metadata.xml`，包含 Windows 与 Linux 使用的 AAPT2。
正常门禁使用严格校验，Wrapper 使用官方分发与固定 SHA-256。
配置缓存与构建缓存保持启用，不要求反复 clean。

普通开发与 CI 使用无正式签名的任务。正式流程及恢复见 [A13_RELEASE](A13_RELEASE.md)。

版本说明见 [AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)、
[内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、
[Gradle 9.9.1](https://docs.gradle.org/9.9.1/release-notes.html) 和
[Temurin 25](https://adoptium.net/temurin/releases/?package=jdk&version=25)。

## Git 与 Actions 维护

`main` 是唯一长期稳定线。任务从已核对的完整 SHA 创建 `devin/a13-*` 分支，通过 PR 交付。按 [AGENTS](../AGENTS.md) 核对合并授权；GitHub 会在合并后自动删除同仓 PR 的头分支。

手工清理前列出本地工作树、远端分支的完整 SHA、开放 PR 和保护规则。已合并的分支可退役；Squash 合并以已合并 PR 的头、合并 SHA 和后续主线实现为证据。旧分支有未整合成果、被使用或归属不明时保留。退役分支有主线无法直接到达的历史时，先记录 ref / SHA，并在仓库外创建和验证可恢复的备份。删除前核对 ref 仍指向已审定的 SHA；读回远端和本地状态。工作树有未知修改或仍在使用时退出清理。

整理 Actions 只删除已完成且有替代证据的记录，例如已合并任务的迭代 CI、同 SHA 重复定时检查、已被成功发布取代的跳过记录。留存：

- 当前 `main` 的 Fast / Full，以及具备发行或恢复用途的精确 SHA 的构建、测试和 R8 证据；
- 发布页和元数据引用的 Signed Build、原始 attempt、未过期签名 Artifact、对应 Fast / Full 和 Stable Release；
- 最新定时验证、仍在运行的任务、开放 PR 检查和未处置故障的代表性记录。

先按 Run ID 盘点，与发布记录、Artifact 及任务引用对照，再复核要删除的项。GitHub 删除运行时也删除其日志和 Artifact；元数据清单仅用于追溯。清理后读回保留项和删除结果。签名与恢复参见 [A13_RELEASE](A13_RELEASE.md#幂等与失败恢复)。
