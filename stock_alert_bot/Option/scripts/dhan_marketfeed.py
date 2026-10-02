import time
import os
import traceback

def main():
    try:
        from dhanhq.marketfeed import DhanFeed
    except Exception as e:
        print('dhanhq.marketfeed import failed:', e)
        return

    print('DhanFeed class found')

    # try authorize if available
    api_key = os.environ.get('DHAN_API_KEY') or os.environ.get('DHAN_CLIENT_ID') or os.environ.get('DHAN_APIKEY')
    access_token = os.environ.get('DHAN_ACCESS_TOKEN')
    # fallback: read token from data file if present
    if not access_token:
        try:
            with open(os.path.join('data', 'dhan_access_token.txt'), 'r') as f:
                access_token = f.read().strip()
        except Exception:
            pass

    if not api_key or not access_token:
        # try to extract client_id from JWT access_token payload if present
        try:
            if access_token and not api_key:
                parts = access_token.split('.')
                if len(parts) >= 2:
                    import base64, json
                    b = parts[1]
                    # pad
                    b += '=' * (-len(b) % 4)
                    decoded = base64.urlsafe_b64decode(b.encode())
                    payload = json.loads(decoded)
                    api_key = payload.get('dhanClientId') or payload.get('dhanClientID') or payload.get('client_id')
        except Exception:
            pass

        if not api_key or not access_token:
            print('Missing credentials: need DHAN_CLIENT_ID (or DHAN_API_KEY) and DHAN_ACCESS_TOKEN (or data/dhan_access_token.txt)')
            return
    # credentials are present; proceed to construct the feed below

    try:
        print('Connecting to marketfeed...')
        # constructor requires client_id, access_token, instruments
        instruments = os.environ.get('FEED_SYMBOLS', 'NIFTY').split(',')
        try:
            feed = DhanFeed(api_key, access_token, instruments)
        except Exception as e:
            print('DhanFeed constructor failed with provided args:', e)
            traceback.print_exc()
            return

        # connect (support sync or async connect)
        try:
            import inspect, asyncio
            if inspect.iscoroutinefunction(feed.connect):
                asyncio.run(feed.connect())
            else:
                feed.connect()
        except Exception as e:
            print('connect failed:', e)
            traceback.print_exc()
            return
    except Exception as e:
        print('connect failed:', e)
        traceback.print_exc()
        return

    # subscribe to some common symbols
    symbols = os.environ.get('FEED_SYMBOLS', 'NIFTY').split(',')
    try:
        if hasattr(feed, 'subscribe_symbols'):
            print('Subscribing to symbols:', symbols)
            feed.subscribe_symbols(symbols)
        elif hasattr(feed, 'subscribe_instruments'):
            print('Subscribing instruments:', symbols)
            feed.subscribe_instruments(symbols)
    except Exception as e:
        print('subscribe failed:', e)
        traceback.print_exc()

    print('Polling incoming data for 15s...')
    start = time.time()
    try:
        while time.time() - start < 15:
            try:
                data = None
                if hasattr(feed, 'get_data'):
                    data = feed.get_data()
                elif hasattr(feed, 'get_instrument_data'):
                    data = feed.get_instrument_data()
                if data:
                    print('TICK:', data)
                time.sleep(0.5)
            except Exception as e:
                print('poll error:', e)
                traceback.print_exc()
                time.sleep(1)
    finally:
        try:
                import inspect, asyncio
                if hasattr(feed, 'disconnect'):
                    if inspect.iscoroutinefunction(feed.disconnect):
                        try:
                            asyncio.run(feed.disconnect())
                        except Exception:
                            pass
                    else:
                        try:
                            feed.disconnect()
                        except Exception:
                            pass
                elif hasattr(feed, 'close_connection'):
                    try:
                        feed.close_connection()
                    except Exception:
                        pass
        except Exception:
            pass

if __name__ == '__main__':
    main()
