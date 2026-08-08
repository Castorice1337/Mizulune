[CmdletBinding()]
param(
    [switch]$SourceOnly,
    [string]$RepositoryRoot,
    [string]$DistDirectory,
    [string]$JarPath,
    [string]$ReportPath,
    [switch]$AllowUnbundledMaxHook
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)

$machinePatterns = @(
    @{ Name = 'launcher-specific MCLDownload path'; Pattern = '(?i)(?:^|[^A-Za-z0-9_])MCLDownload(?:[^A-Za-z0-9_]|$)' },
    @{ Name = 'workspace directory name'; Pattern = '(?i)(?:^|[^A-Za-z0-9_])OpenZen-master(?:[^A-Za-z0-9_]|$)' },
    @{ Name = 'absolute Windows user profile'; Pattern = '(?i)[A-Z]:[\\/]Users[\\/](?!Public(?:[\\/]|$))[^\\/\s"''<>]+' }
)

function Get-RelativePath([string]$Root, [string]$Path) {
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]'\/')
    $pathFull = [IO.Path]::GetFullPath($Path)
    $prefix = $rootFull + [IO.Path]::DirectorySeparatorChar
    if (-not $pathFull.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the expected root"
    }
    return $pathFull.Substring($prefix.Length).Replace('\', '/')
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant()
}

function Find-MachineMarkers([string]$Text, [string]$Label) {
    $findings = @()
    foreach ($rule in $machinePatterns) {
        if ($Text -match $rule.Pattern) {
            $findings += "$Label contains $($rule.Name)"
        }
    }
    return $findings
}

function Test-SourcePortability {
    $extensions = @(
        '.java', '.kt', '.groovy', '.gradle', '.xml', '.html', '.js', '.css',
        '.json', '.md', '.yml', '.yaml', '.toml', '.cpp', '.c', '.h', '.hpp',
        '.cs', '.csproj', '.cmake', '.properties', '.ps1'
    )
    $roots = @(
        'src/main/java',
        'src/main/resources',
        'native/loader',
        'native/dll',
        'backends/Mizulune.FantnelHost',
        '1.21.4protocolmod/src/main'
    )
    $files = @()
    foreach ($relativeRoot in $roots) {
        $root = Join-Path $RepositoryRoot $relativeRoot
        if (Test-Path -LiteralPath $root) {
            $files += Get-ChildItem -LiteralPath $root -Recurse -File |
                Where-Object {
                    $extensions -contains $_.Extension.ToLowerInvariant() -and
                    $_.FullName -notmatch '[\\/](?:bin|obj|build|\.dotnet)[\\/]'
                }
        }
    }
    foreach ($relativeFile in @(
        'build.gradle', 'settings.gradle', 'README.md',
        '1.21.4protocolmod/README.md', '.github/workflows/build-loader.yml'
    )) {
        $path = Join-Path $RepositoryRoot $relativeFile
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $files += Get-Item -LiteralPath $path
        }
    }

    $findings = @()
    foreach ($file in ($files | Sort-Object FullName -Unique)) {
        $relative = Get-RelativePath $RepositoryRoot $file.FullName
        $text = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
        $findings += Find-MachineMarkers $text $relative
        if ($relative -match '(?i)(^|/)webui/' -and $file.Extension -match '(?i)^\.(html|css|js)$') {
            if ($text -match '(?is)<(?:script|link)\b[^>]*(?:src|href)\s*=\s*["'']https?://') {
                $findings += "$relative loads a remote script or stylesheet"
            }
            if ($text -match '(?i)@import\s+(?:url\()?\s*["'']?https?://') {
                $findings += "$relative imports a remote stylesheet"
            }
            if ($text -match '(?i)\$\s*\.\s*ajax|jquery') {
                $findings += "$relative still depends on jQuery"
            }
        }
    }
    if ($findings.Count -gt 0) {
        throw ("Source portability verification failed:`n - " + ($findings -join "`n - "))
    }
    return [ordered]@{ filesScanned = ($files | Sort-Object FullName -Unique).Count; findings = 0 }
}

