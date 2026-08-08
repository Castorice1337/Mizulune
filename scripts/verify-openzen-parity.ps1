[CmdletBinding()]
param(
    [string]$PrivateRepo = 'D:\OpenZen-private',
    [string]$PublicRepo = 'D:\OpenZen-public',
    [switch]$RequireExact,
    [switch]$CheckSensitivePaths
)

$ErrorActionPreference = 'Stop'

function GitAt {
    param([string]$Repo, [string[]]$Args)
    $result = & git -C $Repo @Args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git -C $Repo $($Args -join ' ') failed:`n$result" }
    return $result
}

foreach ($repo in @($PrivateRepo, $PublicRepo)) {
    if (-not (Test-Path (Join-Path $repo '.git'))) { throw "Not a Git repository: $repo" }
}

$private = (GitAt $PrivateRepo @('rev-parse', 'master^{commit}') | Select-Object -Last 1).Trim()
$public = (GitAt $PublicRepo @('rev-parse', 'master^{commit}') | Select-Object -Last 1).Trim()
$privateTree = (GitAt $PrivateRepo @('rev-parse', "$private^{tree}") | Select-Object -Last 1).Trim()
$publicTree = (GitAt $PublicRepo @('rev-parse', "$public^{tree}") | Select-Object -Last 1).Trim()

& git -C $PrivateRepo merge-base --is-ancestor $public $private 2>$null
$publicIsAncestor = $LASTEXITCODE -eq 0
if (-not $publicIsAncestor) { throw "public/master $public is not an ancestor of private/master $private" }
if ($RequireExact -and $private -ne $public) { throw "Exact parity required but commits differ: private=$private public=$public" }
if ($RequireExact -and $privateTree -ne $publicTree) { throw "Exact parity required but trees differ: private=$privateTree public=$publicTree" }

$publicPaths = @(GitAt $PublicRepo @('ls-tree', '-r', '--name-only', 'master'))
if (-not ($publicPaths -contains 'src/main/java/shit/zen/protocol/heypixel/HeyPixelProtocolRuntime.java')) {
    throw 'Public master does not contain the canonical HeyPixel protocol runtime.'
}

if ($CheckSensitivePaths) {
    $sensitive = $publicPaths | Where-Object {
        $_ -match '(^|/)(\.env(\..*)?|credentials?(\..*)?|secrets?(\..*)?|protocol-session(\..*)?|.*\.(pem|p12|pfx|jks|key|asc|gpg))$' -or
        $_ -match '(^|/)MaxHook\.dll$'
    }
    if ($sensitive) { throw "Sensitive paths found in public tree:`n$($sensitive -join "`n")" }
}

[PSCustomObject]@{
    PrivateCommit = $private
    PublicCommit = $public
    PrivateTree = $privateTree
    PublicTree = $publicTree
    PublicIsAncestor = $publicIsAncestor
    Exact = ($private -eq $public -and $privateTree -eq $publicTree)
    ProtocolPublic = $true
}
