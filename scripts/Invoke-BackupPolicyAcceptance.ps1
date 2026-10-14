# Manual cloud/local-transport acceptance only. Requires a deliberately disposable Android AVD.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$AppApk,
    [Parameter(Mandatory=$true)][string]$TestApk,
    [Parameter(Mandatory=$true)][string]$Transport,
    [Parameter(Mandatory=$true)][string]$ReportDirectory,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$Package = 'com.abdev.partituraspdf'
$TestPackage = "$Package.test"
$Runner = "$TestPackage/com.abdev.partituraspdf.data.backup.BackupPolicyAcceptanceRunner"
$Token = [guid]::NewGuid().ToString()
$OriginalTransport = $null
$TransportChanged = $false
$BackupEnabledChanged = $false
$AutoRestoreChanged = $false
$OriginalAutoRestore = $null
$ConsentGiven = $false
$AllowedLocalTransports = @('android/com.android.internal.backup.LocalTransport',
    'com.android.localtransport/.LocalTransport', 'com.android.localtransport/.LocalTransportService')

if ($Serial -notmatch '^emulator-\d+$') { throw 'Only an explicit emulator-NNNN serial is permitted.' }
if ($Transport -notin $AllowedLocalTransports) { throw 'Only the listed Android local test transports are permitted; no cloud-account transport.' }
$AppApk = (Resolve-Path -LiteralPath $AppApk).Path
$TestApk = (Resolve-Path -LiteralPath $TestApk).Path
if ((Test-Path -LiteralPath $ReportDirectory) -and (Get-ChildItem -LiteralPath $ReportDirectory)) {
    throw 'Choose an empty/new report directory; evidence will not be overwritten.'
}
$null = New-Item -ItemType Directory -Path $ReportDirectory -Force
$ReportDirectory = (Resolve-Path -LiteralPath $ReportDirectory).Path
$Log = Join-Path $ReportDirectory 'commands.log'

function Invoke-AdbChecked([string[]]$Arguments) {
    $output = & $Adb -s $Serial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    Add-Content -LiteralPath $Log -Value ("adb -s $Serial " + ($Arguments -join ' ') + "`r`n$text`r`nexit=$exitCode")
    if ($exitCode -ne 0) { throw "adb command failed ($exitCode): $($Arguments -join ' ')" }
    return $text
}

