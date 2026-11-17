# DexSQL 开发查询

使用现有 [DexSQL](https://github.com/LSPosed/dexsql-release) CLI 或 MCP 做离线开发取证。查询已在 0.1.1 的表结构上使用，先以已安装版本的 `tables` 或 MCP `describe_table` 确认。ROM、DEX、私有索引和 mapping 留在仓库外。

| 查询 | 输入与用途 |
| --- | --- |
| `definition_sources.sql` | 宿主、插件 / split、必要 framework 的 DEX 来源。 |
| `bootstrap_contract.sql` | A13 Application.attach / SystemUIApplication.onCreate 声明与来源。 |
| `apk_hook_api_definitions.sql` | R8 模块 APK 或 DEX；Hook API 为 compileOnly，结果应为零行。 |
| `apk_settings_api_refs.sql` | Manifest 入口的直接 Hook API 调用 / 类型引用，配合 mapping 与类加载测试。 |
| `hook_api_calls.sql` | 模块对原生 Hook API 102 的调用；核对 setId / getId，replaceHook 应为零。 |
| `native_hook_ids.sql` | 最终 R8 DEX 的两个 A13 基础设施 ID；结合相同产物 mapping 归属安装器，不证明设备回读结果。 |
| `telephony_contract.sql` / `telephony_callers.sql` | 同一 ROM 的 services.jar；API 33 监听记录、分发器及直接调用链。 |
| `notification_channel_contract.sql` | 同一 ROM 的 SystemUI、Settings、boot framework；渠道设置 ABI 与公开入口，配合 Manifest 与 decode。 |
| `maintenance_targets.sql` | services、Home、SecurityCenter 和 SystemUI 的定向定义、成员与调用核对。见 [样本结果](../../docs/rom-intelligence/A13_SAMPLES.md)。 |

1. 先查 [A13 样本](../../docs/rom-intelligence/A13_SAMPLES.md) 与 Hook 调用者。输入、范围和结果已确认则直接复用。来源更换或缺失的 DEX 影响结论时，核对 SHA-256、ZIP 条目 CRC 和有关 DEX 的头摘要。输出成功仍可能来自损坏条目。
2. 先找定义，记录宿主、split 和 `dex_files.entry_name`；已知描述符直接筛选。发现候选可搜名称或具体调用者，再按完整成员描述符及 ClassLoader 绑定。依赖关系按 `file_id` 连接。比较不同 ROM 只用完整描述符与输入身份。
3. 同一输入批量查询。CLI 每个 `--sql` 一条语句，MCP 复用 `dataset_id` 后关闭。只在调用、字段访问或常量仍不明确时 decode 选中的方法。Manifest 和资源用对应 DexSQL 接口。
4. 只保存复查所需的依据，包含输入、定义 DEX、完整成员、用来确认语义的调用或字段、A13 Hook 和样本覆盖。损坏范围的正命中仅作线索，零结果仍未知。运行时结论依照 [证据等级](../../COMPATIBILITY.md#证据等级)。

资源表、Manifest 和产物类归属需要对应的 APK 或独立输入。裸 R8 DEX 可核对定义与直接引用。
