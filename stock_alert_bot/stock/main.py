import yaml
from scanners.chartlink_scanner import run_chartlink_scanner
from utils.data_fetch import fetch_stock_data
from utils.math_utils import daily_returns, moving_average, volatility
from analysis.deep_analysis import deep_drive_analysis
from reports.html_report import generate_html_report, export_to_csv_excel
from utils.logger_setup import setup_logger

if __name__ == "__main__":
    logger = setup_logger()

    try:
        with open("config.yaml", "r") as f:
            config = yaml.safe_load(f)
        logger.info("Configuration loaded successfully.")
    except Exception as e:
        logger.error(f"Failed to load config.yaml: {e}")
        raise

    scanners = config["scanners"]
    analysis_cfg = config["analysis"]
    output_cfg = config["output"]

    all_symbols = set()
    results = {}
    stock_data = {}

    for name, scanner in scanners.items():
        url = scanner["url"]
        clause = scanner["scan_clause"]
        logger.info(f"Running scanner: {name}")
        shortlisted = run_chartlink_scanner(url, clause)
        logger.info(f"Shortlisted symbols: {shortlisted}")
        all_symbols.update(shortlisted)

    if not all_symbols:
        logger.warning("No live scan results from Chartink; using live market fallback list.")
        from scanners.chartlink_scanner import LIVE_FALLBACK_SYMBOLS
        all_symbols.update(LIVE_FALLBACK_SYMBOLS)

    logger.info(f"Combined unique stocks: {all_symbols}")

    for symbol in all_symbols:
        logger.info(f"Starting analysis for {symbol}")
        df = fetch_stock_data(symbol,
                              period=analysis_cfg["data_period"],
                              interval=analysis_cfg["data_interval"])

        if df.empty:
            logger.warning(f"No data for {symbol}")
            continue

        df = daily_returns(df)
        df = moving_average(df, window=analysis_cfg["moving_average_window"])
        df = volatility(df, window=analysis_cfg["volatility_window"])

        report = deep_drive_analysis(df)
        results[symbol] = report
        stock_data[symbol] = df
        logger.info(f"Analysis completed for {symbol}")

    generate_html_report(results, stock_data, filename_prefix=output_cfg["filename_prefix"])
    export_to_csv_excel(results, filename_prefix=output_cfg["filename_prefix"])

    logger.info("Report generation and export completed successfully.")
