$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$appDataRoot = if ($env:LOCALAPPDATA) {
    Join-Path $env:LOCALAPPDATA "PrettyBoothDesktop"
} else {
    Join-Path $env:USERPROFILE "AppData\Local\PrettyBoothDesktop"
}

function Write-Check {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail
    )

    $prefix = if ($Ok) { "[OK]" } else { "[WARN]" }
    Write-Host "$prefix $Name - $Detail"
}

Write-Host "Pretty Booth Desktop readiness"
Write-Host "Project: $projectRoot"
Write-Host "AppData: $appDataRoot"
Write-Host ""

New-Item -ItemType Directory -Path $appDataRoot -Force | Out-Null
Write-Check "AppData folder" (Test-Path -LiteralPath $appDataRoot) "data, frames, sessions, output, and installed .env live here"

$workingEnv = Join-Path $projectRoot ".env"
$appEnv = Join-Path $appDataRoot ".env"
$envPath = $null
if (Test-Path -LiteralPath $appEnv) {
    $envPath = $appEnv
} elseif (Test-Path -LiteralPath $workingEnv) {
    $envPath = $workingEnv
}

Write-Check ".env" ($null -ne $envPath) ($(if ($envPath) { $envPath } else { "copy .env.example to $appEnv for installed app" }))

$envMap = @{}
if ($envPath) {
    Get-Content -LiteralPath $envPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key = $line.Substring(0, $line.IndexOf("=")).Trim()
            $value = $line.Substring($line.IndexOf("=") + 1).Trim().Trim('"').Trim("'")
            $envMap[$key] = $value
        }
    }
}

$requiredAlbumKeys = @(
    "BOOTH_API_KEY",
    "WEB_ALBUM_BASE_URL",
    "CLOUDINARY_CLOUD_NAME",
    "CLOUDINARY_UPLOAD_PRESET"
)
$missingAlbumKeys = @($requiredAlbumKeys | Where-Object { -not $envMap.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($envMap[$_]) -or $envMap[$_] -like "replace_with*" })
Write-Check "Album upload config" ($missingAlbumKeys.Count -eq 0) ($(if ($missingAlbumKeys.Count -eq 0) { "Cloudinary + web album keys are present" } else { "missing: $($missingAlbumKeys -join ', ')" }))

$printEnabled = $false
if ($envMap.ContainsKey("ENABLE_SYSTEM_PRINT")) {
    $printEnabled = @("1", "true", "yes", "y", "on") -contains $envMap["ENABLE_SYSTEM_PRINT"].ToLowerInvariant()
}
Write-Check "Windows print" $printEnabled ($(if ($printEnabled) { "app will send rendered print file to Windows Print" } else { "disabled; set ENABLE_SYSTEM_PRINT=true when booth printer is ready" }))

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($javaCommand) {
    $javaVersionText = (& cmd /c "java -version 2>&1") -join "`n"
    $isJdk17 = $javaVersionText -match 'version "1[7-9]\.|version "2[0-9]\.'
    Write-Check "JDK 17+" $isJdk17 ($javaVersionText.Split("`n")[0])
} else {
    Write-Check "JDK 17+" $false "java was not found in PATH"
}

$gradle = $null
if (Test-Path -LiteralPath (Join-Path $projectRoot "gradlew.bat")) {
    $gradle = Join-Path $projectRoot "gradlew.bat"
} else {
    $gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradleCommand) {
        $gradle = $gradleCommand.Source
    }
}
Write-Check "Gradle" ($null -ne $gradle) ($(if ($gradle) { $gradle } else { "install Gradle or add Gradle Wrapper before building installer" }))

$hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -and (Get-Command light.exe -ErrorAction SilentlyContinue)
Write-Check "WiX Toolset" ([bool]$hasWix) ($(if ($hasWix) { "installer build tools are in PATH" } else { "needed for .exe/.msi packaging on Windows build machine" }))

Write-Host ""
Write-Host "Run app in dev:"
Write-Host "  gradle run"
Write-Host ""
Write-Host "Build installer:"
Write-Host "  .\build-windows-installer.ps1"
