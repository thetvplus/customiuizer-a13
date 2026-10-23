# DexSQL 开发查询

使用官方 [DexSQL](https://github.com/LSPosed/dexsql-release) 0.1.1 的 CLI 或现有 MCP。
只用于按需开发分析，不加入 Gradle、APK runtime 或默认 CI。
查询参考 A14 `9feacdc6736de98232958ea24b8f722cfb3eff85`，已按 A13 的 Manifest 与启动路径调整。

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
| `maintenance_targets.sql` | 本轮 services、Home、SecurityCenter 与通知菜单的定义复查；按输入使用对应查询，证据范围见 `docs/A13_CORE_MAINTENANCE.md`。 |

MCP 先 `list_tables` / `describe_table`，再 `open_dataset`；一次 `query.sql` 批量传入相关 SQL，
复用 `dataset_id`。结果使用小 `limit` / `max_bytes`，页查询保持 `ORDER BY`，
需要时才 `decode` 唯一方法；分析完 `close_dataset`。CLI 支持多条 `--sql` 和 compact 输出。

Manifest 使用 `get_manifest` / `get_manifest_components`，资源使用 `query_resources` / `read_resource`；
无需自行解析 DEX 或解包 APK。APK / ROM / mapping 留在仓库外。
所有跨 DEX 连接保留 `file_id`，跨样本比较完整描述符与样本哈希，不能比较局部 method_idx。

先检查 ZIP CRC 并核对输入的 DEX 条目和 `definition_sources.sql` 行数；部分输入解析成功
不能证明整个 APK 完整。已知样本及跳过范围见 [A13 样本](../../docs/rom-intelligence/A13_SAMPLES.md)。

静态声明与直接调用不能证明 ART 实际 ClassLoader、间接加载、资源运行效果或设备行为。
裸 R8 DEX 没有 Manifest / resources.arsc；这些检查需已有 APK 或独立的相关输入。
