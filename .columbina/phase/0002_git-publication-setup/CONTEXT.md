# Phase 0002: Git 公开仓库准备

## 状态

partial

## 本阶段目标

为 `D:\OpenZen-master` 创建本地 Git 仓库，并在用户 GitHub 账号下创建公开仓库后推送源码。

## 实际完成内容

- 已在 `D:\OpenZen-master` 执行 `git init`，当前分支为 `master`。
- 已补充 `.gitignore`，排除 `.codex/`、`.agents/`、`.claude/`、`.cursor/`、`src.zip`、`native/dll.zip` 等本地 agent 配置和归档包。
- 已确认 `.codex/`、`.gradle/`、`.idea/`、`build/`、`run/`、`native/build/`、`native/zen.jar`、`native/dll/src/generated_names.h`、`src.zip`、`native/dll.zip` 均处于 ignored 状态。
- 已创建本地初始提交 `f20041c`，提交信息为 `Initial open source import`。
- 已配置仓库本地 Git 作者：`user.name=Castorice1337`，`user.email=Castorice1337@users.noreply.github.com`。
- 已创建远程公开仓库 `https://github.com/Castorice1337/OpenZen`。
- 首次 push 被 GitHub 拒绝，因为当前 OAuth token 缺少 `workflow` scope，不能创建或更新 `.github/workflows/build-loader.yml`。
- 为了先完成公开源码发布，决定暂时从公开提交历史排除 `.github/workflows/build-loader.yml`，待 `workflow` scope 可用后再单独提交该 workflow。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `.gitignore` | 增加本地 AI 配置、zip 归档和临时 workflow 忽略规则 |
| `.columbina/CONTEXT.md` | 增加本阶段索引和 GitHub auth 待确认项 |
| `.columbina/phase/0002_git-publication-setup/CONTEXT.md` | 记录 Git 初始化和 GitHub 发布阻塞状态 |
| `.columbina/phase/0002_git-publication-setup/test.md` | 记录本阶段验证状态 |
| `.columbina/phase/0002_git-publication-setup/debug.md` | 记录 GitHub CLI auth 阻塞信息 |

## 关键实现决策

- 公开仓库不提交本地 agent 配置目录和归档 zip；源码树和构建脚本是 source of truth。
- 保留 `mapping/zen-orignial.jar` 和 `src/main/resources/mapping.srg`，因为它们位于项目源码/资源路径内，且没有证据表明是生成输出。
- 远程仓库名称使用 `OpenZen`，与项目名称一致。
- `.github/workflows/build-loader.yml` 暂时不进入公开提交历史，以绕过当前 token 缺少 `workflow` scope 的限制；这不是永久架构决策。

## 未完成内容

- `.github/workflows/build-loader.yml` 尚未推送。
- 需要重新生成首次公开提交历史并 push。

## 阻塞原因

- GitHub 在 push 时拒绝 workflow 文件：`refusing to allow an OAuth App to create or update workflow .github/workflows/build-loader.yml without workflow scope`。

## 测试状态

未测试
