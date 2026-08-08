# Repository workflow

OpenZen 使用两个 GitHub 仓库：

- `Mizulune-Private`：日常开发主线，承载所有小更新和实验提交。
- `Mizulune`：公开的大版本发布线，发布时从 private 的指定提交快进同步。

协议实现、协议测试、编解码器、生命周期代码和公开 SDK 属于共享源码，随大版本进入 public。private 不再保存 public 没有的源码分支。

## 日常小更新

在 `D:\OpenZen-private` 的 `master` 分支提交并推送：

```powershell
.\scripts\openzen-sync.ps1 -Action push-private
```

private 工作树必须干净，脚本只允许在 `master` 上推送。

## 大版本公开发布

先在 private 完成构建和测试，再把当前提交同步到 public：

```powershell
.\scripts\openzen-sync.ps1 -Action publish-public -ReleaseTag vX.Y.Z
.\scripts\openzen-sync.ps1 -Action verify-release
```

也可以通过 `-Commit <sha>` 发布 private 历史中的指定提交。发布只允许 public `master` 快进，拒绝覆盖 public 上已有的新提交。

## 一致性检查

```powershell
.\scripts\verify-openzen-parity.ps1 -CheckSensitivePaths
```

日常状态允许 private 领先 public；发布完成后使用 `-RequireExact` 验证两个 `master` 指向同一个 commit 和 Git tree：

```powershell
.\scripts\verify-openzen-parity.ps1 -RequireExact -CheckSensitivePaths
```

密钥、token、session、私钥、官方闭源 DLL 和构建产物不提交到任一仓库。
