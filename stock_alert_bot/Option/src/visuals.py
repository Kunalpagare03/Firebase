# src/visuals.py
import matplotlib.pyplot as plt

def plot_oi(summary):
    plt.figure(figsize=(10,5))
    plt.plot(summary['captured_at'], summary['call_oi'], color='red', label='Call OI')
    plt.plot(summary['captured_at'], summary['put_oi'], color='blue', label='Put OI')
    plt.title("Open Interest (OI) Trend")
    plt.xlabel("Timestamp"); plt.ylabel("OI Contracts")
    plt.legend(); plt.grid(True); plt.show()

def plot_iv(summary):
    plt.figure(figsize=(10,5))
    plt.plot(summary['captured_at'], summary['call_iv'], color='red', label='Call IV')
    plt.plot(summary['captured_at'], summary['put_iv'], color='blue', label='Put IV')
    plt.title("Implied Volatility (IV) Trend")
    plt.xlabel("Timestamp"); plt.ylabel("IV (%)")
    plt.legend(); plt.grid(True); plt.show()

def plot_max_pain(summary):
    plt.figure(figsize=(10,5))
    plt.scatter(summary['spot_price'], summary['call_oi'], color='red', label='Call OI')
    plt.scatter(summary['spot_price'], summary['put_oi'], color='blue', label='Put OI')
    plt.title("Max Pain Approximation")
    plt.xlabel("Spot Price"); plt.ylabel("OI Contracts")
    plt.legend(); plt.grid(True); plt.show()
