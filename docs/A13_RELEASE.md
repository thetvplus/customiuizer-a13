# A13 签名、发布与恢复

## 范围与基线

本轮 RC 从已合并 PR #15 的 main `d017f4fbdeb23c40a446ee64444726be0e2d4030` 开始。
本轮正式目标为 `r13.14.1` / versionCode 142，升级范围为 `r13.13.0 → r13.14.1`。
`r13.14.0` 仅为此前未公开发布的开发版本；已核对两仓正式发行及 tag，没有发行过 code 142。
用户已明确预授权本版 Stable Release、源码与 Xposed 两仓和同一正式 APK；验收见 [RC 记录](A13_RC.md)。
Agent 连续完成最终 main 验证、签名候选、晋升与双仓核验，无需聊天逐步确认。
GitHub 原生 `release-signing` / `release-publishing` 人工审批继续生效，等待审批不得记为已发布。

维护者随后明确授权：本版所需的原生审批由 Agent 代为执行，无需维护者持续点击页面。
可使用当前受权账号的 GitHub 原生 pending-deployments 审批接口；保留 required reviewer、
精确 main 限定与禁止管理员绕过，仍逐项验证 CI、证书、Artifact 和来源。
这不是取消保护或每次 main push 自动发行；本版授权范围仍限定为 r13.14.1、Stable、双仓和同一 APK。

参考 A14 main `eae2529d1af5e513c8a58c8ce1d198d371e63516` 的签名、APK 检查、来源证明和双仓恢复架构。
A13 仅使用手动触发的两条独立流程，无 workflow_run、自动发布、签名回退或双仓分别构建。

2026-10-24 已分别下载两仓的正式 r13.13.0 APK：文件均为 2,726,322 字节，SHA-256 均为
`b064712965098b9013a27dc448478afdf208c150aea00654960834b87a5dc47c`；Android apksigner
验证 v2 签名、单签名者及证书 SHA-256：

`c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`

公开指纹及基线固定在 `tools/release-policy.json`。不生成替代私钥，不更改包名或 SDK 绕过校验。

## Environment 与权限

两个 Environment 均要求：人工 reviewer、禁止管理员绕过、仅允许类型为 branch 的精确 `main`，
不允许 tag 或通配分支。脚本在签名前及发布前读回实际规则，缺失即失败。
Secrets 仅检查名称和所在范围，不读取、打印或导出明文。

| Environment | Secrets | 使用范围 |
| --- | --- | --- |
| `release-signing` | `A13_KEYSTORE_BASE64`、`A13_STORE_PASSWORD`、`A13_KEY_ALIAS`、`A13_KEY_PASSWORD` | Signed Build 的单个 build 步骤 |
| `release-publishing` | `A13_XPOSED_RELEASE_TOKEN` | Promote Release 的单个 publish 步骤 |

跨仓 token 需要 Xposed A13 仓库 Contents write；向其推送包含工作流的源码提交时还需要 Workflows write，
以及组织要求的授权。源码仓库使用该 job 的 GitHub token；跨仓 token 不进入签名 job。
保留已有普通仓库 Secrets 时须自行核对用途；独立发布流程不引用旧的通用签名 Secret。

2026-10-24 封版前读回：两个 Environment 的 reviewer 均为 `thetvplus`、禁止管理员绕过、仅允许
main branch。四个签名 Secret 名称齐全；首次复核时 release-publishing 的 Secret 列表为空。
发布时须重新核对 `A13_XPOSED_RELEASE_TOKEN`；名称存在不证明权限有效。未读取或复制未知旧 Secret。

## 操作顺序

1. PR 通过 Fast CI 后，在获得当前合并授权并完成检查的前提下 Squash merge。普通 CI 始终无签名。
2. Full CI 现在对每次 main push 执行，不依赖 `[full-ci]`。等待最终 main 的 Fast 和 Full 均成功。
   签名脚本独立核对仓库 ID、workflow ID/path、原生 main、完整 SHA、当前 attempt、job 及必要步骤，
   不接受 PR Merge SHA、历史 SHA、Fork、取消、失败或 skipped job。
3. 在 main 手动运行 **A13 Signed Build**，`source_sha` 输入最终 main 的完整 40 位 SHA。
   无密钥 validate 先检查，然后人工审批 `release-signing`；build 步骤再次检查 main 和 CI。
