def volume_spike(df, threshold=2.0):
    """
    Detect volume spikes: today's volume > threshold * average volume
    """
    avg_vol = df['Volume'].rolling(20).mean()
    df['VolumeSpike'] = df['Volume'] > threshold * avg_vol
    return df
