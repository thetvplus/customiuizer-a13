# A13 工程文档

| 问题 | 入口 |
| --- | --- |
| 工程取舍、授权和交付 | [AGENTS](../AGENTS.md)、[项目 Skills](../.agents/skills/README.md) |
| 构建环境与变体 | [开发与构建](DEVELOPMENT.md) |
| 选测、证据复用与 CI | [TESTING](TESTING.md) |
| Hook、ClassLoader、状态与所有权 | [架构](../ARCHITECTURE.md)、[进程矩阵](rom-intelligence/A13_PROCESS_MATRIX.md) |
| 平台范围、证据等级与移植 | [兼容性](../COMPATIBILITY.md)、[A13_PARITY](A13_PARITY.md) |
| ROM 输入、签名和调用链 | [A13 样本](rom-intelligence/A13_SAMPLES.md)、[DexSQL](../tools/dexsql/README.md) |
| 正式签名、发布和恢复 | [A13_RELEASE](A13_RELEASE.md) |

## 文档维护

源码、资源和构建配置给出当前实现。文档只写稳定合同、操作入口、可复现证据与待验边界。既有权威页面更新后，其他入口链接过去。README 中英文版本保持对应，版本历史只在双语 CHANGELOG 维护。

开发过程由 Git 保存。有复查价值的阶段记录可链接固定提交。清理前查 Markdown、脚本、测试、生成器和工作流的实际引用。核对本地链接与锚点，并验证受影响的消费者。数值以源码或对应报告为准，证据等级按兼容性文档记录。

## 下一阶段

1. 优先补 #19 的最终资源表与 Host Hook 回退证据。以真实缺资源、安装失败和视图恢复为输入，复用现有资源验证。补齐 veux 的有效 SystemUI 输入后再确认左右控制器与视图。
2. 在 #14 取得对应输入后先核对 11.1.6 的成员与归属，再判断是否值得增加适配。锁屏可视化优先验证面板替换、禁用和迟到发布，现有 MAML 路径优先补 SystemUI 重建与原生菜单交接。
3. 共享热路径只处理有明确频率和所有权成本的问题。优先取消无效回调和重复反射。设置变更沿用旧键、备份和回滚行为测试；工具链升级再复核 R8 和产物语义。
