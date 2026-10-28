# Signs Veyron's exe files with the code signing certificate, so Windows Smart App Control and SmartScreen trust them.
# Unsigned, Smart App Control blocks Veyron.exe outright ("Smart App Control esti sovelluksen").
# Used by build.ps1 for out\app\Veyron\Veyron.exe and dist\Veyron.exe. See CODE_SIGNING.md.
#
# Needs signing.properties next to this script (never committed, see .gitignore), for example:
#   # the certificate in the Windows certificate store (Certum SimplySign, a USB token...): its SHA-1 thumbprint
#   thumbprint=0123456789ABCDEF0123456789ABCDEF01234567
#   # or a .pfx file instead of the thumbprint (its password is asked for, never stored here)
#   #pfx=C:\path\to\veyron.pfx
#   timestamp=http://time.certum.pl
# and signtool.exe (Windows SDK, "Windows SDK Signing Tools for Desktop Apps").
# Without signing.properties nothing is signed and the build goes on with a warning.
# Smart App Control trusts certificates from a trusted CA, RSA or ECC (Microsoft Learn, 2026-09). Azure Artifact Signing
# is not open to individuals in the EU, so Veyron uses a Certum code signing certificate (SimplySign cloud): its
# certificate appears in the Windows store while SimplySign Desktop is logged in, so thumbprint= is enough.
param([Parameter(Mandatory = $true)][string[]]$Files)
$ErrorActionPreference = "Stop"
$config = "$PSScriptRoot\signing.properties"
if (-not (Test-Path $config)) {
	Write-Warning "No signing.properties: $($Files -join ', ') stays unsigned (Smart App Control will block it)."
	return
}
$props = @{}
foreach ($line in Get-Content $config) {
	if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2].Trim() }
}
$signtool = $props["signtool"]
if (-not $signtool) {
	$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
		Where-Object { $_.FullName -match '\\x64\\' } | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $signtool -or -not (Test-Path $signtool)) { throw "signtool.exe not found: install the Windows SDK signing tools or set signtool= in signing.properties" }
$timestamp = $props["timestamp"]
if (-not $timestamp) { $timestamp = "http://time.certum.pl" }

$signArgs = @("sign", "/fd", "sha256", "/tr", $timestamp, "/td", "sha256", "/d", "Veyron")
if ($props["thumbprint"]) {
	$signArgs += @("/sha1", $props["thumbprint"])
} elseif ($props["pfx"]) {
	$password = Read-Host "Password for $($props["pfx"])" -AsSecureString
	$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))
	$signArgs += @("/f", $props["pfx"], "/p", $plain)
} else {
	throw "signing.properties needs thumbprint= or pfx="
}
foreach ($file in $Files) {
	& $signtool @signArgs $file
	if ($LASTEXITCODE -ne 0) { throw "Signing $file failed." }
	& $signtool verify /pa $file | Out-Null
	if ($LASTEXITCODE -ne 0) { throw "The signature on $file does not verify." }
	Write-Host "Signed $file"
}
