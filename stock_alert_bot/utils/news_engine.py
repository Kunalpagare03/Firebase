import requests
from bs4 import BeautifulSoup
from datetime import datetime
import logging

log = logging.getLogger("NewsEngine")

def fetch_market_news():
    """
    Fetches latest market-moving news from top financial sources.
    Analyzes sentiment to provide a 'Market Impact' score.
    """
    news_items = []
    # Using a combined feed approach (simulated here with a top source)
    url = "https://news.google.com/rss/search?q=NSE+India+Stock+Market+News&hl=en-IN&gl=IN&ceid=IN:en"

    try:
        response = requests.get(url, timeout=10)
        soup = BeautifulSoup(response.content, 'xml')
        items = soup.find_all('item')[:10] # Top 10 headlines

        for item in items:
            title = item.title.text
            link = item.link.text

            # Simple keyword-based impact analysis
            impact = "Neutral"
            if any(word in title.lower() for word in ['surge', 'bullish', 'growth', 'record high', 'positive']):
                impact = "Positive"
            elif any(word in title.lower() for word in ['crash', 'drop', 'slump', 'bearish', 'negative', 'inflation']):
                impact = "Negative"

            news_items.append({
                "headline": title,
                "link": link,
                "impact": impact,
                "time": datetime.now().strftime("%H:%M")
            })
    except Exception as e:
        log.error(f"Error fetching news: {e}")

    return news_items
