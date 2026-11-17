# AGENTS.md · CustoMIUIzer A13

**Judgment over Procedure.** 先确定触发、预期行为和完成条件。沿实际调用链检查输入、所有者、失败和释放，只有具体证据缺口才扩大范围。相关的同根因回归一并修复。复用已核实的来源和检查结果，完整审阅 diff、验证受影响合同后收口。

- 当前用户要求与已授权任务合同优先。源码和实际结果说明当前行为，历史记录供追溯。
- 正确性、兼容性、生命周期、稳定性优先。取消无效工作，复用原生能力和已有状态。成熟实现按实际收益调整。保留 PrefMap、ResourceHooks、ProcessRouter、PreferenceBootstrap 职责，Java 迁移小批量单独提交，保留 MainModule、XposedHelpers 和 MemberUtilsX 的 Java 边界。
- 支持 Android 13，主目标 MIUI 14，HyperOS 1 为实验兼容。包名 `tv.withaibuild.customiuizer.r13`，ABI `arm64-v8a`。Hook API 以 [模块元数据](app/src/main/resources/META-INF/xposed/module.prop) 和 [兼容说明](COMPATIBILITY.md) 为准。A14 的生产分支、APK、签名和运行库独立，Android 14+ 与灵动额头不进入 A13。
- 运行时保持原调用、异常与所有权合同。功能按所属进程和 ClassLoader 安装，关闭时不启动业务工作。冷路径准备稳定成员，热路径读取已准备的有界状态。详见 [架构](ARCHITECTURE.md)。
- 已有功能改语义前依次核对 MonwF 上游、A13 历史、当前源码和 A14 参考。移植前在 [A13_PARITY](docs/A13_PARITY.md) 分类，独立证明 A13 的目标和回退。ROM 取证按需使用 [DexSQL](tools/dexsql/README.md)。
- 按 [TESTING](docs/TESTING.md) 从 Quick 选到 Standard / Deep，保留有效断言和独立门禁。分别报告静态与构建结果，ROM、日志和设备结论遵循 [证据等级](COMPATIBILITY.md#证据等级)。
- 从已确认的 exact SHA 建 `devin/a13-*`。保护未知修改和已有工作树，禁止 force push、破坏性 reset、改写公开历史和无差别 clean。开发授权覆盖 commit、push、PR 和必要 CI 跟进。合并 `main` 须用户要求，只读或本地停止点优先。交付记录 Base SHA、Final SHA 和 commits。
- 仅在当前发行授权下变更正式版本、签名、打 tag 或发布，遵循 [A13_RELEASE](docs/A13_RELEASE.md)。正式版本名、单调递增的 versionCode 和双语 CHANGELOG 同步。APK、签名材料、ROM、DEX、原始日志、trace、mapping 和 profiler 数据留在仓库外。

按需入口见 [文档导航](docs/README.md) 与 [项目 Skills](.agents/skills/README.md)。
