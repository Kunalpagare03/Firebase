$path = 'C:\Users\Kunal\Desktop\antigrvity\android_app\app\src\main\java\com\Kunal\stock\ui\SimpleSetupScreen.kt'
$content = [System.IO.File]::ReadAllText($path)
$content = $content -replace 'progress = \{ v\.confidence / 100f \}', 'progress = v.confidence / 100f'
[System.IO.File]::WriteAllText($path, $content)
Write-Host "Fixed LinearProgressIndicator"