function Open-Zip([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    return [IO.Compression.ZipFile]::OpenRead($Path)
}

function Read-ZipEntryText($Entry) {
    $stream = $Entry.Open()
    try {
        $memory = New-Object IO.MemoryStream
        try {
            $stream.CopyTo($memory)
            return [Text.Encoding]::UTF8.GetString($memory.ToArray())
        } finally {
            $memory.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Test-ZipEntryNames($Archive, [string]$Label) {
    $names = @($Archive.Entries | ForEach-Object { $_.FullName })
    $invalid = @($names | Where-Object {
        $_ -match '^[\\/]' -or $_ -match '^[A-Za-z]:' -or
        $_ -match '(^|[\\/])\.\.([\\/]|$)' -or $_ -match '\\'
    })
    $duplicates = @($names | Group-Object | Where-Object Count -gt 1 | ForEach-Object Name)
    $caseDuplicates = @($names | Group-Object { $_.ToLowerInvariant() } |
        Where-Object Count -gt 1 | ForEach-Object Name)
    if ($invalid.Count -gt 0 -or $duplicates.Count -gt 0 -or $caseDuplicates.Count -gt 0) {
        throw "$Label contains unsafe or duplicate ZIP entry names"
    }
}

function Test-Jar([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "JAR is missing"
    }
    $expectedLibraries = @(
        'openzen/dll-libs/annotations-23.0.0.jar',
        'openzen/dll-libs/jbr-api-1.5.0.jar',
        'openzen/dll-libs/jlayer-1.0.1.4.jar',
        'openzen/dll-libs/kotlin-stdlib-2.3.20.jar',
        'openzen/dll-libs/kotlinx-coroutines-core-jvm-1.8.0.jar',
        'openzen/dll-libs/mp3spi-1.9.5.4.jar',
        'openzen/dll-libs/skiko-awt-0.148.1.jar',
        'openzen/dll-libs/skiko-awt-runtime-windows-x64-0.148.1.jar',
        'openzen/dll-libs/tritonus-share-0.3.7.4.jar'
    ) | Sort-Object
    $archive = Open-Zip $Path
    try {
        Test-ZipEntryNames $archive 'Mizulune JAR'
        $libraries = @($archive.Entries | Where-Object {
            $_.FullName -match '^openzen/dll-libs/[^/]+\.jar$'
        } | ForEach-Object FullName | Sort-Object)
        if (($libraries -join "`n") -ne ($expectedLibraries -join "`n")) {
            throw "Embedded runtime JAR set differs from the locked distribution contract"
        }

        $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $manifestEntry) { throw 'JAR manifest is missing' }
        $manifest = Read-ZipEntryText $manifestEntry
        if ($manifest -notmatch '(?m)^Implementation-Title:\s*Mizulune\s*$') {
            throw 'JAR Implementation-Title is not stable Mizulune'
        }
        $webUi = $archive.GetEntry('webui/index.html')
        if ($null -ne $webUi) {
            $webText = Read-ZipEntryText $webUi
            if ($webText -match '(?is)<(?:script|link)\b[^>]*(?:src|href)\s*=\s*["'']https?://' -or
                $webText -match '(?i)\$\s*\.\s*ajax|jquery') {
                throw 'Packaged Java WebUI still has a remote or jQuery dependency'
            }
        }

        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/') -or $entry.FullName -match '^openzen/dll-libs/') { continue }
            if ($entry.FullName -notmatch '(?i)\.(class|html|js|css|json|xml|properties|mf|toml|yml|yaml|md)$') { continue }
            $findings = @(Find-MachineMarkers (Read-ZipEntryText $entry) ("JAR:" + $entry.FullName))
            if ($findings.Count -gt 0) { throw ($findings -join '; ') }
        }
        return [ordered]@{
            path = [IO.Path]::GetFileName($Path)
            sha256 = Get-Sha256 $Path
            embeddedRuntimeJars = $libraries.Count
            safeEntries = $archive.Entries.Count
        }
    } finally {
        $archive.Dispose()
    }
}

function Get-DistFileMap([string]$Root) {
    $map = @{}
    foreach ($file in Get-ChildItem -LiteralPath $Root -Recurse -File) {
        $relative = Get-RelativePath $Root $file.FullName
        $map[$relative] = [ordered]@{ size = $file.Length; sha256 = Get-Sha256 $file.FullName }
    }
    return $map
}

function Test-Distribution([string]$Root) {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        throw 'Distribution directory is missing'
    }
    $manifestPath = Join-Path $Root 'mizulune-distribution-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw 'Distribution manifest is missing'
    }
    $manifestText = Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestPath
    $markerFindings = @(Find-MachineMarkers $manifestText 'distribution manifest')
    if ($markerFindings.Count -gt 0) { throw ($markerFindings -join '; ') }
    $manifest = $manifestText | ConvertFrom-Json
    if ($manifest.schema -ne 'mizulune.loader-distribution/v1') {
        throw 'Unexpected distribution manifest schema'
    }
    if (-not $manifest.maxHook.bundled -and -not $AllowUnbundledMaxHook) {
        throw 'Portable distribution must bundle the hash-pinned MaxHook input'
    }
    if (-not $manifest.jvm.bundled -or $manifest.jvm.distribution -ne 'in-package') {
        throw 'Portable distribution must bundle the pinned MaxHook JVM'
    }

    $actual = Get-DistFileMap $Root
    $declared = @{}
    foreach ($entry in $manifest.bundledFiles) {
        $path = [string]$entry.path
        $key = $path.ToLowerInvariant()
        if ($declared.ContainsKey($key)) { throw "Duplicate bundled file entry: $path" }
        $declared[$key] = $path
        if (-not $actual.ContainsKey($path)) { throw "Declared distribution file is missing: $path" }
        if ([int64]$entry.size -ne [int64]$actual[$path].size -or
            ([string]$entry.sha256).ToUpperInvariant() -ne $actual[$path].sha256) {
            throw "Distribution file hash/size mismatch: $path"
        }
    }
    $expectedActual = @($manifest.bundledFiles | ForEach-Object { [string]$_.path }) +
        'mizulune-distribution-manifest.json'
    $unexpected = @($actual.Keys | Where-Object { $expectedActual -notcontains $_ })
    $missing = @($expectedActual | Where-Object { -not $actual.ContainsKey($_) })
    if ($unexpected.Count -gt 0 -or $missing.Count -gt 0) {
        throw "Distribution exact file set failed; missing=$missing unexpected=$unexpected"
    }

    foreach ($required in @(
        'MizuluneLoader.exe',
        'fantnel/Mizulune.FantnelHost.exe',
        'fantnel/resources/7z.exe',
        'fantnel/resources/fantnel-bootstrap.json'
    )) {
        if (-not $actual.ContainsKey($required)) { throw "Required package file is missing: $required" }
    }
    if ($manifest.maxHook.bundled) {
        $maxHookPath = [string]$manifest.maxHook.packagedRelativePath
        if ($maxHookPath -ne 'maxhook/MaxHook.dll' -or -not $actual.ContainsKey($maxHookPath)) {
            throw 'Bundled MaxHook path is not canonical'
        }
        if ($actual[$maxHookPath].sha256 -ne ([string]$manifest.maxHook.requiredSha256).ToUpperInvariant()) {
            throw 'Bundled MaxHook hash is not the required hash'
        }
    }
    $jdkRoot = [string]$manifest.jvm.packagedRelativeRoot
    $javaPath = [string]$manifest.jvm.javaExecutableRelativePath
    $runtimeManifestPath = [string]$manifest.jvm.runtimeManifestRelativePath
    $jvmPath = "$jdkRoot/bin/server/jvm.dll"
    if ($jdkRoot -ne 'runtime/jdk17' -or
        $javaPath -ne 'runtime/jdk17/bin/java.exe' -or
        $runtimeManifestPath -ne
            'runtime/jdk17/maxhook-java-runtime-manifest.json') {
        throw 'Bundled JVM paths are not canonical'
    }
    foreach ($requiredRuntime in @($javaPath, $jvmPath, $runtimeManifestPath)) {
        if (-not $actual.ContainsKey($requiredRuntime)) {
            throw "Required bundled JVM file is missing: $requiredRuntime"
        }
    }
    $requiredJvmHash = ([string]$manifest.jvm.requiredJvmSha256).ToUpperInvariant()
    if ($actual[$jvmPath].sha256 -ne $requiredJvmHash) {
        throw 'Bundled JVM hash is not the required hash'
    }
    $runtimeManifest = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath (Join-Path $Root $runtimeManifestPath) | ConvertFrom-Json
    if ($runtimeManifest.schema -ne 'openzen.maxhook-java-runtime/v1' -or
        ([string]$runtimeManifest.requiredJvmSha256).ToUpperInvariant() -ne
            $requiredJvmHash) {
        throw 'Bundled JVM runtime manifest is invalid'
    }

    $forbiddenArtifact = @($actual.Keys | Where-Object {
        (($_ -match '(?i)\.zip$') -or
            ($_ -match '(?i)(^|/)(?:jdk|jre|java)(?:/|$)') -or
            ($_ -match '(?i)(?:javaw?\.exe|jvm\.dll)$')) -and
            $_ -notmatch '^runtime/jdk17/'
    })
    if ($forbiddenArtifact.Count -gt 0) {
        throw "Loader package contains non-canonical JVM/archive pollution: $forbiddenArtifact"
    }
    return [ordered]@{
        files = $actual.Count
        bytes = [int64](($actual.Values | ForEach-Object { $_.size } | Measure-Object -Sum).Sum)
        maxHookBundled = [bool]$manifest.maxHook.bundled
        jvmBundled = [bool]$manifest.jvm.bundled
        jvmSha256 = $requiredJvmHash
        manifestSha256 = Get-Sha256 $manifestPath
    }
}

