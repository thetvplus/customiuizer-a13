# A13 工程文档

| 问题 | 入口 |
| --- | --- |
| 工程取舍、授权和交付 | [AGENTS](../AGENTS.md)、[项目 Skills](../.agents/skills/README.md) |
| 撰写、翻译、命名与整理文档 | [文档规范](DOCS_STYLE.md) |
| 构建环境与变体 | [开发与构建](DEVELOPMENT.md) |
| 选测、证据复用与 CI | [TESTING](TESTING.md) |
| Hook、ClassLoader、状态与所有权 | [架构](../ARCHITECTURE.md)、[进程矩阵](rom-intelligence/A13_PROCESS_MATRIX.md) |
| 平台范围、证据等级与移植 | [兼容性](../COMPATIBILITY.md)、[A13_PARITY](A13_PARITY.md) |
| ROM 输入、签名和调用链 | [A13 样本](rom-intelligence/A13_SAMPLES.md)、[DexSQL](../tools/dexsql/README.md) |
| r13.14.3 Hook 校准 | [DexSQL 精修计划](rom-intelligence/A13_HOOK_REFINEMENT.md) |
| 正式签名、发布和恢复 | [A13_RELEASE](A13_RELEASE.md) |
| 退役分支与整理 CI 历史 | [Git 与 Actions 维护](DEVELOPMENT.md#git-与-actions-维护) |
| 安装、下载、用户数据与日志 | [项目说明](../README.md)、[隐私说明](../PRIVACY.md) |

## 验证积压

1. 优先补历史反馈 [#19](https://github.com/thetvplus/customiuizer-a13/issues/19) 的最终资源表与 Host Hook 回退证据。以真实缺资源、安装失败和视图恢复为输入，复用现有资源验证。补齐 veux 的有效 SystemUI 输入后再确认左右控制器与视图。
2. 在历史反馈 [#14](https://github.com/thetvplus/customiuizer-a13/issues/14) 取得对应输入后先核对 11.1.6 的成员与归属，再判断是否值得增加适配。锁屏可视化优先验证面板替换、禁用和迟到发布，现有 MAML 路径优先补 SystemUI 重建与原生菜单交接。
3. 共享热路径只处理有明确频率和所有权成本的问题。优先取消无效回调和重复反射。设置变更沿用旧键、备份和回滚行为测试；工具链升级再复核 R8 和产物语义。
