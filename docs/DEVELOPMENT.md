# A13 开发与构建

| 工具 | 当前配置 |
| --- | --- |
| Gradle / AGP | 9.8.1 / 9.4.1 |
| JDK / Java target | 25 / 25 |
| Kotlin compiler / language / API / JVM target | 2.4.21 / 2.4 / 2.4 / 25 |
| Android compile / min / target SDK | 37 / 33 / 34 |
| SDK Build Tools | 36.0.0 |
| libxposed API / service | 102.0.0 / 102.0.0 |

AGP 管理内置 Kotlin 集成。根构建脚本显式提供 Kotlin 编译器 2.4.21，以保留 JVM 25 支持；
不再应用 `org.jetbrains.kotlin.android`，不使用 `android.builtInKotlin=false` 或
`android.newDsl=false`。Kotlin runtime 仍由版本目录与 BOM 管理。

使用仓库 Gradle Wrapper 与 JDK 25，将 `ANDROID_HOME` 指向含所需 SDK 的目录。
构建配置、版本目录和 [CI 安装脚本](../tools/ci_install_android_sdk.sh) 是版本与 SDK 包标识的依据。
检查选择、定向命令和复用条件见 [TESTING](TESTING.md)。

Hook API 为 `compileOnly`，Service 为设置端运行依赖。产品支持范围见 [COMPATIBILITY](../COMPATIBILITY.md)。

依赖校验保持开启。升级后为实际解析的依赖更新 `gradle/verification-metadata.xml`，
正常门禁使用严格校验；Wrapper 使用官方分发与固定 SHA-256。
配置缓存与构建缓存保持启用，不要求反复 clean。

普通开发与 CI 使用无正式签名的任务。正式流程及恢复见 [A13_RELEASE](A13_RELEASE.md)。

参考：[AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes)、
[内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、
[Gradle 9.8.1](https://docs.gradle.org/9.8.1/release-notes.html)。