function Test-Relocation([string]$SourceRoot) {
    $base = Join-Path $RepositoryRoot 'build/portability-test'
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    $run = Join-Path $base (([Guid]::NewGuid().ToString('N')) + '/relocated package/nested level')
    New-Item -ItemType Directory -Force -Path $run | Out-Null
    try {
        foreach ($child in Get-ChildItem -LiteralPath $SourceRoot -Force) {
            Copy-Item -LiteralPath $child.FullName -Destination $run -Recurse -Force
        }
        $before = Get-DistFileMap $SourceRoot
        $after = Get-DistFileMap $run
        $beforePaths = ($before.Keys | Sort-Object) -join "`n"
        $afterPaths = ($after.Keys | Sort-Object) -join "`n"
        if ($beforePaths -ne $afterPaths) {
            throw 'Relocated file set differs from source distribution'
        }
        foreach ($path in $before.Keys) {
            if ($before[$path].sha256 -ne $after[$path].sha256) {
                throw "Relocated file hash differs: $path"
            }
        }
        $hostExecutable = Join-Path $run 'fantnel/Mizulune.FantnelHost.exe'
        Push-Location $run
        try {
            & $hostExecutable --health | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Relocated Fantnel --health failed' }
            & $hostExecutable --console-health | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Relocated Fantnel --console-health failed' }
        } finally {
            Pop-Location
        }
        return [ordered]@{ copiedFiles = $after.Count; hashesMatch = $true; hostHealth = $true }
    } finally {
        if (Test-Path -LiteralPath $run) {
            $baseResolved = (Resolve-Path -LiteralPath $base).Path.TrimEnd([char[]]'\/') + '\'
            $runResolved = (Resolve-Path -LiteralPath $run).Path
            if (-not $runResolved.StartsWith($baseResolved, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'Refusing to clean a relocation path outside build/portability-test'
            }
            Remove-Item -LiteralPath $runResolved -Recurse -Force
        }
    }
}

function Write-JsonReport($Value, [string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return }
    $full = [IO.Path]::GetFullPath($Path)
    $parent = Split-Path -Parent $full
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $temporary = $full + '.tmp'
    $json = $Value | ConvertTo-Json -Depth 8
    Set-Content -LiteralPath $temporary -Value $json -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $full -Force
}

$source = Test-SourcePortability
if ($SourceOnly) {
    $result = [ordered]@{
        schema = 'mizulune.distributability-report/v1'
        mode = 'source-only'
        source = $source
    }
    Write-JsonReport $result $ReportPath
    Write-Output "Source portability PASS ($($source.filesScanned) files)"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($DistDirectory) -or
    [string]::IsNullOrWhiteSpace($JarPath)) {
    throw 'DistDirectory and JarPath are required outside SourceOnly mode'
}
$DistDirectory = [IO.Path]::GetFullPath($DistDirectory)
$JarPath = [IO.Path]::GetFullPath($JarPath)

$distribution = Test-Distribution $DistDirectory
$jar = Test-Jar $JarPath
$relocation = Test-Relocation $DistDirectory
$result = [ordered]@{
    schema = 'mizulune.distributability-report/v1'
    mode = 'full'
    source = $source
    distribution = $distribution
    jar = $jar
    relocation = $relocation
}
Write-JsonReport $result $ReportPath
Write-Output "Distributability PASS: files=$($distribution.files), maxHook=$($distribution.maxHookBundled), embeddedJars=$($jar.embeddedRuntimeJars)"
