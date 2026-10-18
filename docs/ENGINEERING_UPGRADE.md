# A13 工程现代化升级台账

更新日期：2026-10-18。按小批次记录；Android SDK 许可/组件可用后再完成 Android 编译门禁。

## 基线与本地工作保护

- 可追踪工作分支：`devin/a13-r13.13.0`，Base SHA `8d46dc29851d70ef5e67071ba48db7c43ff20b15`。纳入 Issue #12 的备份键修复、Java 25 输出升级与对应工具链更新。
- 共享源码目录不是 Git checkout；已确认关键配置、治理文档和 Issue 文件与分支副本换行归一后内容一致。分支副本作为差异审查基准，完成后同步镜像到共享目录。
- A14 只读参考：`codex/engineering-hardening-20261018`，本地 SHA `7d0e88bb3a9bfd537f9116387e0eb751d2122005`。独立 A14 互操作工作树未改动。
- 当前用户已授权推送新分支、发布 r13.13.0、合并并关闭开放 issues。保留 GitHub Actions；版本交付采用一个提交。发布构建仍被 Android SDK 许可阻塞。

## 工具链矩阵

| 项目 | A13 当前配置 | A14 本地参考 | 决策 / 证据 |
|---|---|---|---|
| Gradle / AGP | 9.7.0 / 9.3.1 | 9.8.1 / 9.4.1 | Gradle 9.7.0 可在 JDK 25 上运行；AGP 9.3 最低要求 Gradle 9.5。选用 Kotlin 2.4 系列官方完整兼容矩阵覆盖的 Gradle 9.7 / AGP 9.3.1 组合。 |
| 构建 / Daemon JDK | JDK、daemon、Java toolchain 均为 25 | 同为 25 | 保留 JDK 25。 |
| Java / Kotlin 输出 | Java source/target 25；Kotlin JVM target 25；语言/API 2.4 | 同为 25；语言/API 2.4 | Gradle 配置探针读到 Java source/target=25、Kotlin JVM_25、language/API=2.4。此项证明配置值，不等于 Android 编译或 APK 验证。 |
| Android SDK | compile 36、min 33、target 34、Build Tools 36.0.0 | compile 37、min/target 34、Build Tools 36.0.0 | 保留 A13 Android 13 产品边界和 API 配置。 |
| Kotlin 集成 | 独立 Kotlin Android Gradle Plugin 2.4.21；Kotlin BOM/stdlib 2.4.21 | AGP 内置 Kotlin；BOM/stdlib 2.4.21 | A13 AGP 内置编译器的 `JvmTarget` API 不含 JVM_25。为输出 25，关闭内置 Kotlin并使用独立插件；AGP 9 的 `android.builtInKotlin=false`、`android.newDsl=false` 与独立插件产生弃用警告，未来切换 AGP 10 前需迁移到内置插件。 |
| D8 / R8 | 随 AGP 9.3.1 提供 | 随 AGP 9.4.1 提供；另有 AGP 内部资源任务 | 不引入 A14 内部 AGP API；D8/R8 实际接收 Java 25 class 文件仍待 Android 编译门禁验证。 |
| libxposed | API 101.0.1 / service 101.0.0 | API/service 102.0.0 | 保留 A13 API 101 必经兼容边界。 |
| 直接依赖 | Kotlin BOM 2.4.21；Commons Lang 3.20.0、Preference 1.2.1、Palette 1.0.0、AppCompat 1.7.1、JUnit 4.13.2 | Kotlin BOM/stdlib 2.4.21；Commons Lang 3.21.0、AppCompat 1.8.0，另有 DexKit、Coroutines、Fragment、Robolectric | 只升级 Java/Kotlin 输出所需 Kotlin 编译插件与 BOM；其他应用依赖维持 A13 版本。 |
| CI / SDK 包 | JDK 25、Python 3.12、Actions SHA 固定；compileSdk 36、Build Tools 36.0.0 | JDK 25、Python 3.12；compileSdk 37、Build Tools 36.0.0 | SDK 安装脚本固定 stable channel 和 Build Tools 36.0.0，不再另装动态 `cmdline-tools;latest`。 |

