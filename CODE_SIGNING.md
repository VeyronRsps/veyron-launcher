# Code signing policy

The Windows files built from this repository are signed with a Certum Open Source Code Signing certificate, so
Windows (Smart App Control, SmartScreen) can verify who published them and that they have not been changed.

## What is signed

Only files built from this repository by `build.ps1`:

- `Veyron.exe`, the installer (`installer/VeyronSetup.cs`)
- `Veyron\Veyron.exe` inside `Veyron-Launcher.zip`, the launcher's start program (made by `jpackage` from `launcher/src`)

Nothing else is signed with this certificate: not the game client the launcher downloads, and no third-party software.
The Java runtime inside the zip keeps its own signatures from Eclipse Adoptium and Microsoft.

## How

- The project maintainer (Topi, topi.nurmela@outlook.com) builds a release with `build.ps1` from a tagged commit of this repository.
- `sign.ps1` signs with `signtool` (SHA-256, RFC 3161 timestamp from Certum). The private key stays in Certum's
  SimplySign cloud and is unlocked with the maintainer's two-factor login for each signing session.
- `signing.properties` (the certificate's thumbprint) is local only and never committed.

## Checking a download

Right-click the file > Properties > Digital Signatures, or:

```powershell
Get-AuthenticodeSignature .\Veyron.exe
```

The signer is shown as "Open Source Developer" with the maintainer's name.

## Reporting a problem

If you find a file signed with this certificate that was not built from this repository, open an issue.
