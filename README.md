# Veyron launcher

The launcher and Windows installer for the Veyron game client. It is free and open source (MIT licence).

What it does:

1. **Veyron.exe (installer)** unpacks the launcher to `%LOCALAPPDATA%\Veyron\launcher`, puts a Veyron shortcut on the
   desktop and in the Start menu, and starts the launcher. Running a newer Veyron.exe installs the newer launcher.
2. **The launcher** asks the Veyron game server for the current client's SHA-256 (`client.sha256`). When the copy in
   `%USERPROFILE%\.veyron\client.jar` differs, it downloads `client.jar`, checks the hash and starts the client with
   its own Java. When the server can't be reached it starts the copy it already has.

The launcher is plain Java (no libraries). The installer is one C# file built with the .NET Framework 4 compiler that
ships with Windows.

## Downloads

Built from this repository and published on the Veyron website:

| File | What it is |
|---|---|
| `Veyron.exe` | One-file installer (signed) |
| `Veyron-Launcher.zip` | The launcher with its own Java; run `Veyron\Veyron.exe` (signed) |
| `Veyron.jar` | The launcher for players who have Java 17 or newer |

## Building

Requirements: Windows, a JDK 17 or newer (`JAVA_HOME` or `javac` on the `PATH`), PowerShell.

```powershell
.\build.ps1
```

The files land in `dist\`. With a code signing certificate set up (see [CODE_SIGNING.md](CODE_SIGNING.md)) the exe
files are signed during the build; without one they are built unsigned and the build says so.

## Layout

| Path | Contents |
|---|---|
| `launcher/src` | `veyron.launcher.VeyronLauncher` |
| `launcher/resources` | Icons, splash image and `veyron_launcher.properties` (update address, client start options) |
| `installer/VeyronSetup.cs` | The installer |
| `icons/` | Windows icons for the exe files |
| `build.ps1`, `sign.ps1` | Build and signing scripts |

## Maintainer

Topi, topinurmela99@gmail.com (the Veyron launcher's maintainer and the holder of its code signing certificate).

## Licence

MIT, see [LICENSE](LICENSE).
