import os, traceback

try:
    t = open('data/dhan_access_token.txt').read().strip()
except Exception as e:
    print('No token file:', e)
    raise SystemExit(1)

try:
    import dhanhq
    print('dhanhq module loaded; members:', [n for n in dir(dhanhq) if not n.startswith('_')])
except Exception as e:
    print('Import dhanhq failed:', e)
    traceback.print_exc()
    raise SystemExit(1)

# Try marketfeed.DhanFeed
try:
    from dhanhq import marketfeed
    print('marketfeed members:', [n for n in dir(marketfeed) if not n.startswith('_')])
    try:
        feed = marketfeed.DhanFeed(client_id=os.environ.get('DHAN_CLIENT_ID','1100062523'), access_token=t, instruments=None, version='v1')
        print('DhanFeed instantiated:', type(feed))
        if hasattr(feed, 'option_chain'):
            try:
                res = feed.option_chain('NIFTY')
                print('DhanFeed.option_chain result type:', type(res))
            except Exception as e:
                print('DhanFeed.option_chain failed:', e)
    except Exception as e:
        print('DhanFeed instantiation failed:', e)
        traceback.print_exc()
except Exception as e:
    print('marketfeed import failed:', e)

# Try dhanhq.dhanhq client
try:
    if hasattr(dhanhq, 'dhanhq'):
        try:
            client = dhanhq.dhanhq('1100062523', t, disable_ssl=True)
            print('dhanhq client instantiated:', type(client))
            print('client has option_chain:', hasattr(client, 'option_chain'))
            if hasattr(client, 'option_chain'):
                try:
                    res = client.option_chain('NIFTY')
                    print('client.option_chain returned type:', type(res))
                except Exception as e:
                    print('client.option_chain failed:', e)
        except Exception as e:
            print('dhanhq.dhanhq(...) failed:', e)
            traceback.print_exc()
    else:
        print('dhanhq module has no attribute dhanhq')
except Exception as e:
    print('dhanhq client check failed:', e)
    traceback.print_exc()
