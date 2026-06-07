# Debug Record: Phase 0002

## GitHub CLI auth 与 workflow scope

### 症状

早期 `gh auth status` 输出：

```text
github.com
  X Failed to log in to github.com account Castorice1337 (default)
  - Active account: true
  - The token in default is invalid.
  - To re-authenticate, run: gh auth login -h github.com
  - To forget about this account, run: gh auth logout -h github.com -u Castorice1337
```

### 已尝试

- `gh auth login -h github.com -w`
- `gh auth login --hostname github.com --git-protocol https --web --scopes repo`

### 结果

后续一次 `gh auth login --hostname github.com --git-protocol https --web --scopes repo` 成功登录为 `Castorice1337`，并成功创建远程 repo：

```text
https://github.com/Castorice1337/OpenZen
```

但 push 被 GitHub 拒绝：

```text
refusing to allow an OAuth App to create or update workflow `.github/workflows/build-loader.yml` without `workflow` scope
```

尝试 `gh auth refresh --hostname github.com --scopes repo,workflow` 超时，随后 `gh auth status` 再次显示 token invalid。

### 后续处理

先将 `.github/workflows/build-loader.yml` 暂时排除出公开提交历史，完成源码 push。后续用户需要在本机终端完成带 `workflow` scope 的 GitHub CLI 授权，再单独提交并推送 workflow 文件。
