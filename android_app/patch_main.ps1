$path = 'C:\Users\Kunal\Desktop\antigrvity\android_app\app\src\main\java\com\Kunal\stock\MainActivity.kt'
$lines = [System.IO.File]::ReadAllLines($path)
$out = [System.Collections.Generic.List[string]]::new()
foreach ($line in $lines) {
    if ($line -match 'import com\.Kunal\.stock\.ui\.BacktestScreen') {
        $out.Add($line)
        $out.Add('import com.Kunal.stock.ui.SimpleSetupScreen')
    } elseif ($line -match 'NavigationItem\("Option Lab"') {
        $out.Add($line)
        $out.Add('        NavigationItem("Setup Guide", "setup", Icons.Default.MenuBook)')
    } elseif ($line -match 'composable\("backtest"\) \{ BacktestScreen\(\) \}') {
        $out.Add($line)
        $out.Add('            composable("setup") { SimpleSetupScreen() }')
    } else {
        $out.Add($line)
    }
}
[System.IO.File]::WriteAllLines($path, $out)
Write-Host 'Done patching MainActivity'
