import datetime
import plotly.graph_objects as go
import webbrowser
import os
import pandas as pd

def generate_html_report(results, stock_data, filename_prefix="analysis_report"):
    """
    Generate an HTML report with metrics, candlestick charts,
    RSI, MACD, and Volume bars. Auto-opens in browser.
    """
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"{filename_prefix}_{timestamp}.html"

    html = f"""
    <html>
    <head>
        <title>Stock Analysis Report</title>
        <style>
            body {{ font-family: Arial, sans-serif; margin: 20px; }}
            h1 {{ color: #2c3e50; }}
            table {{ border-collapse: collapse; width: 100%; margin-bottom: 30px; }}
            th, td {{ border: 1px solid #ddd; padding: 8px; text-align: center; }}
            th {{ background-color: #34495e; color: white; }}
            tr:nth-child(even) {{ background-color: #f2f2f2; }}
            .chart {{ margin-bottom: 50px; }}
        </style>
    </head>
    <body>
        <h1>Daily Stock Analysis Report</h1>
        <p>Generated on: {datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</p>
    """

    for symbol, report in results.items():
        html += f"<h2>{symbol}</h2>"
        html += "<table><tr><th>Metric</th><th>Value</th></tr>"
        for key, value in report.items():
            html += f"<tr><td>{key}</td><td>{value}</td></tr>"
        html += "</table>"

        # Add candlestick + RSI + MACD + Volume charts
        df = stock_data.get(symbol)
        if df is not None and not df.empty:
            fig = go.Figure()

            # Candlestick chart
            fig.add_trace(go.Candlestick(
                x=df.index,
                open=df['Open'],
                high=df['High'],
                low=df['Low'],
                close=df['Close'],
                name="Candlestick"
            ))

            # Volume bars
            fig.add_trace(go.Bar(
                x=df.index,
                y=df['Volume'],
                name="Volume",
                marker_color="orange",
                opacity=0.4,
                yaxis="y2"
            ))

            # RSI line
            if 'RSI' in df.columns:
                fig.add_trace(go.Scatter(
                    x=df.index,
                    y=df['RSI'],
                    mode="lines",
                    name="RSI",
                    yaxis="y3",
                    line=dict(color="blue")
                ))

            # MACD + Signal lines
            if 'MACD' in df.columns and 'Signal' in df.columns:
                fig.add_trace(go.Scatter(
                    x=df.index,
                    y=df['MACD'],
                    mode="lines",
                    name="MACD",
                    yaxis="y3",
                    line=dict(color="green")
                ))
                fig.add_trace(go.Scatter(
                    x=df.index,
                    y=df['Signal'],
                    mode="lines",
                    name="Signal",
                    yaxis="y3",
                    line=dict(color="red")
                ))

            # Layout with multiple y-axes
            fig.update_layout(
                title=f"{symbol} Price Action + Indicators",
                xaxis_rangeslider_visible=False,
                yaxis=dict(title="Price"),
                yaxis2=dict(title="Volume", overlaying="y", side="right", showgrid=False),
                yaxis3=dict(title="Indicators", overlaying="y", side="left", showgrid=False)
            )

            chart_html = fig.to_html(full_html=False, include_plotlyjs='cdn')
            html += f"<div class='chart'>{chart_html}</div>"

    html += "</body></html>"

    # Save file
    with open(filename, "w", encoding="utf-8") as f:
        f.write(html)

    # Auto-open in browser
    abs_path = os.path.abspath(filename)
    webbrowser.open(f"file://{abs_path}")

    print(f"\n✅ Report saved as {filename} and opened in your browser.")


def export_to_csv_excel(results, filename_prefix="analysis_report"):
    """
    Export analysis results to timestamped CSV and Excel for backtesting.
    """
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    csv_file = f"{filename_prefix}_{timestamp}.csv"
    excel_file = f"{filename_prefix}_{timestamp}.xlsx"

    df = pd.DataFrame.from_dict(results, orient="index")
    df.index.name = "Symbol"

    df.to_csv(csv_file)
    df.to_excel(excel_file)

    print(f"📊 Results exported to {csv_file} and {excel_file}")
