[CmdletBinding()]
param(
    [ValidateSet('status', 'push-private', 'publish-public', 'verify-release')]
    [string]$Action = 'status',
    [string]$PrivateRepo = 'D:\OpenZen-private',
    [string]$PublicRepo = 'D:\OpenZen-public',
    [string]$Commit = '',
    [string]$ReleaseTag = ''
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([string]$Repo, [string[]]$Arguments)
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Git writes normal progress (not only failures) to stderr. Do not let
        # Windows PowerShell's Stop policy turn that progress into an exception.
        $ErrorActionPreference = 'Continue'
        $output = @(& git -C $Repo @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "git -C `"$Repo`" $($Arguments -join ' ') failed:`n$output"
    }
    return $output
}

function Assert-Repo {
    param([string]$Repo)
    if (-not (Test-Path (Join-Path $Repo '.git'))) {
        throw "Not a Git repository: $Repo"
    }
}

function Get-RefCommit {
    param([string]$Repo, [string]$Ref)
    return (Invoke-Git $Repo @('rev-parse', "$Ref^{commit}") | Select-Object -Last 1).Trim()
}

function Assert-Master {
    param([string]$Repo)
    $branch = (Invoke-Git $Repo @('branch', '--show-current') | Select-Object -Last 1).Trim()
    if ($branch -ne 'master') {
        throw "$Repo is on '$branch'; switch to master before synchronizing."
    }
}

function Assert-Clean {
    param([string]$Repo)
    $status = @(Invoke-Git $Repo @('status', '--porcelain'))
    if ($status.Count -gt 0) {
        throw "$Repo has uncommitted changes. Commit or stash them before synchronization."
    }
}

function Assert-Ancestor {
    param([string]$Repo, [string]$Older, [string]$Newer)
    & git -C $Repo merge-base --is-ancestor $Older $Newer 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "$Older is not an ancestor of $Newer in $Repo; refusing a non-fast-forward sync."
    }
}

function Show-Repo {
    param([string]$Label, [string]$Repo)
    Assert-Repo $Repo
    $branch = (Invoke-Git $Repo @('branch', '--show-current') | Select-Object -Last 1).Trim()
    $head = (Invoke-Git $Repo @('rev-parse', 'HEAD') | Select-Object -Last 1).Trim()
    $subject = (Invoke-Git $Repo @('show', '-s', '--format=%s', 'HEAD') | Select-Object -Last 1).Trim()
    [PSCustomObject]@{ Repository = $Label; Path = $Repo; Branch = $branch; Commit = $head; Subject = $subject }
}

switch ($Action) {
    'status' {
        Show-Repo 'private' $PrivateRepo
        Show-Repo 'public' $PublicRepo
        $privateHead = Get-RefCommit $PrivateRepo 'master'
        $publicHead = Get-RefCommit $PublicRepo 'master'
        & git -C $PrivateRepo merge-base --is-ancestor $publicHead $privateHead 2>$null
        [PSCustomObject]@{ PublicMaster = $publicHead; PrivateMaster = $privateHead; PublicIsAncestor = ($LASTEXITCODE -eq 0) }
        break
    }
    'push-private' {
        Assert-Repo $PrivateRepo
        Assert-Master $PrivateRepo
        Assert-Clean $PrivateRepo
        Invoke-Git $PrivateRepo @('push', 'origin', 'master')
        break
    }
    'publish-public' {
        Assert-Repo $PrivateRepo
        Assert-Repo $PublicRepo
        Assert-Master $PrivateRepo
        Assert-Master $PublicRepo
        Assert-Clean $PrivateRepo
        Assert-Clean $PublicRepo

        $target = if ([string]::IsNullOrWhiteSpace($Commit)) { Get-RefCommit $PrivateRepo 'master' } else { Get-RefCommit $PrivateRepo $Commit }
        Assert-Ancestor $PrivateRepo $target (Get-RefCommit $PrivateRepo 'master')
        Invoke-Git $PublicRepo @('fetch', '--no-tags', $PrivateRepo, "$target`:refs/remotes/migration/private-release") | Out-Null
        $publicHead = Get-RefCommit $PublicRepo 'master'
        Assert-Ancestor $PublicRepo $publicHead $target
        Invoke-Git $PublicRepo @('push', 'origin', "$target`:refs/heads/master")
        # Keep the local public worktree on the same fast-forwarded master that
        # was just published, so parity checks do not compare stale local refs.
        Invoke-Git $PublicRepo @('merge', '--ff-only', $target) | Out-Null

        if (-not [string]::IsNullOrWhiteSpace($ReleaseTag)) {
            Invoke-Git $PrivateRepo @('tag', '-a', $ReleaseTag, $target, '-m', "Public release $ReleaseTag")
            Invoke-Git $PublicRepo @('tag', '-a', $ReleaseTag, $target, '-m', "Public release $ReleaseTag")
            Invoke-Git $PrivateRepo @('push', 'origin', $ReleaseTag)
            Invoke-Git $PublicRepo @('push', 'origin', $ReleaseTag)
        }
        Write-Output "Published $target to public/master."
        break
    }
    'verify-release' {
        Assert-Repo $PrivateRepo
        Assert-Repo $PublicRepo
        $privateRef = if ([string]::IsNullOrWhiteSpace($Commit)) { 'master' } else { $Commit }
        $privateCommit = Get-RefCommit $PrivateRepo $privateRef
        $publicCommit = Get-RefCommit $PublicRepo 'master'
        if ($privateCommit -ne $publicCommit) { throw "Release mismatch: private=$privateCommit public=$publicCommit" }
        $privateTree = (Invoke-Git $PrivateRepo @('rev-parse', "$privateCommit^{tree}") | Select-Object -Last 1).Trim()
        $publicTree = (Invoke-Git $PublicRepo @('rev-parse', "$publicCommit^{tree}") | Select-Object -Last 1).Trim()
        if ($privateTree -ne $publicTree) { throw "Release tree mismatch: private=$privateTree public=$publicTree" }
        Write-Output "Release parity PASS: $privateCommit / tree $privateTree"
        break
    }
}
