# Pretty Booth Desktop

Desktop rewrite for the photobooth app. This folder is intentionally separate
from the Android project at `../Photobooth`.

## Current flow

- Choose print size/layout/effect.
- Choose print copy count and confirm payment manually.
- 3-second countdown for each shot.
- Capture from the default Windows webcam/capture card when available.
- Import local JPEG/PNG sources for capture testing or fallback.
- Crop every capture to the selected layout photo slot ratio.
- Select photos for print while keeping all captured photos for album upload.
- Add custom transparent PNG frames.
- Render the final print image with the selected frame overlay.
- Upload JPEG originals plus the final print to Cloudinary.
- Create/finalize/complete the web album API and show a real QR code.
- Save completed sessions locally.
- Optionally send the rendered print file to Windows Print.

The real PayOS gateway is still an adapter point. The booth flow, webcam/capture
card still capture, layout rendering, Cloudinary/web album upload, QR delivery,
custom frames, local session log, and optional Windows print path are wired for
desktop.

## Runtime folders

Installed app data is stored here:

```powershell
%LOCALAPPDATA%\PrettyBoothDesktop
```

Important subfolders:

- `%LOCALAPPDATA%\PrettyBoothDesktop\data\frames`
- `%LOCALAPPDATA%\PrettyBoothDesktop\data\sessions`
- `%LOCALAPPDATA%\PrettyBoothDesktop\data\output`

The app reads `.env` in this order:

1. `%LOCALAPPDATA%\PrettyBoothDesktop\.env`
2. `.env` in the current project folder while running in development
3. System environment variables

## Env

Copy `.env.example` to the AppData folder for an installed app:

```powershell
New-Item -ItemType Directory -Path "$env:LOCALAPPDATA\PrettyBoothDesktop" -Force
Copy-Item .env.example "$env:LOCALAPPDATA\PrettyBoothDesktop\.env"
notepad "$env:LOCALAPPDATA\PrettyBoothDesktop\.env"
```

For development, you can also copy it to the project root:

```powershell
Copy-Item .env.example .env
```

Do not commit `.env`. It contains booth/cloud keys.

Set `ENABLE_SYSTEM_PRINT=true` only when the printer is ready. When it is false,
the app still renders the print file locally and uploads the album.

## Check machine readiness

```powershell
Set-Location E:\HK1_2026_2027\PhotoboothDesktop
.\check-desktop-readiness.ps1
```

This checks AppData, `.env`, Cloudinary/web album keys, Windows print flag, JDK,
Gradle, and WiX Toolset.

## Run in development

The build machine needs JDK 17+ and Gradle.

```powershell
Set-Location E:\HK1_2026_2027\PhotoboothDesktop
gradle run
```

If this project later gets a Gradle Wrapper, use:

```powershell
.\gradlew.bat run
```

## Build Windows app

This creates a self-contained Windows installer. The computer that builds the
app needs JDK 17+, Gradle or Gradle Wrapper, and WiX Toolset. The customer
computer only needs the final `.exe` or `.msi` file.

```powershell
Set-Location E:\HK1_2026_2027\PhotoboothDesktop
.\build-windows-installer.ps1
```

Installers are copied to:

```powershell
E:\HK1_2026_2027\PhotoboothDesktop\release
```

Send the generated `.exe` or `.msi` to another Windows machine. That machine does
not need Android Studio, Gradle, JDK, or source code.

If PowerShell blocks the script for this session:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\build-windows-installer.ps1
```

## Build from GitHub

If this desktop project is pushed to GitHub as its own repository, run the
`Build Windows Installer` workflow from the Actions tab. GitHub will build the
installer and expose it as a downloadable artifact.