function Assert-Emulator {
    if ((Invoke-AdbChecked @('get-state')) -ne 'device') { throw 'Selected emulator is not ready.' }
    if ((Invoke-AdbChecked @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Selected device is not an Android emulator.' }
    if ((Invoke-AdbChecked @('shell','getprop','ro.hardware')) -notin @('ranchu','goldfish')) {
        throw 'Only supported Android emulator hardware is permitted.'
    }
}

function Invoke-Fixture([string]$Action) {
    Assert-Emulator
    $registered = Invoke-AdbChecked @('shell','pm','list','instrumentation')
    if (-not $registered.Contains($Runner)) { throw 'Install the explicitly built acceptance-runner APK, not the normal JUnit APK.' }
    $null = Invoke-AdbChecked @('shell','run-as',$Package,'mkdir','-p','cache')
    # Token is a generated UUID, never arbitrary shell text; marker is outside backup domains.
    $null = Invoke-AdbChecked @('shell','run-as',$Package,'sh','-c',"'echo $Token > cache/c06-backup-consent'")
    $result = Invoke-AdbChecked @('shell','am','instrument','-w','-e','action',$Action,
        '-e','confirm','DISPOSABLE_EMULATOR_ONLY','-e','token',$Token,$Runner)
    $result | Set-Content -LiteralPath (Join-Path $ReportDirectory "$Action.txt")
    if ($result -notmatch 'policy_result=PASS' -or $result -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw "Fixture $Action failed or did not execute; inspect its report."
    }
}

Assert-Emulator
$identity = [ordered]@{
    serial = $Serial; token = $Token; transport = $Transport
    model = Invoke-AdbChecked @('shell','getprop','ro.product.model')
    api = Invoke-AdbChecked @('shell','getprop','ro.build.version.sdk')
    fingerprint = Invoke-AdbChecked @('shell','getprop','ro.build.fingerprint')
    appApkSha256 = (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash
    testApkSha256 = (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash
    mode = 'LOCAL_CLOUD_STYLE_BACKUP_NOT_D2D'; startedUtc = [DateTime]::UtcNow.ToString('o')
}
if ([int]$identity.api -lt 26) { throw 'API 26 or newer is required.' }
$identity | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory 'identity.json')
$transports = Invoke-AdbChecked @('shell','bmgr','list','transports')
$listed = @($transports -split '\r?\n' | ForEach-Object { $_.Trim().TrimStart('*').Trim() })
if ($Transport -notin $listed) { throw 'Required local transport unavailable: BLOCKED; no destructive operations performed.' }
$selected = @($transports -split '\r?\n' | Where-Object { $_ -match '^\s*\*' })
if ($selected.Count -ne 1) { throw 'Cannot identify original selected transport; stop safely.' }
$OriginalTransport = $selected[0].Trim().TrimStart('*').Trim()
$OriginalAutoRestore = Invoke-AdbChecked @('shell','settings','get','secure','backup_auto_restore')
if ($OriginalAutoRestore -notin @('0','1')) { throw 'Cannot safely record auto-restore state; BLOCKED before destructive operations.' }
Write-Host ($identity | ConvertTo-Json)
Write-Host 'THIS ERASES THE TEST APP AND ITS LOCAL TEST BACKUP ON THE SELECTED DISPOSABLE AVD.'
$phrase = "ERASE ONLY $Serial $Package"
if ((Read-Host "Confirm this is a disposable AVD with no user data. Type exactly: $phrase") -cne $phrase) {
    throw 'Destructive consent not provided.'
}
$ConsentGiven = $true

try {
    Assert-Emulator
    $null = Invoke-AdbChecked @('shell','bmgr','autorestore','false'); $AutoRestoreChanged = $true
    $enabled = Invoke-AdbChecked @('shell','bmgr','enabled')
    if ($enabled -match 'currently disabled') {
        $null = Invoke-AdbChecked @('shell','bmgr','enable','true'); $BackupEnabledChanged = $true
    } elseif ($enabled -notmatch 'currently enabled') { throw 'Unknown backup manager state.' }
    $selection = Invoke-AdbChecked @('shell','bmgr','transport',$Transport)
    if ($selection -notmatch 'Selected transport') { throw 'Transport selection did not succeed.' }
    $TransportChanged = $true
    $null = Invoke-AdbChecked @('shell','bmgr','wipe',$Transport,$Package)
    foreach ($name in @($TestPackage,$Package)) {
        $installed = Invoke-AdbChecked @('shell','pm','list','packages',$name)
        if (($installed -split '\r?\n') -contains "package:$name") {
            $null = Invoke-AdbChecked @('uninstall',$name)
        }
    }
    $null = Invoke-AdbChecked @('install','-t',$AppApk)
    $null = Invoke-AdbChecked @('install','-t',$TestApk)
    Invoke-Fixture 'seed'
    $null = Invoke-AdbChecked @('shell','am','force-stop',$Package)
    $backup = Invoke-AdbChecked @('shell','bmgr','backupnow',$Package)
    $backup | Set-Content -LiteralPath (Join-Path $ReportDirectory 'backup.txt')
    if ($backup -notmatch ('Package\s+' + [regex]::Escape($Package) + '\s+with result: Success') -or
        $backup -notmatch 'Backup finished with result: Success') { throw 'Backup did not report actual success.' }
    $sets = Invoke-AdbChecked @('shell','bmgr','list','sets')
    $sets | Set-Content -LiteralPath (Join-Path $ReportDirectory 'restore-sets.txt')
    $matches = [regex]::Matches($sets, '(?m)^\s*([0-9a-fA-F]+)\s*:')
    if ($matches.Count -ne 1) { throw 'Need exactly one local restore set; do not guess a token.' }
    $restoreToken = $matches[0].Groups[1].Value
    Assert-Emulator
    $null = Invoke-AdbChecked @('uninstall',$TestPackage)
    $null = Invoke-AdbChecked @('uninstall',$Package)
    $null = Invoke-AdbChecked @('install','-t',$AppApk)
    $null = Invoke-AdbChecked @('install','-t',$TestApk)
    # Restore-at-install is disabled: manual restore must be the operation that brings preferences back.
    $null = Invoke-AdbChecked @('shell','run-as',$Package,'mkdir','-p','files')
    $privateFiles = Invoke-AdbChecked @('shell','run-as',$Package,'find','files','-type','f')
    if ($privateFiles -match 'app_preferences\.preferences_pb|c06-backup-unrelated') {
        throw 'Unexpected private fixture data before manual restore.'
    }
    $restore = Invoke-AdbChecked @('shell','bmgr','restore',$restoreToken,$Package)
    $restore | Set-Content -LiteralPath (Join-Path $ReportDirectory 'restore.txt')
    if ($restore -notmatch 'restoreFinished:\s*0') { throw 'Restore did not report actual success.' }
    $null = Invoke-AdbChecked @('shell','am','force-stop',$Package)
    Invoke-Fixture 'verify'
    'PASS: local cloud-style backup/restore only. D2D NOT RUN.' |
        Set-Content -LiteralPath (Join-Path $ReportDirectory 'result.txt')
} catch {
    ("FAIL/BLOCKED: " + $_.Exception.Message + '. No backup/restore PASS.') |
        Set-Content -LiteralPath (Join-Path $ReportDirectory 'result.txt')
    throw
} finally {
    # Restore only the settings we changed, and only on the still-verified selected emulator.
    if ($ConsentGiven) {
        try {
            Assert-Emulator
            if ($TransportChanged) { $null = Invoke-AdbChecked @('shell','bmgr','transport',$OriginalTransport) }
            if ($BackupEnabledChanged) { $null = Invoke-AdbChecked @('shell','bmgr','enable','false') }
            if ($AutoRestoreChanged) {
                $restoreSetting = if ($OriginalAutoRestore -eq '1') { 'true' } else { 'false' }
                $null = Invoke-AdbChecked @('shell','bmgr','autorestore',$restoreSetting)
            }
        } catch {
            ("FAIL/BLOCKED restoring emulator settings: " + $_.Exception.Message + '. No overall PASS.') |
                Set-Content -LiteralPath (Join-Path $ReportDirectory 'result.txt')
            throw
        }
    }
}
Write-Host "PASS local backup/restore; evidence: $ReportDirectory; D2D NOT RUN."
