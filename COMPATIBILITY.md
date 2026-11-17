# A13 兼容矩阵

## 平台

| 平台 | 状态 | 规则 |
|---|---|---|
| MIUI 14 / Android 13 | PRIMARY | 已有行为优先；回归必须阻断 |
| HyperOS 1 / Android 13 | COMPATIBILITY_WITH_EVIDENCE | 完整 target variant、能力探测、安全跳过、证据分级 |
| Android 14+ | UNSUPPORTED | 转到 A14 仓库 |

## libxposed

| 能力 | 状态 | 规则 |
|---|---|---|
| API 102 | 最低生产基线 | 原生 HookBuilder 与 Service 102.0.0；不维护 API 101 回退 |

设置进程只加载 Service SDK；Hook API 仍为 `compileOnly`。原生 ID 不代替安装状态和所有者管理，
不启用热重载、显式替换或 detach。Android 13 的 ROM 边界不因 Hook API 升级改变。

当前 ROM 结论与不完整输入范围见 [A13 样本](docs/rom-intelligence/A13_SAMPLES.md)。

## 签名与升级

原 A13 发布私钥目前无法取得，`r13.13.0` 经用户明确授权更换发布证书。
这不是可直接覆盖旧版的更新：先在旧版导出设置备份，卸载后安装新版，再恢复备份、
在框架中重新启用模块并重启设备。后续正式发布应保持本版证书连续性。

| 版本 | 签名证书 SHA-256 |
| --- | --- |
| r13.12.2 | `15ce32f03e4d8e62df9390f77431862e59bf2cf95cd5a72f0c7330cdfcca2934` |
| r13.13.0 | `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70` |

## 证据等级

先标来源，再按实际范围记录验证状态。

| 来源 | 可支持的结论 |
| --- | --- |
| SOURCE | 当前控制流、回退、资源声明与测试夹具覆盖的语义。 |
| ROM | 指定有效样本中的版本、宿主、Manifest 与资源表。 |
| DEX | 指定定义 DEX 中的完整成员描述符、字段访问及可见调用关系。残缺输入中的零结果保持未知。 |
| BUILD | 同输入下实际完成的编译、JVM、lint、R8 或产物检查。只认各门禁输出。 |
| LOG | 对应构建、ROM 与重现场景中日志直接反映的行为。 |
| DEVICE | 指定 A13 设备、样本和构建中实际执行的场景及结果。 |

```text
STATIC_VERIFIED  : 源码 / ROM / DEX / resource / ABI 静态证据；
                    纯静态语义推理；
                    相关纯静态 / 单元测试；
                    不含有运行时或设备行为声明；
                    Android 编译成功不改变 STATIC 或 RUNTIME 声明。

BUILD_VERIFIED   : 相关 Gradle 编译 / lint / test / build 门禁通过；
                    APK assembly 仅在任务要求 APK 时才计入；
                    仍不含有设备运行时声明。

LOG_VERIFIED     : 来自相关 A13 目标环境的 LSPosed / logcat 日志支持该行为。

DEVICE_VERIFIED  : 在 A13 设备上直接验证该行为。

UNVERIFIED       : 只有推断，证据不足。
```

静态与构建证据分别记录：

```text
STATIC_VERIFIED = YES/NO
BUILD_VERIFIED  = YES/NO
```

A14 结果限 A14。静态 ROM 结论限已检查的有效样本和成员。ART 加载、实际回调与设备性能按对应日志或实机记录判断。

## 跨版本移植

每个 `PORT` 任务只记录：功能、A14 来源、A13 状态、平台差异、证据。
禁止维护“所有文件逐行一致”的 parity 表。
A14 功能进入 A13 前必须通过 `docs/A13_PARITY.md` 记录并分类。
