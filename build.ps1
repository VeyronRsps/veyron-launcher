# Builds every file players download, straight from this repository, into dist\:
#   Veyron.jar           the launcher (runs on any Java 17+)
#   Veyron-Launcher.zip  the launcher with its own Java (jpackage), Veyron\Veyron.exe inside
#   Veyron.exe           the one-file installer (installer\VeyronSetup.cs with the zip inside)
# The exe files are signed by sign.ps1 when signing.properties exists (see CODE_SIGNING.md).
# Needs a JDK 17+ (JAVA_HOME, or java on the PATH) and Windows' .NET Framework 4 C# compiler.
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Split-Path (Split-Path (Get-Command javac).Source) }
$out = "$root\out"; $dist = "$root\dist"
foreach ($d in $out, $dist) { if (Test-Path $d) { Remove-Item $d -Recurse -Force } ; New-Item -ItemType Directory $d | Out-Null }

Write-Host "Compiling the launcher..."
$classes = "$out\classes"
New-Item -ItemType Directory $classes | Out-Null
$sources = @(Get-ChildItem "$root\launcher\src" -Recurse -Filter *.java | ForEach-Object FullName)
& "$jdk\bin\javac.exe" --release 8 -nowarn -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Copy-Item "$root\launcher\resources\*" $classes
& "$jdk\bin\jar.exe" --create --file "$dist\Veyron.jar" --main-class veyron.launcher.VeyronLauncher -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "Packaging Veyron.exe with its own Java..."
$stage = "$out\stage"
New-Item -ItemType Directory $stage | Out-Null
Copy-Item "$dist\Veyron.jar" "$stage\VeyronLauncher.jar"
# jpackage's own jlink options without --strip-native-commands: the launcher starts the client with the runtime's javaw.exe
& "$jdk\bin\jpackage.exe" --type app-image --name Veyron --app-version 1.0 --vendor Veyron `
	--input $stage --main-jar VeyronLauncher.jar --main-class veyron.launcher.VeyronLauncher `
	--jlink-options "--strip-debug --no-man-pages --no-header-files" `
	--icon "$root\icons\veyron_v.ico" --dest "$out\app"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
& "$root\sign.ps1" -Files "$out\app\Veyron\Veyron.exe"
Compress-Archive -Path "$out\app\Veyron" -DestinationPath "$dist\Veyron-Launcher.zip"

Write-Host "Building the installer..."
$work = "$out\installer"
New-Item -ItemType Directory $work | Out-Null
Copy-Item "$dist\Veyron-Launcher.zip" "$work\launcher.zip"
# the version a Veyron.exe carries: a new launcher zip means a new install on the player's machine
$version = (Get-FileHash "$dist\Veyron-Launcher.zip" -Algorithm SHA256).Hash.Substring(0, 16).ToLower()
[IO.File]::WriteAllText("$work\version.txt", $version, [Text.Encoding]::ASCII)
$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
& "$fw\csc.exe" /nologo /optimize+ /target:winexe "/out:$dist\Veyron.exe" "/win32icon:$root\icons\veyron.ico" `
	"/resource:$work\launcher.zip,launcher.zip" "/resource:$work\version.txt,version.txt" `
	"/r:$fw\System.IO.Compression.dll" "/r:$fw\System.Windows.Forms.dll" "/r:$fw\System.Drawing.dll" `
	"$root\installer\VeyronSetup.cs"
if ($LASTEXITCODE -ne 0) { throw "csc failed" }
& "$root\sign.ps1" -Files "$dist\Veyron.exe"

Get-ChildItem $dist | ForEach-Object { Write-Host ("{0,-22} {1,8:N1} MB  SHA-256 {2}" -f $_.Name, ($_.Length / 1MB), (Get-FileHash $_.FullName).Hash) }
