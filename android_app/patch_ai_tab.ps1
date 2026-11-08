$path = 'C:\Users\Kunal\Desktop\antigrvity\android_app\app\src\main\java\com\Kunal\stock\ui\SimpleSetupScreen.kt'
$lines = [System.IO.File]::ReadAllLines($path)
$out = [System.Collections.Generic.List[string]]::new()
foreach ($line in $lines) {
    # 1. Add AI Agent tab to tabs list
    if ($line -match 'val tabs = listOf\("Signal"') {
        $out.Add('    val tabs = listOf("Signal", "AI Agent", "Day Plan", "Setups", "Hero Zero", "Journal", "Risk Calc")')
    }
    # 2. Add AI Agent case in when block
    elseif ($line -match '0 -> SignalTab\(\)') {
        $out.Add('            0 -> SignalTab()')
        $out.Add('            1 -> AiAgentTab()')
    }
    elseif ($line -match '1 -> DayPlanTab\(\)') {
        $out.Add('            2 -> DayPlanTab()')
    }
    elseif ($line -match '2 -> SetupsTab\(\)') {
        $out.Add('            3 -> SetupsTab()')
    }
    elseif ($line -match '3 -> HeroZeroTab\(\)') {
        $out.Add('            4 -> HeroZeroTab()')
    }
    elseif ($line -match '4 -> JournalTab\(\)') {
        $out.Add('            5 -> JournalTab()')
    }
    elseif ($line -match '5 -> RiskCalcTab\(\)') {
        $out.Add('            6 -> RiskCalcTab()')
    }
    else {
        $out.Add($line)
    }
}
[System.IO.File]::WriteAllLines($path, $out)
Write-Host "Patched tabs and when-block"
