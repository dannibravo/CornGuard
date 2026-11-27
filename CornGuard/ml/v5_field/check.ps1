# Shows v5 training progress. Works from any folder.
#   .\ml\v5_field\check.ps1            one-time summary
#   .\ml\v5_field\check.ps1 -Watch 5   clear the screen and refresh every 5 s (Ctrl+C to stop)
param([double]$Watch = 0)
$ml = Split-Path -Parent $PSScriptRoot
$py = Join-Path $ml ".venv\Scripts\python.exe"
$script = Join-Path $PSScriptRoot "check_progress.py"
if ($Watch -le 0) { & $py $script; return }
if ($Watch -lt 0.5) { $Watch = 0.5 }
while ($true) {
    $out = & $py $script | Out-String   # read first, then clear, so the screen doesn't flicker blank
    Clear-Host
    Write-Host ("{0}  (every {1}s, Ctrl+C to stop)" -f (Get-Date -Format "HH:mm:ss"), $Watch)
    Write-Host $out
    Start-Sleep -Milliseconds ([int]($Watch * 1000))
}
