# Windows PowerShell 5.1 or PowerShell 7. Build first, then launch with console input.
param(
    [string] $Project = '.',
    [string] $Module = ':',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GameArgs = @()
)
$ErrorActionPreference = 'Stop'
if ($Module -ne ':' -and $Module -notmatch '^(:[A-Za-z0-9_-]+)+$') {
    throw 'Module must be : or a path such as :game'
}
$task = if ($Module -eq ':') { ':canopyLaunchManifest' } else { "${Module}:canopyLaunchManifest" }
$manifest = [IO.Path]::GetTempFileName()
$originalLocation = Get-Location
$originalJavaHome = $env:JAVA_HOME
$exitCode = 1
try {
    Set-Location -LiteralPath $Project
    if (-not (Test-Path -LiteralPath '.\gradlew.bat')) { throw 'No Gradle wrapper in project directory' }
    & .\gradlew.bat '--console=plain' '-I' "$PSScriptRoot\launcher.gradle.kts" "-PcanopyLaunchManifest=$manifest" $task
    if ($LASTEXITCODE -ne 0) { $exitCode = $LASTEXITCODE }
    else {
        $fields = @(Get-Content -LiteralPath $manifest -Encoding UTF8)
        if ($fields.Count -ne 3) { throw 'Invalid launcher manifest' }
        Set-Location -LiteralPath $fields[0]
        $env:JAVA_HOME = $fields[1]
        & $fields[2] @GameArgs
        $exitCode = $LASTEXITCODE
    }
}
finally {
    Remove-Item -LiteralPath $manifest -ErrorAction SilentlyContinue
    Set-Location -LiteralPath $originalLocation.Path
    $env:JAVA_HOME = $originalJavaHome
}
exit $exitCode
