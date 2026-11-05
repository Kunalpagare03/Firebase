$path = 'C:\Users\Kunal\Desktop\antigrvity\android_app\app\src\main\java\com\Kunal\stock\MainActivity.kt'
$lines = [System.IO.File]::ReadAllLines($path)
$out = [System.Collections.Generic.List[string]]::new()
foreach ($line in $lines) {
    # Fix missing comma: Science) -> Science),
    if ($line -match 'NavigationItem\("Option Lab", "backtest", Icons\.Default\.Science\)$') {
        $out.Add($line + ',')
    } else {
        $out.Add($line)
    }
}
[System.IO.File]::WriteAllLines($path, $out)
Write-Host 'Fixed comma on Option Lab line'