官方资料：[AGP / Gradle 版本要求](https://developer.android.com/build/releases/about-agp)、[Gradle Java 兼容矩阵](https://docs.gradle.org/current/userguide/compatibility.html)、[Kotlin Gradle 兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html)、[Kotlin 2.4 发布记录](https://kotlinlang.org/docs/releases.html)、[AGP 内置 Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Kotlin compilerOptions](https://kotlinlang.org/docs/gradle-compiler-options.html)、[固定版本的 setup-android Action](https://github.com/android-actions/setup-android/blob/40fd30fb8d7440372e1316f5d1809ec01dcd3699/README.md)。Kotlin 文档的完整兼容表列到 KGP 2.4.20（Gradle 9.7 / AGP 9.3.1）；2.4.21 是同一 2.4 系列的 bug-fix patch，因此沿用该兼容范围是版本连续性推断，仍以本地 Gradle 配置和后续编译验证为准。

## 本批改动

1. Java source/target 升到 25；Kotlin 显式固定 language/API 2.4、JVM target 25，并通过 BOM 将 Kotlin stdlib 对齐到 2.4.21。
2. 版本目录加入 Kotlin Android 插件 2.4.21；根插件解析顺序先加载 Kotlin 再加载 AGP。由于 AGP 内置 Kotlin 版本不支持 JVM_25，配置使用 AGP 9 的临时退出开关，保留现有 Android DSL。
3. Gradle Wrapper 升到 9.7.0，包含 SHA-256 校验和、对应 wrapper JAR/脚本；AGP 保持 9.3.1。Build Tools 固定 36.0.0，SDK 安装脚本同步。
4. 修正 `ARCHITECTURE.md` 中过时的 `targetSdk=33` 记录；不改变 minSdk、targetSdk、ABI、libxposed API 或 ROM 支持边界。

## 依赖校验、CI 与结构观察

- Gradle dependency verification 保持 metadata 校验开启、签名校验关闭；metadata 从 559 个组件增至 599 个，新增 Kotlin 2.4.21 编译/插件及 BOM artifacts，并记录 SHA-1/SHA-256。
- 在线 `debugRuntimeClasspath` 解析选中 Kotlin stdlib 2.4.21，无 `FAILED`。其他直接运行时依赖未仅因版本较旧而升级。
- `org.gradle.configuration-cache=true` 与 Build Cache 已开启；A13 CI 已运行 full 验证后再执行编译、测试、lint 与 assemble。本轮不改任务图或缓存策略。
- APK 体积、DEX 数量和完整 Android 构建耗时未测量；测试覆盖百分比未生成。没有运行时性能收益声明。
- DexSQL 是 LSPosed 的 APK/DEX SQL CLI 与 MCP，不是 Android 运行库。A13 当前没有 DexSQL 查询集或构建接入；当前 Codex 会话提供只读 DexSQL MCP。可按需查询类/方法、Smali、Manifest 与资源；不添加 APK 依赖、不作为默认 CI 门槛，也不以 DEX 静态查询代替设备证据。官方项目说明可执行文件使用独立二进制许可证，文档使用 MIT；本轮未安装/下载二进制或打开 ROM/APK 样本。参考：[官方项目与许可证](https://github.com/LSPosed/dexsql-release)、[官方 CLI/MCP 文档](https://dexsql.lsposed.org/docs/)。

## 验证与状态

- Gradle Wrapper `--version`：Gradle 9.7.0；Launcher/Daemon JDK 25。Wrapper SHA-256 为 `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae`，最终 URL 保持 Gradle 官方 distribution。
- `gradlew help`：成功；配置探针确认 Java source/target 25、Kotlin JVM_25、language/API 2.4。普通 dependency verification 模式下配置成功。
- `debugRuntimeClasspath`：成功，Kotlin stdlib/BOM 选中 2.4.21，无 `FAILED`。
- `python tools/verify.py fast --changed`、`python tools/verify.py full`：invariants、compatibility contracts、hook contract parity 通过；两者都在 `compileDebugKotlin` 前因本机未接受 Android SDK Platform 36 / Build Tools 36.0.0 许可而失败。该许可不由本任务代为接受，Kotlin 编译、测试和 lint 未运行。
- `python -m compileall tools`：通过。`python -m unittest discover -s tools/tests -p "test_*.py"`：1339 tests，4 skipped，全部通过。ScopeProtectionTest 只在当前工作分支允许 verification metadata 变动；其他排除路径仍受保护。
- `python tools/check_ci_portability.py`、文本编码检查（828 文件）、Git Bash `-n` SDK 脚本语法、`git diff --check`：均通过。
- Issues：#1–#3 已关闭（已完成）；#4–#5 已关闭（不计划）；#12 修复已进入候选提交，待正式发布后关闭。
- 证据：`STATIC_VERIFIED = YES`；`BUILD_VERIFIED = NO`。未生成 APK；D8/R8 对 Java 25 class 文件的实际处理、A13 日志和设备行为都未验证。
- Git：Base SHA `8d46dc29851d70ef5e67071ba48db7c43ff20b15`；当前分支应保持一个版本交付提交。提交 SHA 以分支历史及最终交付记录为准。

## 后续

- 经用户授权接受 Android SDK Platform 36 / Build Tools 36.0.0 许可并确保组件可用后，重跑 fast/full，验证 Kotlin 25 class 文件进入 A13 D8/R8 构建链；随后签名构建、发布、合并并关闭 #12。
- AGP 10 前迁移到支持 JVM 25 的内置 Kotlin 和新版 Android DSL，移除临时 `android.builtInKotlin=false` / `android.newDsl=false` 及对应弃用警告。
