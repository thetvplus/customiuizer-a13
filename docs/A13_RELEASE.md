# A13 签名、发布与恢复

## 范围与基线

正式版默认自动发布：最新 `main` 的 Full CI 成功后自动签名，签名构建成功后自动同步源码与 Xposed 两仓。
人工 `workflow_dispatch` 保留为恢复入口，要求精确 SHA/原始 Run ID。两个 Environment 继续隔离签名与跨仓发布 Secrets，但不配置 required reviewers；仅允许 `main`。
候选验证检查工作流、来源、证书、Artifact 与原始 attempt。
用户安装与签名迁移见 [兼容说明](../COMPATIBILITY.md#签名与升级)。

r13.14.1 的封版与单次授权属于 [历史交接](https://github.com/thetvplus/customiuizer-a13/blob/78fe48b3ea1f7fa8637215a26025ff464ad25708/docs/A13_RC.md)。
现行发行规则以两个受保护工作流、`tools/release-policy.json` 和实际回执为准。

2026-10-24 已分别下载两仓的正式 r13.13.0 APK：文件均为 2,726,322 字节，SHA-256 均为
`b064712965098b9013a27dc448478afdf208c150aea00654960834b87a5dc47c`；Android apksigner
验证 v2 签名、单签名者及证书 SHA-256：

`c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`

公开指纹及基线固定在 `tools/release-policy.json`。不生成替代私钥，不更改包名或 SDK 绕过校验。

## Environment 与权限

两个 Environment 均要求：**无 required reviewers**、禁止管理员绕过、仅允许类型为 branch 的精确 `main`，
不允许 tag 或通配分支。首次启用时，在 GitHub Settings → Environments 中移除两个环境的 Required reviewers（保留 main-only 分支限制和 Secrets）；脚本在签名前及发布前读回规则，配置仍有人审则失败。
Secrets 仅检查名称和所在范围，不读取、打印或导出明文。

| Environment | Secrets | 使用范围 |
| --- | --- | --- |
| `release-signing` | `A13_KEYSTORE_BASE64`、`A13_STORE_PASSWORD`、`A13_KEY_ALIAS`、`A13_KEY_PASSWORD` | Signed Build 的单个 build 步骤 |
| `release-publishing` | `A13_XPOSED_RELEASE_TOKEN` | Promote Release 的单个 publish 步骤 |

跨仓 token 需要 Xposed A13 仓库 Contents write；向其推送包含工作流的源码提交时还需要 Workflows write，
以及组织要求的授权。源码仓库使用该 job 的 GitHub token；跨仓 token 不进入签名 job。
保留已有普通仓库 Secrets 时须自行核对用途；独立发布流程不引用旧的通用签名 Secret。

## 操作顺序

1. PR 通过 Fast CI 后，在获得当前合并授权并完成检查的前提下 Squash merge。普通 CI 始终无签名。
2. 等待最终 main 的 Fast 和 Full 均成功。Full CI 对每次 main push 执行。
   签名脚本独立核对仓库 ID、workflow ID/path、原生 main、完整 SHA、当前 attempt、job 及必要步骤，
   不接受 PR Merge SHA、历史 SHA、Fork、取消、失败或 skipped job。Fast 与 Full 独立运行，自动预检可等待同一 SHA 的 Fast 完成。
3. 默认由 **A13 Full CI** 在 main 成功后通过 `workflow_run` 自动触发 **A13 Signed Build**。无密钥 validate 校验事件源、原生 main/CI、版本及环境；只有新版本才进入签名。可选从 main 手动运行 Signed Build，提供最终 main 的完整 SHA。
   `release-signing` 不再需要逐次人工审批；签名前重新检查 main、CI、Environment 和发布版本。
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

   - `CustoMIUIzer-A13-<versionName>.apk`
   - `SHA256SUMS`
   - `build-metadata.json`
   - `release-notes.md`（双语、摘要和来源）
   - `attestation.sigstore.json`

   构建元数据使用 schema 2，仍绑定完整 source SHA、原始 Run ID/attempt、APK 大小、hash、证书
   以及双语说明 hash。改名不放宽来源校验；旧 schema / SHA 后缀文件名会被拒绝。

8. 自动签名构建成功后，`workflow_run` 自动启动 **A13 Stable Release**，绑定触发它的原始 `build_run_id`，验证 Artifact、来源证明、SHA、证书及两仓发布权限，然后自动选择 `both`。
   `release-publishing` 不再需要逐次人工审批。保留手动恢复模式：默认 `actions-only` 只验证；选择 `source` 或 `both` 时仍须填写 `publish <versionName> from <build_run_id>`。

发布 job 在验证后重新获取原始 Artifact，比较前后 source SHA、APK hash、run attempt、Artifact ID。
下载还核对 Artifact ZIP digest、APK、元数据、完整来源证明（包括仓库 ID、workflow、ref、SHA、run、attempt）
和精确源码的版本、双语日志。文件集合为严格白名单，不接受被替换或损坏的产物。

## 幂等与失败恢复

- 源码 tag 用 `<versionName>`，Xposed tag 用 `<versionCode>-<versionName>`，均指向相同 source SHA。
- `both` 始终先发布源码仓库，再同步 Xposed；两仓上传原始 Artifact 的完全相同字节。
- 新 Release 标题使用核验过的版本名。先创建 draft，严格上传并下载核验 APK 和 `build-metadata.json`
  两个附件，最后发布为 `draft=false`、`prerelease=false`、Latest。完整 SHA256SUMS、双语说明文件及
  attestation 保留于原始 Actions Artifact；发布前仍必须验证全部五个文件和来源证明。
  双仓使用相同标题、双语正文和完全相同的 APK/元数据；恢复时也只接受这个精确附件集合。
- 已存在的 tag 指向不同 SHA，或同名资源内容不同，立即终止；绝不 force push、覆盖资源或删除正式 Release。
- 已有完整正式源码 Release 时跳过签名与晋升。只有 tag 或存在 draft 时，停止签名并使用原始候选产物恢复。
- 相同 SHA 已有成功签名的候选产物时，重复触发跳过签名与晋升。恢复时使用原成功 Signed Build 的 Run ID，不重跑该构建。
- 已有完全一致的正式 Release 只读验证；已有一致的 draft 仅补上传缺失文件，下载全部核验后发布。
- 源码成功、Xposed 失败会明确报告部分成功，保留已成功的 Release。重新运行 Promote Release，
  仍选择**原始成功 Signed Build**；不重新构建、不重新签名。源码已发布且完全一致时允许 main 前进后恢复同步。
- 旧 Artifact 已过期、缺失，或重跑 Signed Build 导致 attempt 改变时，自动恢复失败，不能以重建冒充原始产物。
  应在 30 天内恢复，并自行保存原始公开产物。现有流程不从未知本地 APK 恢复。
- 新发布不能回退到已有更高版本；versionCode 必须高于现行正式版本。只支持正式发行状态，
  不自动改变现有 draft/prerelease 以绕过冲突。

## English operations

By default, a successful native A13 Full CI on the latest main triggers A13 Signed Build via workflow_run; a successful signed build triggers A13 Stable Release, which promotes the original verified artifact to both source and Xposed repositories. No per-release human Environment reviewers or authorization phrase are required for this trusted chain. Manual dispatch remains for recovery and still requires explicit source SHA or original signed build ID, and manual publication authorization.

Both Environment secrets remain isolated. Before enabling unattended releases, remove Required reviewers in GitHub Settings → Environments for release-signing and release-publishing, while retaining exact main-only deployment branch policies, disabled admin bypass, scoped secrets, and all provenance and certificate verification. An Environment with reviewers still configured fails release preflight rather than silently bypassing policy.

Fast and Full CI remain unsigned. The signed artifact uses one exact source SHA, native CI proof, a fixed Android signing certificate, GitHub provenance, strict five-file allowlist, and immutable attempt/artifact checks. Both repositories receive exactly the same verified bytes. A partial Xposed failure must be recovered from the original artifact, without rebuild or re-sign.


## main Ruleset

已核对 API 参数、仓库管理权限、Squash 可用性、PR 检查来源及基线包含关系，
并应用 [最小提案](a13-main-ruleset.proposed.json)。规则 `A13 main safety`（ID `25766547`）
已读回为 Active：main 禁止删除/force push、必须经 PR 且只允许 Squash、bypass 为空，
要求 GitHub Actions（15368）的 `fast` 并保持最新 main；不要求 PR 运行 main-only Full。
规则只匹配 `refs/heads/main`，不限制发行 tag，也不改变两个 Environment 的人工审批。

## 正式构建图回归

首次 Signed Build 的两个失败 attempt 均无候选 Artifact。随后用不存在的占位 keystore、
`officialRelease=true` 与 `--dry-run` 复现：AGP 9.4 拒绝 SourceSet API 的 Provider 目录。
来源证明改为有声明输入/输出的任务，通过 Variant API `assets.addGeneratedSourceDirectory` 接入，
自动建立生成任务到资源合并的依赖；来源文件格式、版本和证书门禁保持。
官方接口见 [SourceDirectories](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/variant/SourceDirectories.Flat)。

main Fast 和 Full 均运行 `python tools/check_release_build_graph.py`，配置真实正式任务图，
使用占位配置和强制 `--dry-run`，不读取真实密钥、不执行打包；断言来源生成先于 assets 合并与 APK 打包，
并包含 lint。签名预检还必须读回这一步 success。来源生成任务另在本地实际执行，核对完整 SHA、
versionName/code、包名和 release 标志；该测试同样不使用真实私钥或生成 APK。
