Write-Host "Building portable version..."
gradle createReleaseDistributable

$dest = "build\compose\binaries\main-release\app\LeSouvenirDesktop"
$appDest = "$dest\app"

Write-Host "Copying EDSDK..."
Copy-Item -Recurse -Force EDSDK_64 $dest\
Copy-Item -Recurse -Force EDSDK_64 $appDest\

Write-Host "Copying bin folder (ffmpeg)..."
Copy-Item -Recurse -Force bin $dest\
Copy-Item -Recurse -Force bin $appDest\

Write-Host "Copying configuration files..."
if (Test-Path ".env") {
    Copy-Item -Force .env $dest\
    Write-Host "   Copied .env"
} else {
    Write-Host "   WARNING: .env not found!"
}

if (Test-Path "serviceAccountKey.json") {
    Copy-Item -Force serviceAccountKey.json $dest\
    Write-Host "   Copied serviceAccountKey.json"
} else {
    Write-Host "   WARNING: serviceAccountKey.json not found! Firebase will fail."
}

Write-Host "Copying local data (frames, covers, settings)..."
$localDataPath = "$env:LOCALAPPDATA\LeSouvenirDesktop\data"
if (Test-Path $localDataPath) {
    # Remove old data directories to prevent leftovers from previous builds
    if (Test-Path "$dest\data") { Remove-Item -Recurse -Force "$dest\data" }
    if (Test-Path "$appDest\data") { Remove-Item -Recurse -Force "$appDest\data" }

    # Create the data directories in destination
    New-Item -ItemType Directory -Force -Path "$dest\data" > $null
    New-Item -ItemType Directory -Force -Path "$appDest\data" > $null

    # Only copy necessary folders, exclude output and sessions
    $foldersToCopy = @("config", "covers", "frames")
    foreach ($folder in $foldersToCopy) {
        $sourcePath = "$localDataPath\$folder"
        if (Test-Path $sourcePath) {
            Copy-Item -Recurse -Force $sourcePath "$dest\data\"
            Copy-Item -Recurse -Force $sourcePath "$appDest\data\"
            Write-Host "   [OK] Copied data\$folder"
            
            if ($folder -eq "covers") {
                $events = Get-ChildItem -Path $sourcePath -Filter "*.png" | Select-Object -ExpandProperty BaseName
                if ($events) {
                    Write-Host "      -> Các sự kiện (bundle) được copy:"
                    foreach ($evt in $events) {
                        Write-Host "         - $evt"
                    }
                }
            }
            if ($folder -eq "frames") {
                $frameCount = (Get-ChildItem -Path $sourcePath -Recurse -Filter "*.png").Count
                Write-Host "      -> Đã copy tổng cộng $frameCount khung ảnh."
            }
        }
    }
} else {
    Write-Host "   WARNING: Local data folder not found in AppData!"
}

Write-Host "Creating .portable flag..."
New-Item -ItemType File -Force -Path "$dest\.portable" > $null
New-Item -ItemType File -Force -Path "$appDest\.portable" > $null

Write-Host ""
Write-Host "PORTABLE BUILD COMPLETE!"
Write-Host "=========================================================="
Write-Host "You can now copy the folder at:"
Write-Host "E:\HK1_2026_2027\PhotoboothDesktop\$dest"
Write-Host "to your Mini PC!"