4. 先在临时 Gradle home/project cache 运行无密钥 `:app:help -PofficialRelease=false`，
   公开其下载/配置诊断；子进程不继承签名 Secret、GitHub Token 或签名配置路径，预检失败不加载密钥。
   之后才将 Base64 严格还原到临时 `release.p12`，用 PKCS12 导出公开证书并核验指纹，
   生成正确 Java Properties 转义的临时配置。密码不进入命令行；Gradle 子进程不继承四个 Secret，
   禁用构建/配置缓存、后台 daemon 和 scan，复用同一次预检的临时 Gradle 目录，退出时全部清理。
   构建输出被捕获，失败只显示操作失败，避免诊断泄露签名值。
5. 只执行一次正式 `assembleRelease` 与 `lintVitalRelease`。finally 和 always 清理临时材料。
   runner 被强制终止时依赖 GitHub 托管临时 runner 的销毁，不使用自托管 runner。
6. 校验正式标志、applicationId、版本、SDK、arm64-v8a、zipalign、v2 签名、证书、libxposed 入口、
   R8 输出，以及 APK 内完整 `sourceCommit`。APK 和元数据共同生成并验证 GitHub SLSA 来源证明。
7. Artifact 名为 `a13-signed-<run_id>-<attempt>`，保留 30 天。严格保留以下五个文件：

   - `CustoMIUIzer-A13-r13.14.1.apk`
   - `SHA256SUMS`
   - `build-metadata.json`
   - `release-notes.md`（双语、摘要和来源）
   - `attestation.sigstore.json`

   构建元数据使用 schema 2，仍绑定完整 source SHA、原始 Run ID/attempt、APK 大小、hash、证书
   以及双语说明 hash。改名不放宽来源校验；旧 schema / SHA 后缀文件名会被拒绝。
   2026-10-24 复核时 Signed Build 无运行记录，没有需要迁移的旧格式候选。

8. 本版在签名验证成功后，按已有预授权在 main 运行 **A13 Stable Release**，选择原始 `build_run_id`。
   按维护者要求更新显示名称；工作流文件仍为 `a13-promote-release.yml`，输入和保护逻辑不变。
   默认 `actions-only` 仅验证 Artifact。选择 `source` 或 `both` 时输入精确确认语句
   `publish r13.14.1 from <build_run_id>`，并人工审批 `release-publishing`。

发布 job 在审批后重新获取原始 Artifact，比较审批前的 source SHA、APK hash、run attempt、Artifact ID。
下载还核对 Artifact ZIP digest、APK、元数据、完整来源证明（包括仓库 ID、workflow、ref、SHA、run、attempt）
和精确源码的版本、双语日志。文件集合为严格白名单，不接受被替换或损坏的产物。

## 幂等与失败恢复

- 源码 tag：`r13.14.1`；Xposed tag：`142-r13.14.1`，均指向相同 source SHA。
- `both` 始终先发布源码仓库，再同步 Xposed；两仓上传原始 Artifact 的完全相同字节。
- 新 Release 标题为 `r13.14.1`。先创建 draft，严格上传并下载核验 APK 和 `build-metadata.json`
  两个附件，最后发布为 `draft=false`、`prerelease=false`、Latest。完整 SHA256SUMS、双语说明文件及
  attestation 保留于原始 Actions Artifact；发布前仍必须验证全部五个文件和来源证明。
  双仓使用相同标题、双语正文和完全相同的 APK/元数据；恢复时也只接受这个精确附件集合。
- 已存在的 tag 指向不同 SHA，或同名资源内容不同，立即终止；绝不 force push、覆盖资源或删除正式 Release。
- 该版本已有源码 tag 或 Release 时，拒绝新的 Signed Build，转为恢复原始候选产物。
- 已有完全一致的正式 Release 只读验证；已有一致的 draft 仅补上传缺失文件，下载全部核验后发布。
- 源码成功、Xposed 失败会明确报告部分成功，保留已成功的 Release。重新运行 Promote Release，
  仍选择**原始成功 Signed Build**；不重新构建、不重新签名。源码已发布且完全一致时允许 main 前进后恢复同步。
- 旧 Artifact 已过期、缺失，或重跑 Signed Build 导致 attempt 改变时，自动恢复失败，不能以重建冒充原始产物。
  应在 30 天内恢复，并自行保存原始公开产物。现有流程不从未知本地 APK 恢复。
- 新发布不能回退到已有更高版本；versionCode 必须高于现行正式版本。只支持正式发行状态，
  不自动改变现有 draft/prerelease 以绕过冲突。

