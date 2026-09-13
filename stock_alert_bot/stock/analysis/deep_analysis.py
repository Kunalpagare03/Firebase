from analysis.indicators import calculate_rsi, calculate_macd
from analysis.volume_analysis import volume_spike
from analysis.price_action import detect_trend
from analysis.chart_patterns import detect_vcp

def deep_drive_analysis(df):
    """
    Perform full analysis: RSI, MACD, Volume, Trend, Patterns
    """
    df = calculate_rsi(df)
    df = calculate_macd(df)
    df = volume_spike(df)

    trend = detect_trend(df)
    vcp = detect_vcp(df)

    latest = df.iloc[-1]
    report = {
        "Trend": trend,
        "RSI": latest['RSI'],
        "MACD": latest['MACD'],
        "Signal": latest['Signal'],
        "VolumeSpike": latest['VolumeSpike'],
        "Pattern": vcp
    }
    return report
