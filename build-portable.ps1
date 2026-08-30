Write-Host "Building portable version..."
gradle createReleaseDistributable

$dest = "build\compose\binaries\main-release\app\PrettyBoothDesktop"
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

Write-Host ""
Write-Host "PORTABLE BUILD COMPLETE!"
Write-Host "=========================================================="
Write-Host "You can now copy the folder at:"
Write-Host "E:\HK1_2026_2027\PhotoboothDesktop\$dest"
Write-Host "to your Mini PC!"
