$envFile = Join-Path $PSScriptRoot '.env'

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile"
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        return
    }

    $pair = $line -split '=', 2
    if ($pair.Count -eq 2) {
        $name = $pair[0].Trim()
        $value = $pair[1].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

Write-Host 'Loaded environment variables from .env into current process.'
