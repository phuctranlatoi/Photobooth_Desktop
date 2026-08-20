$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$appName = "PrettyBoothDesktop"
$version = "1.0.0"
$releaseDir = Join-Path $projectRoot "release"

$gradle = $null
if (Test-Path -LiteralPath (Join-Path $projectRoot "gradlew.bat")) {
    $gradle = Join-Path $projectRoot "gradlew.bat"
} else {
    $gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradleCommand) {
        $gradle = $gradleCommand.Source
    }
}

if (-not $gradle) {
    Write-Host "Chua tim thay Gradle."
    Write-Host "May build can co Gradle hoac Gradle Wrapper. May khach cai app KHONG can Gradle."
    Write-Host "Sau khi build xong, chi can gui file trong thu muc release sang may khac de cai."
    exit 1
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    Write-Host "Chua tim thay Java trong PATH."
    Write-Host "May build can JDK 17. May khach cai app KHONG can cai JDK."
    exit 1
}

$javaVersionText = (& java -version 2>&1) -join "`n"
if ($javaVersionText -notmatch 'version "17\.|version "18\.|version "19\.|version "20\.|version "21\.|version "22\.') {
    Write-Host "Java trong PATH khong phai JDK 17+."
    Write-Host "May build can JDK 17+. May khach cai app KHONG can cai JDK."
    Write-Host "Java hien tai:"
    Write-Host $javaVersionText
    exit 1
}

Write-Host "Dang doc danh sach task Gradle..."
$tasksOutput = & $gradle tasks --all --console=plain
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$taskNames = $tasksOutput | ForEach-Object {
    if ($_ -match "^([A-Za-z][A-Za-z0-9]*)\s") {
        $Matches[1]
    }
}

$packageTasks = @()
if ($taskNames -contains "packageExe") {
    $packageTasks += "packageExe"
}
if ($taskNames -contains "packageMsi") {
    $packageTasks += "packageMsi"
}
if ($packageTasks.Count -eq 0 -and $taskNames -contains "packageDistributionForCurrentOS") {
    $packageTasks += "packageDistributionForCurrentOS"
}

if ($packageTasks.Count -eq 0) {
    Write-Host "Khong tim thay task dong goi Windows trong Gradle project."
    exit 1
}

if ($packageTasks -contains "packageExe" -or $packageTasks -contains "packageMsi") {
    $hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -and
        (Get-Command light.exe -ErrorAction SilentlyContinue)
    if (-not $hasWix) {
        Write-Host "Chua tim thay WiX Toolset trong PATH."
        Write-Host "De build .exe/.msi installer, cai WiX Toolset tren may build:"
        Write-Host "  choco install wixtoolset -y"
        Write-Host "Sau do mo PowerShell moi va chay lai script."
        exit 1
    }
}

foreach ($task in $packageTasks) {
    Write-Host "Dang build installer bang task: $task"
    & $gradle $task
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

$binariesDir = Join-Path $projectRoot "build\compose\binaries"
$installers = Get-ChildItem -LiteralPath $binariesDir -Recurse -File -Include *.exe,*.msi |
    Sort-Object LastWriteTime -Descending

if (-not $installers -or $installers.Count -eq 0) {
    Write-Host "Build xong nhung chua tim thay file .exe/.msi. Kiem tra thu muc build\compose\binaries."
    exit 1
}

$copiedInstallers = @()
foreach ($installer in $installers) {
    $extension = $installer.Extension.ToLowerInvariant()
    $targetName = "$appName-$version-Windows-Setup$extension"
    if ($extension -eq ".msi") {
        $targetName = "$appName-$version-Windows-Installer$extension"
    }
    $targetPath = Join-Path $releaseDir $targetName
    Copy-Item -LiteralPath $installer.FullName -Destination $targetPath -Force
    $copiedInstallers += Get-Item -LiteralPath $targetPath
}

$hashFile = Join-Path $releaseDir "SHA256SUMS.txt"
$hashLines = $copiedInstallers | ForEach-Object {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
    "$($hash.Hash)  $($_.Name)"
}
$hashLines | Set-Content -LiteralPath $hashFile -Encoding UTF8

Write-Host ""
Write-Host "Da tao ban cai dat Windows:"
$copiedInstallers | ForEach-Object { Write-Host $_.FullName }
Write-Host ""
Write-Host "Gui file .exe hoac .msi trong thu muc release sang may khac."
Write-Host "May khac chi can double-click de cai app, khong can Android Studio, Gradle hay source code."
Write-Host "Checksum: $hashFile"