本轮未取得 A13 设备/日志；SecurityCenter 11.1.6 缺实际样本，音乐可视化及扩展电源菜单仍为
RETIRE_CANDIDATE。以上为发布工程和静态/构建门禁，不构成 DEVICE_VERIFIED。

## PR #15 已有验证（历史）

以下为 PR #15 的记录，不代表后续候选 SHA 的验证。当前 RC 结果见 [RC 记录](A13_RC.md)。

- 现有核心精修差异及既有回归复核后，没有新增可证明的功能发布阻塞，不重复改写 Hook。
- Android JVM：150 类、1325 项，0 failures / errors、1 skipped；Lint：0 errors、696 warnings。
- 正常无密钥 R8 通过；公开来源资产生成任务实际执行并核对完整 SHA、versionName/code、applicationId。
  该任务不加载签名、不构建 APK；正式 assembleRelease、lintVitalRelease 和 GitHub attestation 仍待受保护流程审批后执行。
- 新增签名、来源、成功获取产物、draft 恢复及发布中断等回归；Java Properties 特殊密码通过真实 JDK round trip。
- Python 工具测试 936 项（4 skipped），其中新增发布工程回归 40 项。正式构建缺签名配置时实测在组装前拒绝。
- 工作流通过固定版本 actionlint 1.7.12 和结构化静态合同；普通 CI 的签名禁令保留，例外必须满足完整保护合同。
- 当前 A13 APK 没有原生库；允许无 native-code 声明，有原生库时仅允许 arm64-v8a。兼容当前 aapt2 的 minSdkVersion 输出。
- STATIC_VERIFIED = YES；BUILD_VERIFIED = YES（上述 JVM / lint / R8 范围）；未取得 LOG_VERIFIED / DEVICE_VERIFIED。

## English operations

Fast and Full CI remain unsigned. Full CI runs on every main push. Signing is a separate manual main dispatch
with an exact SHA, successful native Fast/Full runs and human `release-signing` approval. Both Environments
require reviewers, no admin bypass and exactly the main branch. The four A13 signing secrets are confined to
the build step, decoded as PKCS#12, certificate-checked and erased afterwards. Official Gradle output is not
printed or uploaded; private signing inputs never become artifacts or saved caches.

For r13.14.1, the maintainer explicitly authorized the Agent to submit native required-reviewer approvals
on their behalf. Keep all Environment protections and exact-source checks. An unsigned cold Gradle bootstrap
now runs before loading signing files, with private variables removed and public diagnostics visible;
main/CI protection is checked again afterwards. Official signing output remains private and temporary caches are erased.

The successful signed candidate is attested and retained for 30 days. Promotion is a separate manual workflow;
`actions-only` is the default. For publication select `source` or `both`, type
`publish <versionName> from <build_run_id>` and approve `release-publishing`. Both repositories receive the same
binary and metadata, titled `r13.14.1`, with identical bilingual notes. Checksums and attestation remain in the
original five-file artifact. Promotion re-verifies the immutable Artifact ID/attempt and all content after
approval, uploads to a draft, downloads assets to verify their bytes, then publishes a stable release.

If Xposed fails after source publication, keep the source Release and retry promotion of the original successful
build. Matching published releases remain unchanged; matching drafts gain only missing files. Conflicting tags,
assets, metadata, notes or expired original artifacts stop recovery. Never rebuild, re-sign, overwrite or delete
an existing release to hide a failure. The maintainer has preauthorized the r13.14.1 stable release to both repositories. Continue through the protected workflows after final main CI succeeds; native human Environment approvals remain mandatory. Record real run IDs and verification results before claiming publication.

Implementation references: [GitHub Environment security](https://docs.github.com/en/actions/reference/security/secure-use),
[attestation verification](https://cli.github.com/manual/gh_attestation_verify),
[offline bundle/repository identity](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-attestations-offline).

## main Ruleset

已核对 API 参数、仓库管理权限、Squash 可用性、PR 检查来源及基线包含关系，
并应用 [最小提案](a13-main-ruleset.proposed.json)。规则 `A13 main safety`（ID `25766547`）
已读回为 Active：main 禁止删除/force push、必须经 PR 且只允许 Squash、bypass 为空，
要求 GitHub Actions（15368）的 `fast` 并保持最新 main；不要求 PR 运行 main-only Full。
规则只匹配 `refs/heads/main`，不限制发行 tag，也不改变两个 Environment 的人工审批。
