---
name: a13-safe-implementation
description: 实施 CustoMIUIzer A13 代码、验证工具、CI 或工程文档变更，按受影响的合同选测并交付。指定 Git diff 的只读审查使用 a13-independent-review。
---

# A13 安全实施

沿用当前任务已确认的授权、基线与证据，按 [AGENTS](../../../AGENTS.md) 执行。

1. 用真实触发、调用者和失败结果划定范围。设置行为从 preference key 追到 Installer 或控制器，工具和文档从 CLI、CI 与链接消费者追到权威输入。仅有具体 ROM 成员或 loader 疑问时查询 [DexSQL](../../../tools/dexsql/README.md)。
2. 实施最小可回退改动。改 Hook 核对原调用次数、异常边界、所有者、失效和回退。改资源、偏好或入口时核对包装、旧数据、反射与生成引用。优先复用已有能力。测试应检出真实缺陷，正确的生产实现不受静态断言的写法支配。
3. 审阅完整任务 diff，按 [TESTING](../../../docs/TESTING.md#执行分级) 补受影响的检查。复用同输入的有效结果，具体缺口才扩大验证。
4. 按任务授权完成提交、推送、PR 与必要 CI。报告已验证范围、失败或待验项。正式操作遵循 [发行流程](../../../docs/A13_RELEASE.md)。
